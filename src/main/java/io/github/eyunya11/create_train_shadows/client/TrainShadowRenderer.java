package io.github.eyunya11.create_train_shadows.client;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntityRenderer;

import io.github.eyunya11.create_train_shadows.Create_train_shadows;
import io.github.eyunya11.create_train_shadows.core.LocalRect;
import io.github.eyunya11.create_train_shadows.core.Mat3x4;
import io.github.eyunya11.create_train_shadows.core.ShadowOpacity;
import io.github.eyunya11.create_train_shadows.core.ShadowProjector;
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
    // Plain white, tinted black per vertex: a flat shadow with hard edges, not the round blob of vanilla.
    private static final RenderType SHADOW_LAYER =
        RenderType.entityNoOutline(ResourceLocation.withDefaultNamespace("textures/misc/white.png"));

    // Vanilla's shadowStrength for entities is 1. Becomes a setting later.
    private static final double SHADOW_STRENGTH = 1.0;

    private final ShadowProjector projector = new ShadowProjector();
    private final ShadowDrawer drawer = new ShadowDrawer();
    private final LevelGround ground = new LevelGround();
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

        renderShadow(entity, partialTicks, ms, buffers);
    }

    private void renderShadow(CarriageContraptionEntity entity, float partialTicks, PoseStack ms,
        MultiBufferSource buffers) {
        // The same cases in which Create skips drawing the train itself.
        if (!entity.validForRender || entity.firstPositionUpdate) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.entityShadows().get() || entity.isInvisible()) {
            return;
        }
        Level level = entity.level();
        if (level != minecraft.level) {
            return; // Ponder scenes and other virtual levels
        }
        Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return;
        }

        double strength = ShadowOpacity.distanceFade(entityRenderDispatcher.distanceToSqr(entity)) * SHADOW_STRENGTH;
        if (strength <= 0) {
            return;
        }
        List<LocalRect> footprint = FootprintCache.get(contraption);
        if (footprint.isEmpty()) {
            return;
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
        projector.project(footprint, localToWorld, strength, ground.use(level), drawer);
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
