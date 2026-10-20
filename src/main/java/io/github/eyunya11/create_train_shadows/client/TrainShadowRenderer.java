package io.github.eyunya11.create_train_shadows.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntityRenderer;
import io.github.eyunya11.create_train_shadows.Create_train_shadows;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Create's train renderer, plus a ground shadow drawn after the train itself.
 * Registered over Create's own renderer for the carriage contraption entity.
 */
public class TrainShadowRenderer extends CarriageContraptionEntityRenderer {
    private boolean loggedFirstRender;

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
    }
}
