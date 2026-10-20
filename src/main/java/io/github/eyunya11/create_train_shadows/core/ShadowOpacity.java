package io.github.eyunya11.create_train_shadows.core;

/**
 * Shadow strength. Modelled on the vanilla entity shadow (EntityRenderDispatcher / Create's
 * ShadowRenderHelper), which fades with the camera distance, with the height above the ground and with the
 * light level, but with each fade a separate setting: {@link ShadowParams#VANILLA} gives the vanilla look.
 */
public final class ShadowOpacity {
    /** Vanilla draws a shadow while it is lit at least this much; darker than this it is skipped. */
    public static final int MIN_LIGHT = 4;

    /** How many block rows below the body are searched at most, however tall the fade is. */
    public static final int MAX_DEPTH = 24;

    private ShadowOpacity() {
    }

    /** 1 at the camera, 0 at {@code fadeDistance} blocks and beyond. Vanilla's curve, with its own range. */
    public static double distanceFactor(double cameraDistanceSq, double fadeDistance) {
        return Math.max(0.0, 1.0 - cameraDistanceSq / (fadeDistance * fadeDistance));
    }

    /**
     * The alpha of a shadow polygon, before the distance factor.
     *
     * @param height     how far the body is above the receiving surface, measured from the lowest part of the
     *                   body (the part of it that is as low as the body goes counts as height 0), in blocks
     * @param brightness the dimension's light curve at the receiving cell, 0..1
     * @return alpha up to 1; zero or less means nothing should be drawn
     */
    public static float alpha(ShadowParams params, double height, double brightness) {
        double beyondGrace = Math.max(0.0, height - params.graceHeight());
        double heightFactor = 1.0 - beyondGrace / params.fadeHeight();
        return (float) Math.min(1.0, params.opacity() * brightness * heightFactor);
    }

    /**
     * How many block rows below a part of the body can still hold a visible shadow.
     *
     * @param standoff how far that part hangs above the lowest part of the body, in blocks
     */
    public static int searchDepth(ShadowParams params, double standoff) {
        int depth = (int) Math.ceil(params.graceHeight() + params.fadeHeight() + standoff) + 1;
        return Math.max(1, Math.min(MAX_DEPTH, depth));
    }

    /**
     * How many block rows under the lowest part of the body can still be darkened by something that is not
     * the ground straight below it (the side of a pillar, a track under a bridge): as many as it takes for the
     * fade to reach zero. Stopping earlier would cut the shadow off while it is still dark.
     */
    public static int rowsBelow(ShadowParams params) {
        int rows = (int) Math.ceil(params.graceHeight() + params.fadeHeight()) + 1;
        return Math.max(1, Math.min(MAX_DEPTH, rows));
    }
}
