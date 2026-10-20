package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShadowOpacityTest {
    @Test
    void distanceFadeMatchesVanilla() {
        assertEquals(1.0, ShadowOpacity.distanceFade(0), 1e-12);
        assertEquals(0.0, ShadowOpacity.distanceFade(256), 1e-12); // 16 blocks
        assertEquals(0.75, ShadowOpacity.distanceFade(64), 1e-12); // 8 blocks
        assertTrue(ShadowOpacity.distanceFade(400) < 0);
    }

    @Test
    void alphaAtContactIsHalfInFullLight() {
        // Vanilla: (opacity - height / 2) * 0.5 * brightness
        assertEquals(0.5f, ShadowOpacity.alpha(1.0, 0.0, 1.0), 1e-6f);
    }

    @Test
    void alphaFadesWithHeightAndReachesZeroAtTwoBlocks() {
        assertEquals(0.25f, ShadowOpacity.alpha(1.0, 1.0, 1.0), 1e-6f);
        assertEquals(0.0f, ShadowOpacity.alpha(1.0, 2.0, 1.0), 1e-6f);
        assertTrue(ShadowOpacity.alpha(1.0, 3.0, 1.0) < 0);
    }

    @Test
    void alphaScalesWithBrightness() {
        assertEquals(0.25f, ShadowOpacity.alpha(1.0, 0.0, 0.5), 1e-6f);
        assertEquals(0.0f, ShadowOpacity.alpha(1.0, 0.0, 0.0), 1e-6f);
    }

    @Test
    void alphaIsCappedAtOne() {
        assertEquals(1.0f, ShadowOpacity.alpha(10.0, 0.0, 1.0), 1e-6f);
    }

    @Test
    void negativeHeightCountsAsContact() {
        assertEquals(ShadowOpacity.alpha(1.0, 0.0, 1.0), ShadowOpacity.alpha(1.0, -0.5, 1.0), 1e-6f);
    }

    @Test
    void searchDepthCoversEveryVisibleHeight() {
        for (double strength : new double[] {0.1, 0.5, 1.0, 1.5, 2.0}) {
            int depth = ShadowOpacity.maxDepth(strength);
            // A surface deeper than `depth` rows is further than 2 * strength below, so it is invisible.
            assertTrue(depth >= 1);
            assertTrue(depth > 2 * strength - 1e-9, "depth " + depth + " for strength " + strength);
        }
        assertEquals(3, ShadowOpacity.maxDepth(1.0));
    }
}
