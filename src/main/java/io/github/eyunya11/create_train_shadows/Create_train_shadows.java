package io.github.eyunya11.create_train_shadows;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file.
// Rendering only: nothing to do on a dedicated server.
@Mod(value = Create_train_shadows.MODID, dist = Dist.CLIENT)
public class Create_train_shadows {
    public static final String MODID = "create_train_shadows";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Create_train_shadows(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        // Mods -> Create: Train Shadows -> Config
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        LOGGER.info("Create: Train Shadows loaded");
    }
}
