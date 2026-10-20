package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShadowOpacityTest {
    private static final ShadowParams VANILLA = ShadowParams.VANILLA;
    private static final ShadowParams DEFAULT = ShadowParams.DEFAULT;

    @Test
    void distanceFactorIsOneAtTheCameraAndZeroAtTheFadeDistance() {
        assertEquals(1.0, ShadowOpacity.distanceFactor(0, 128), 1e-12);
        assertEquals(0.0, ShadowOpacity.distanceFactor(128.0 * 128.0, 128), 1e-12);
        assertEquals(0.75, ShadowOpacity.distanceFactor(64.0 * 64.0, 128), 1e-12);
        assertEquals(0.0, ShadowOpacity.distanceFactor(500.0 * 500.0, 128), 1e-12);
    }

    @Test
    void vanillaDistanceCurveIsTheSixteenBlockOne() {
        assertEquals(0.75, ShadowOpacity.distanceFactor(64, VANILLA.fadeDistance()), 1e-12); // 8 blocks
        assertEquals(0.0, ShadowOpacity.distanceFactor(256, VANILLA.fadeDistance()), 1e-12); // 16 blocks
    }

    @Test
    void aShadowIsStillVisibleFarBeyondVanillasRangeWithTheDefaults() {
        double factor = ShadowOpacity.distanceFactor(60.0 * 60.0, DEFAULT.fadeDistance());
        assertTrue(factor > 0.7, "factor at 60 blocks: " + factor);
    }

    @Test
    void vanillaParametersGiveVanillasAlpha() {
        // Vanilla: (opacity - height / 2) * 0.5 * brightness, with opacity 1 close to the camera
        assertEquals(0.5f, ShadowOpacity.alpha(VANILLA, 0.0, 1.0), 1e-6f);
        assertEquals(0.25f, ShadowOpacity.alpha(VANILLA, 1.0, 1.0), 1e-6f);
        assertEquals(0.0f, ShadowOpacity.alpha(VANILLA, 2.0, 1.0), 1e-6f);
        assertTrue(ShadowOpacity.alpha(VANILLA, 3.0, 1.0) < 0);
    }

    @Test
    void alphaScalesWithBrightness() {
        assertEquals(0.25f, ShadowOpacity.alpha(VANILLA, 0.0, 0.5), 1e-6f);
        assertEquals(0.0f, ShadowOpacity.alpha(VANILLA, 0.0, 0.0), 1e-6f);
    }

    @Test
    void alphaIsCappedAtOne() {
        assertEquals(1.0f, ShadowOpacity.alpha(new ShadowParams(10, 16, 2, 0, 0, 0, 0, 0, false), 0.0, 1.0), 1e-6f);
    }

    @Test
    void negativeHeightCountsAsContact() {
        assertEquals(ShadowOpacity.alpha(DEFAULT, 0.0, 1.0), ShadowOpacity.alpha(DEFAULT, -0.5, 1.0), 1e-6f);
    }

    @Test
    void theGraceHeightKeepsAStandardTrainAtFullOpacity() {
        assertEquals(0.6f, ShadowOpacity.alpha(DEFAULT, 0.0, 1.0), 1e-6f);
        assertEquals(0.6f, ShadowOpacity.alpha(DEFAULT, 1.0, 1.0), 1e-6f); // one block up: the usual bogey height
    }

    @Test
    void beyondTheGraceHeightTheShadowFadesLinearlyOverTheFadeHeight() {
        assertEquals(0.3f, ShadowOpacity.alpha(DEFAULT, 1.0 + 4.0, 1.0), 1e-6f);
        assertEquals(0.0f, ShadowOpacity.alpha(DEFAULT, 1.0 + 8.0, 1.0), 1e-6f);
        assertTrue(ShadowOpacity.alpha(DEFAULT, 20.0, 1.0) < 0);
    }

    @Test
    void searchDepthCoversEveryHeightThatIsStillVisible() {
        assertEquals(3, ShadowOpacity.searchDepth(VANILLA, 0));
        assertEquals(10, ShadowOpacity.searchDepth(DEFAULT, 0));
        assertEquals(11, ShadowOpacity.searchDepth(DEFAULT, 1));
        for (double standoff : new double[] {0, 1, 2.5, 7}) {
            int depth = ShadowOpacity.searchDepth(DEFAULT, standoff);
            assertTrue(depth >= DEFAULT.graceHeight() + DEFAULT.fadeHeight() + standoff, "standoff " + standoff);
        }
    }

    @Test
    void searchDepthIsCapped() {
        assertEquals(ShadowOpacity.MAX_DEPTH, ShadowOpacity.searchDepth(new ShadowParams(0.6, 128, 32, 8, 0, 0, 0, 0, false), 100));
    }
}
