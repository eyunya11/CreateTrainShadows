package io.github.eyunya11.create_train_shadows.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackRenderer;

import io.github.eyunya11.create_train_shadows.Create_train_shadows;
import io.github.eyunya11.create_train_shadows.core.Mat3x4;
import io.github.eyunya11.create_train_shadows.core.ShadowField;
import io.github.eyunya11.create_train_shadows.core.ShadowOpacity;
import io.github.eyunya11.create_train_shadows.core.ShadowParams;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The shadow of a carriage on Create's track, drawn on the track's own model: the faces of the rails, the
 * sleepers and the girders are drawn again, black and translucent, with a darkness for each vertex that
 * depends on where the vertex is under the carriage. So it follows the model exactly, on the level, on a slope
 * and along a curve, instead of lying over it as a plate.
 *
 * <p>The model's faces come from two places: a track block's baked model, and, for a curve, the very routine
 * that Create's own block entity renderer uses for it ({@code TrackRenderer.renderBezierTurn}), which draws the
 * whole curve. Neither changes from frame to frame, so each is recorded once, into a {@link QuadRecorder},
 * and every frame only the faces near the carriage are looked at.
 */
final class TrackModelShadows {
    /** Faces are looked at in groups of this many, with a box around each group, to skip far ones quickly. */
    private static final int GROUP = 32;
    private static final double SLACK = 1.5;
    private static final int MAX_CACHED_BLOCKS = 4096;

    /** Recorded faces: positions relative to an origin block, and a box around each group of faces. */
    private record Quads(long originX, long originY, long originZ, float[] vertices, float[] groupBoxes) {
        static final Quads EMPTY = new Quads(0, 0, 0, new float[0], new float[0]);

        static Quads of(BlockPos origin, float[] vertices) {
            int quads = vertices.length / 12;
            int groups = (quads + GROUP - 1) / GROUP;
            float[] boxes = new float[groups * 6];
            for (int g = 0; g < groups; g++) {
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
                int end = Math.min(quads, (g + 1) * GROUP) * 12;
                for (int i = g * GROUP * 12; i < end; i += 3) {
                    minX = Math.min(minX, vertices[i]);
                    minY = Math.min(minY, vertices[i + 1]);
                    minZ = Math.min(minZ, vertices[i + 2]);
                    maxX = Math.max(maxX, vertices[i]);
                    maxY = Math.max(maxY, vertices[i + 1]);
                    maxZ = Math.max(maxZ, vertices[i + 2]);
                }
                boxes[g * 6] = minX;
                boxes[g * 6 + 1] = minY;
                boxes[g * 6 + 2] = minZ;
                boxes[g * 6 + 3] = maxX;
                boxes[g * 6 + 4] = maxY;
                boxes[g * 6 + 5] = maxZ;
            }
            return new Quads(origin.getX(), origin.getY(), origin.getZ(), vertices, boxes);
        }
    }

    private record BlockEntry(BlockState state, Quads quads) {
    }

    private static final byte UNKNOWN = -1;

    private final Long2ObjectOpenHashMap<BlockEntry> blocks = new Long2ObjectOpenHashMap<>();
    private final Map<BezierConnection, Quads> curves = new WeakHashMap<>();
    private final Set<BezierConnection> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Long2ByteOpenHashMap trackBlocks = new Long2ByteOpenHashMap(); // per tick
    private final Long2ByteOpenHashMap lights = new Long2ByteOpenHashMap(); // per tick
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final ShadowField field = new ShadowField();
    private final double[] xs = new double[4], ys = new double[4], zs = new double[4];
    private final float[] alphas = new float[4];

    private Level level;
    private long tick = Long.MIN_VALUE;
    private boolean broken;

    TrackModelShadows() {
        trackBlocks.defaultReturnValue(UNKNOWN);
        lights.defaultReturnValue(UNKNOWN);
    }

    void draw(Level level, Carriage carriage, FootprintCache.Footprints footprints, Mat3x4 localToWorld,
        ShadowParams params, double distanceFactor, ShadowDrawer out) {
        if (broken) {
            return;
        }
        try {
            begin(level);
            field.set(footprints.ground(), localToWorld, params, distanceFactor);
            drawBlocks(ShadowOpacity.rowsBelow(params), out);
            drawCurves(carriage, out);
        } catch (RuntimeException | LinkageError e) {
            // The models and Create's classes are not ours; if one behaves differently than expected, lose
            // only the shadow on the track
            broken = true;
            Create_train_shadows.LOGGER.error("Shadows on the track model failed and are switched off: {}",
                e.toString(), e);
        } finally {
            this.level = null;
        }
    }

    private void begin(Level level) {
        long now = level.getGameTime();
        if (level != this.level && this.tick != Long.MIN_VALUE) {
            blocks.clear();
            curves.clear();
        }
        if (now != tick) {
            trackBlocks.clear();
            lights.clear();
            tick = now;
        }
        this.level = level;
    }

    // --- track blocks

