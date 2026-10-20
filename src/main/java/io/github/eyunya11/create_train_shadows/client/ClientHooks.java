package io.github.eyunya11.create_train_shadows.client;

import com.simibubi.create.AllEntityTypes;
import io.github.eyunya11.create_train_shadows.Create_train_shadows;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = Create_train_shadows.MODID, value = Dist.CLIENT)
public final class ClientHooks {
    private ClientHooks() {
    }

    // The last registration for an entity type wins, so this relies on neoforge.mods.toml ordering this
    // mod AFTER create: Create registers its own renderer first, then this one replaces it.
    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AllEntityTypes.CARRIAGE_CONTRAPTION.get(), TrainShadowRenderer::new);
    }

    // Fired once every renderer exists; reads back what the dispatcher will actually use, so a conflict
    // with another mod that replaces the train renderer shows up in the log instead of as missing shadows.
    @SubscribeEvent
    static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        EntityRenderer<?> renderer = event.getRenderer(AllEntityTypes.CARRIAGE_CONTRAPTION.get());
        if (renderer instanceof TrainShadowRenderer) {
            Create_train_shadows.LOGGER.info("Train renderer in use: {}", renderer.getClass().getName());
        } else {
            Create_train_shadows.LOGGER.warn("Train renderer in use is {}, not ours; train shadows will not be drawn. "
                + "Another mod probably replaces Create's train renderer.", renderer == null ? "missing" : renderer.getClass().getName());
        }
    }
}
