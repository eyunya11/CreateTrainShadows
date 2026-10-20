package io.github.eyunya11.create_train_shadows.core;

/**
 * How a shadow looks. Plain numbers, so the maths needs nothing from the game or its config system.
 *
 * @param opacity      alpha of the darkest part, in full light and at contact (vanilla: 0.5)
 * @param fadeDistance camera distance at which the shadow has faded out completely, in blocks (vanilla: 16)
 * @param fadeHeight   height over which the shadow fades out once it is higher than {@code graceHeight} (vanilla: 2)
 * @param graceHeight  height above the lowest part of the body that still counts as contact, in blocks.
 *                     Create hangs a train one block above its track, so the default keeps trains dark.
 * @param margin       how far the shadow reaches past the footprint on every side, in blocks
 * @param softness     width of the band along the outer edge of the shadow in which it fades from nothing to
 *                     full darkness, in blocks; 0 gives a hard edge
 * @param sideReach    how far from the body, in blocks, the side of a wall is still darkened; 0 draws no
 *                     shadow on walls (vanilla shadows only fall on the ground)
 * @param sideOpacity  alpha of the darkest part of a wall's shadow, right next to the body
 * @param tracks       whether the shadow lies on the rails of Create's track (sloping ones and curves too),
 *                     instead of on whatever is under the track
 */
public record ShadowParams(double opacity, double fadeDistance, double fadeHeight, double graceHeight,
    double margin, double softness, double sideReach, double sideOpacity, boolean tracks) {
    /** What the vanilla entity shadow does, for comparison and for tests. */
    public static final ShadowParams VANILLA = new ShadowParams(0.5, 16.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, false);

    /** The defaults of the mod's configuration. */
    public static final ShadowParams DEFAULT = new ShadowParams(0.6, 128.0, 8.0, 1.0, 0.5, 0.5, 2.0, 0.5, true);
}