    private void drawBlocks(int rowsBelow, ShadowDrawer out) {
        int fromX = Mth.floor(field.minX() - SLACK), toX = Mth.floor(field.maxX() + SLACK);
        int fromZ = Mth.floor(field.minZ() - SLACK), toZ = Mth.floor(field.maxZ() + SLACK);
        int fromY = Mth.floor(field.minY()) - rowsBelow, toY = Mth.floor(field.maxY());
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                for (int y = fromY; y <= toY; y++) {
                    if (isTrack(x, y, z)) {
                        pos.set(x, y, z);
                        emit(blockQuads(pos), out);
                    }
                }
            }
        }
    }

    private boolean isTrack(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        byte known = trackBlocks.get(key);
        if (known == UNKNOWN) {
            pos.set(x, y, z);
            known = (byte) (level.getBlockState(pos).getBlock() instanceof TrackBlock ? 1 : 0);
            trackBlocks.put(key, known);
        }
        return known == 1;
    }

    private Quads blockQuads(BlockPos blockPos) {
        long key = blockPos.asLong();
        BlockState state = level.getBlockState(blockPos);
        BlockEntry entry = blocks.get(key);
        if (entry == null || entry.state() != state) {
            if (blocks.size() >= MAX_CACHED_BLOCKS) {
                blocks.clear();
            }
            entry = new BlockEntry(state, recordBlock(blockPos.immutable(), state));
            blocks.put(key, entry);
        }
        return entry.quads();
    }

    /** The faces of the block's model, relative to the block, the way the chunk renderer draws them. */
    private Quads recordBlock(BlockPos blockPos, BlockState state) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(state);
        RandomSource random = RandomSource.create(42L);
        ModelData data = model.getModelData(level, blockPos, state, level.getModelData(blockPos));
        QuadRecorder recorder = new QuadRecorder();
        PoseStack identity = new PoseStack();
        for (RenderType type : model.getRenderTypes(state, random, data)) {
            dispatcher.getModelRenderer().renderModel(identity.last(), recorder, state, model, 1.0F, 1.0F, 1.0F, 0,
                OverlayTexture.NO_OVERLAY, data, type);
        }
        return Quads.of(blockPos, recorder.toArray());
    }

    // --- curves

    private void drawCurves(Carriage carriage, ShadowDrawer out) {
        seen.clear();
        carriage.bogeys.forEach(bogey -> {
            if (bogey != null) {
                drawCurveUnder(bogey.leading(), out);
                drawCurveUnder(bogey.trailing(), out);
            }
        });
    }

    private void drawCurveUnder(TravellingPoint wheel, ShadowDrawer out) {
        TrackEdge edge = wheel.edge;
        if (edge == null || !edge.isTurn() || !seen.add(edge.getTurn())) {
            return;
        }
        BezierConnection curve = edge.getTurn();
        Quads quads = curves.get(curve);
        if (quads == null) {
            quads = recordCurve(curve);
            curves.put(curve, quads);
        }
        emit(quads, out);
    }

    /**
     * The faces of the whole curve, relative to the block entity that holds it. Create draws a curve from that
     * block entity with one routine; it is given a consumer that records instead of drawing.
     */
    private Quads recordCurve(BezierConnection curve) {
        try {
            BezierConnection primary = curve.isPrimary() ? curve : curve.secondary();
            QuadRecorder recorder = new QuadRecorder();
            TrackRenderer.renderBezierTurn(level, primary, new PoseStack(), recorder);
            return Quads.of(primary.bePositions.getFirst(), recorder.toArray());
        } catch (RuntimeException e) {
            Create_train_shadows.LOGGER.error("Could not record a curve of the track for its shadow: {}", e.toString(), e);
            return Quads.EMPTY;
        }
    }

    // --- drawing the recorded faces

    private void emit(Quads quads, ShadowDrawer out) {
        float[] v = quads.vertices();
        float[] boxes = quads.groupBoxes();
        int quadCount = v.length / 12;
        for (int group = 0; group * GROUP < quadCount; group++) {
            if (!groupNear(quads, boxes, group)) {
                continue;
            }
            int end = Math.min(quadCount, (group + 1) * GROUP);
            for (int quad = group * GROUP; quad < end; quad++) {
                int base = quad * 12;
                double highest = -Double.MAX_VALUE;
                for (int i = 0; i < 4; i++) {
                    xs[i] = quads.originX() + v[base + 3 * i];
                    ys[i] = quads.originY() + v[base + 3 * i + 1];
                    zs[i] = quads.originZ() + v[base + 3 * i + 2];
                    highest = Math.max(highest, ys[i]);
                }
                if (!field.isNear(xs[0], ys[0], zs[0], SLACK)) {
                    continue;
                }
                double brightness = brightnessAbove(Mth.floor(xs[0]), Mth.floor(highest), Mth.floor(zs[0]));
                if (brightness <= 0) {
                    continue;
                }
                boolean any = false;
                for (int i = 0; i < 4; i++) {
                    alphas[i] = (float) field.alphaAt(xs[i], ys[i], zs[i], brightness);
                    any |= alphas[i] > 0;
                }
                if (any) {
                    out.rawQuad(xs, ys, zs, alphas);
                }
            }
        }
    }

    private boolean groupNear(Quads quads, float[] boxes, int group) {
        int b = group * 6;
        return quads.originX() + boxes[b + 3] >= field.minX() - SLACK && quads.originX() + boxes[b] <= field.maxX() + SLACK
            && quads.originZ() + boxes[b + 5] >= field.minZ() - SLACK && quads.originZ() + boxes[b + 2] <= field.maxZ() + SLACK
            && quads.originY() + boxes[b + 1] <= field.maxY() + 1.0;
    }

    /** The brightness 0..1 of the cell above the one the face is in, or 0 when it is too dark for a shadow. */
    private double brightnessAbove(int x, int y, int z) {
        long key = BlockPos.asLong(x, y + 1, z);
        byte known = lights.get(key);
        if (known == UNKNOWN) {
            pos.set(x, y + 1, z);
            known = (byte) level.getMaxLocalRawBrightness(pos);
            lights.put(key, known);
        }
        return known < ShadowOpacity.MIN_LIGHT ? 0.0 : LightTexture.getBrightness(level.dimensionType(), known);
    }
}
