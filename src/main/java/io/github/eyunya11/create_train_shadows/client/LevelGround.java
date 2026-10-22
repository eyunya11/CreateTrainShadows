package io.github.eyunya11.create_train_shadows.client;

import com.simibubi.create.content.trains.track.TrackBlock;

import io.github.eyunya11.create_train_shadows.core.ShadowOpacity;
import io.github.eyunya11.create_train_shadows.core.ShadowProjector;
import io.github.eyunya11.create_train_shadows.core.TrackSurface;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Finds what a shadow lands on, following the vanilla entity shadow (and Create's ShadowRenderHelper):
 * a visible block whose collision shape is a full block, with enough light in the cell above it.
 *
 * <p>Create's track is not a full block. Its collision shape is a 2/16 high plate over the whole cell, so by
 * the vanilla rule a shadow would be drawn on the block under the track, inside the track's own model, and
 * would show only beside it; and the rails stand higher than that plate, so they would stay bright. When the
 * track setting is PLATE, a piece of Create's track takes the shadow at the top of its rails instead, tilted on
 * a slope (which has no collision at all). In the other settings the track takes no shadow here, so that it
 * lets the shadow through to whatever lies under it (a track on a bridge over a pit); with MODEL it is shaded
 * by its own model. Unless {@code -Dcreate_train_shadows.vanillaReceiver=true} is set, other plates (one box
 * covering the whole cell) also receive the shadow, at the top of the plate.
 */
final class LevelGround implements ShadowProjector.Ground {
    private static final boolean VANILLA_RECEIVER = Boolean.getBoolean("create_train_shadows.vanillaReceiver");
    private static final double EDGE = 1.0e-3;

    private final BlockPos.MutableBlockPos receiver = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
    private final double[] trackPlane = new double[3];
    private Level level;
    private boolean tracks;

    /** @param tracks whether Create's track takes the shadow on the top of its rails, slopes included */
    LevelGround use(Level level, boolean tracks) {
        this.level = level;
        this.tracks = tracks;
        return this;
    }

    @Override
    public boolean sample(int cellX, int cellZ, int startY, int depth, double[] out) {
        int lowest = level.getMinBuildHeight();
        // Same loop as vanilla: y is the row of the air above the receiving block at y - 1.
        for (int y = startY; y > startY - depth && y - 1 >= lowest; y--) {
            receiver.set(cellX, y - 1, cellZ);
            BlockState state = level.getBlockState(receiver);
            if (state.getRenderShape() == RenderShape.INVISIBLE) {
                continue;
            }
            if (!tracks && state.getBlock() instanceof TrackBlock) {
                // The track is shaded by its own model (or not at all). Its plate must not take the shadow as well:
                // what lies under an elevated track, the floor of a pit it spans, then never gets any.
                continue;
            }
            // The light first: it is cheap, and most blocks below the first surface (the ground under the ground) are
            // in the dark, where there is no shadow to draw
            above.set(cellX, y, cellZ);
            int light = level.getMaxLocalRawBrightness(above);
            if (light < ShadowOpacity.MIN_LIGHT) {
                continue;
            }
            double top;
            double slopeX = 0, slopeZ = 0;
            if (tracks && onTrackSurface(state)) {
                top = trackPlane[0];
                slopeX = trackPlane[1];
                slopeZ = trackPlane[2];
            } else {
                top = receivingTop(state);
                if (Double.isNaN(top)) {
                    continue;
                }
            }
            out[0] = (y - 1) + top;
            out[1] = LightTexture.getBrightness(level.dimensionType(), light);
            out[2] = slopeX;
            out[3] = slopeZ;
            return true;
        }
        return false;
    }

    /**
     * Whether the block is a piece of Create's track, and if so, the plane of the top of its rails in
     * {@link #trackPlane}. Track is not a full block and a slope has no collision at all, so neither the
     * rules for full blocks nor those for plates would put the shadow where the rails are.
     */
    private boolean onTrackSurface(BlockState state) {
        if (!(state.getBlock() instanceof TrackBlock track) || !state.hasProperty(TrackBlock.SHAPE)) {
            return false;
        }
        Vec3 normal = state.getValue(TrackBlock.SHAPE).getNormal();
        return TrackSurface.plane(normal.x, normal.y, normal.z, track.getElevationAtCenter(level, receiver, state),
            trackPlane);
    }

    /** Height of the receiving surface inside its block, or NaN when this block can't receive a shadow. */
    private double receivingTop(BlockState state) {
        VoxelShape collision = state.getCollisionShape(level, receiver);
        if (collision.isEmpty()) {
            return Double.NaN;
        }
        if (state.isCollisionShapeFullBlock(level, receiver)) {
            return 1.0;
        }
        if (VANILLA_RECEIVER) {
            return Double.NaN;
        }

        AABB bounds = collision.bounds();
        boolean coversCell = bounds.minX <= EDGE && bounds.maxX >= 1.0 - EDGE
            && bounds.minZ <= EDGE && bounds.maxZ >= 1.0 - EDGE;
        if (coversCell && collision.toAabbs().size() == 1) {
            return bounds.maxY;
        }
        return Double.NaN;
    }
}
