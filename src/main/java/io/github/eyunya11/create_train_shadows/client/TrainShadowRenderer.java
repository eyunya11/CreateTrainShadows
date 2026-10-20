package io.github.eyunya11.create_train_shadows.client;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntityRenderer;

import io.github.eyunya11.create_train_shadows.Config;
import io.github.eyunya11.create_train_shadows.Create_train_shadows;
import io.github.eyunya11.create_train_shadows.client.Diagnostics.Outcome;
import io.github.eyunya11.create_train_shadows.core.Mat3x4;
import io.github.eyunya11.create_train_shadows.core.ShadowOpacity;
import io.github.eyunya11.create_train_shadows.core.ShadowParams;
import io.github.eyunya11.create_train_shadows.core.ShadowProjector;
import io.github.eyunya11.create_train_shadows.core.ShadowRect;
import io.github.eyunya11.create_train_shadows.core.SideShadowProjector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Create's train renderer, plus a ground shadow drawn after the train itself.
 * Registered over Create's own renderer for the carriage contraption entity.
 */
public class TrainShadowRenderer extends CarriageContraptionEntityRenderer {
    // Plain white, tinted black per vertex: a flat shadow, not the round blob of vanilla. The vanilla shadow
    // render type: it pulls what it draws a little towards the camera, so a shadow can lie almost on the surface
    // it falls on without z-fighting, and it culls back faces (ShadowDrawer writes the vertices to suit).
    private static final RenderType SHADOW_LAYER =
        RenderType.entityShadow(ResourceLocation.withDefaultNamespace("textures/misc/white.png"));

    private final ShadowProjector projector = new ShadowProjector();
    private final SideShadowProjector sideProjector = new SideShadowProjector();
    private final ShadowDrawer drawer = new ShadowDrawer();
    private final LevelGround ground = new LevelGround();
    private final LevelWalls walls = new LevelWalls();
    private final CurvedTrackShadows curves = new CurvedTrackShadows();
    private boolean loggedFirstRender;
    private boolean loggedFirstShadow;

    public TrainShadowRenderer(EntityRendererProvider.Context context) {
        super(context);
        Create_train_shadows.LOGGER.info("TrainShadowRenderer created");
    }

    @Override
    public void render(CarriageContraptionEntity entity, float yaw, float partialTicks, PoseStack ms,
        MultiBufferSource buffers, int overlay) {
        super.render(entity, yaw, partialTicks, ms, buffers, overlay);

        if (!loggedFirstRender) {
            loggedFirstRender = true;
            Create_train_shadows.LOGGER.info("TrainShadowRenderer is rendering a train");
        }

        Diagnostics.record(renderShadow(entity, partialTicks, ms, buffers));
    }

    /** @return what became of the request, for the diagnostics */
    private Outcome renderShadow(CarriageContraptionEntity entity, float partialTicks, PoseStack ms,
        MultiBufferSource buffers) {
        // The same cases in which Create skips drawing the train itself.
        if (!entity.validForRender || entity.firstPositionUpdate) {
            return Outcome.NOT_READY;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.entityShadows().get()) {
            return Outcome.SHADOWS_OFF_IN_VIDEO_SETTINGS;
        }
        if (!Config.enabled()) {
            return Outcome.DISABLED_IN_MOD_CONFIG;
        }
        if (entity.isInvisible()) {
            return Outcome.INVISIBLE;
        }
        Level level = entity.level();
        if (level != minecraft.level) {
            return Outcome.OTHER_LEVEL; // Ponder scenes and other virtual levels
        }
        Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return Outcome.NO_CONTRAPTION;
        }

        ShadowParams params = Config.params();
        double distanceSq = entityRenderDispatcher.distanceToSqr(entity);
        Diagnostics.distance(Math.sqrt(distanceSq));
        double distanceFactor = ShadowOpacity.distanceFactor(distanceSq, params.fadeDistance());
        if (distanceFactor <= 0) {
            return Outcome.TOO_FAR;
        }
        FootprintCache.Footprints footprints = FootprintCache.get(contraption, params);
        List<ShadowRect> footprint = footprints.ground();
        if (footprint.isEmpty()) {
            return Outcome.NO_FOOTPRINT;
        }

        // The transform Create draws the blocks with: local -> relative to the entity (ContraptionMatrices).
        double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        PoseStack model = new PoseStack();
        entity.applyLocalTransforms(model, partialTicks);
        Mat3x4 localToWorld = toWorld(model.last().pose(), x, y, z);

        if (!loggedFirstShadow) {
            loggedFirstShadow = true;
            Create_train_shadows.LOGGER.info("Drawing the first train shadow ({} footprint rectangles)",
                footprint.size());
        }

        VertexConsumer consumer = buffers.getBuffer(SHADOW_LAYER);
        drawer.begin(consumer, ms.last(), x, y, z);
        projector.project(footprint, localToWorld, params, distanceFactor, ground.use(level, params.tracks()), drawer);
        sideProjector.project(footprints.walls(), footprints.topY(), localToWorld, params, distanceFactor,
            walls.use(level), drawer);
        if (params.tracks()) {
            Carriage carriage = entity.getCarriage();
            if (carriage != null) {
                curves.draw(carriage, footprints.extent(), footprint, localToWorld, params, distanceFactor, level,
                    drawer);
            }
        }
        Diagnostics.drawn(drawer.polygonCount(), Math.sqrt(distanceSq));
        return Outcome.DRAWN;
    }

    private static Mat3x4 toWorld(Matrix4f model, double x, double y, double z) {
        Vector3f p = new Vector3f();
        double[] origin = image(model, 0, 0, 0, x, y, z, p);
        double[] unitX = image(model, 1, 0, 0, x, y, z, p);
        double[] unitY = image(model, 0, 1, 0, x, y, z, p);
        double[] unitZ = image(model, 0, 0, 1, x, y, z, p);
        return Mat3x4.fromImages(origin, unitX, unitY, unitZ);
    }

    private static double[] image(Matrix4f model, float lx, float ly, float lz, double x, double y, double z,
        Vector3f scratch) {
        model.transformPosition(lx, ly, lz, scratch);
        return new double[] {x + scratch.x, y + scratch.y, z + scratch.z};
    }
}
