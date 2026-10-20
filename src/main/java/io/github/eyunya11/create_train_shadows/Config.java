package io.github.eyunya11.create_train_shadows;

import io.github.eyunya11.create_train_shadows.core.ShadowParams;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side options. The values are copied into a {@link ShadowParams} whenever the config is loaded or
 * changed (also from the in-game config screen), so rendering never touches the config system.
 */
@EventBusSubscriber(modid = Create_train_shadows.MODID, value = Dist.CLIENT)
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ShadowParams DEFAULTS = ShadowParams.DEFAULT;

    private static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Draw shadows under Create trains.")
        .translation("create_train_shadows.configuration.enabled")
        .define("enabled", true);

    private static final ModConfigSpec.DoubleValue OPACITY = BUILDER
        .comment("How dark the shadow is where it is darkest, in full light. Vanilla entity shadows use 0.5.")
        .translation("create_train_shadows.configuration.opacity")
        .defineInRange("opacity", DEFAULTS.opacity(), 0.0, 1.0);

    private static final ModConfigSpec.DoubleValue FADE_DISTANCE = BUILDER
        .comment("Camera distance, in blocks, at which the shadow has faded out completely. Vanilla uses 16.")
        .translation("create_train_shadows.configuration.fadeDistance")
        .defineInRange("fadeDistance", DEFAULTS.fadeDistance(), 16.0, 512.0);

    private static final ModConfigSpec.DoubleValue FADE_HEIGHT = BUILDER
        .comment("Once a train is higher above the ground than graceHeight, the shadow fades out over this many blocks.")
        .translation("create_train_shadows.configuration.fadeHeight")
        .defineInRange("fadeHeight", DEFAULTS.fadeHeight(), 1.0, 32.0);

    private static final ModConfigSpec.DoubleValue GRACE_HEIGHT = BUILDER
        .comment("Height above the lowest part of the train, in blocks, that still counts as touching the ground.",
            "Create hangs a train one block above its track, so 1 keeps trains on the ground dark.")
        .translation("create_train_shadows.configuration.graceHeight")
        .defineInRange("graceHeight", DEFAULTS.graceHeight(), 0.0, 8.0);

    private static final ModConfigSpec.DoubleValue MARGIN = BUILDER
        .comment("How far the shadow reaches past the outline of the train on every side, in blocks.",
            "A shadow exactly under the train is mostly hidden by the train itself.")
        .translation("create_train_shadows.configuration.margin")
        .defineInRange("margin", DEFAULTS.margin(), 0.0, 2.0);

    private static final ModConfigSpec.DoubleValue SOFTNESS = BUILDER
        .comment("Width of the band along the outer edge of the shadow in which it fades from nothing to full darkness,",
            "in blocks. 0 gives a hard edge. With a width equal to the spread, the shadow is darkest at the outline of",
            "the train and fades out to its outer edge.")
        .translation("create_train_shadows.configuration.softness")
        .defineInRange("softness", DEFAULTS.softness(), 0.0, 2.0);

    private static final ModConfigSpec.DoubleValue SIDE_REACH = BUILDER
        .comment("How far from a train, in blocks, the sides of walls are darkened: tunnel walls, platform edges and",
            "steps in the ground. The wall is darkest right next to the train and fades out with distance.",
            "0 draws no shadow on walls.")
        .translation("create_train_shadows.configuration.sideReach")
        .defineInRange("sideReach", DEFAULTS.sideReach(), 0.0, 4.0);

    private static final ModConfigSpec.DoubleValue SIDE_OPACITY = BUILDER
        .comment("How dark a wall gets right next to the train, in full light.")
        .translation("create_train_shadows.configuration.sideOpacity")
        .defineInRange("sideOpacity", DEFAULTS.sideOpacity(), 0.0, 1.0);

    private static final ModConfigSpec.BooleanValue TRACKS = BUILDER
        .comment("Lay the shadow on the rails of Create's track, including sloping and curved track, instead of on",
            "the ground under the track. The rails stand higher than the plate of the track block, so without this",
            "they stay bright inside the shadow.")
        .translation("create_train_shadows.configuration.trackShadows")
        .define("trackShadows", DEFAULTS.tracks());

    static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile boolean enabled = true;
    private static volatile ShadowParams params = DEFAULTS;

    private Config() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static ShadowParams params() {
        return params;
    }

    @SubscribeEvent
    static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        enabled = ENABLED.get();
        params = new ShadowParams(OPACITY.get(), FADE_DISTANCE.get(), FADE_HEIGHT.get(), GRACE_HEIGHT.get(),
            MARGIN.get(), SOFTNESS.get(), SIDE_REACH.get(), SIDE_OPACITY.get(), TRACKS.get());
    }
}
