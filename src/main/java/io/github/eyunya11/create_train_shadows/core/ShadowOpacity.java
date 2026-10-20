package io.github.eyunya11.create_train_shadows.core;

/**
 * Shadow strength, following the vanilla entity shadow (EntityRenderDispatcher / Create's ShadowRenderHelper):
 * it fades with the camera distance, with the height above the ground and with the light level.
 */
public final class ShadowOpacity {
    /** Vanilla draws a shadow while it is lit at least this much; darker than this it is skipped. */
    public static final int MIN_LIGHT = 4;

    private ShadowOpacity() {
    }

    /** The distance factor of vanilla: 1 at the camera, 0 at 16 blocks. May be negative far away. */
    public static double distanceFade(double cameraDistanceSq) {
        return 1.0 - cameraDistanceSq / 256.0;
    }

    /**
     * The alpha of a shadow polygon.
     *
     * @param strength     vanilla's {@code opacity}: {@link #distanceFade} times the configured strength
     * @param height       how far the underside is above the receiving surface, in blocks
     * @param brightness   the dimension's light curve at the receiving cell, 0..1
     * @return alpha in 0..1; zero or less means nothing should be drawn
     */
    public static float alpha(double strength, double height, double brightness) {
        double f = (strength - Math.max(0.0, height) / 2.0) * 0.5 * brightness;
        return (float) Math.min(1.0, f);
    }

    /** How many blocks below the underside a shadow can still be visible (alpha reaches zero at 2 * strength). */
    public static int maxDepth(double strength) {
        return Math.max(1, (int) Math.ceil(2.0 * strength) + 1);
    }
}
