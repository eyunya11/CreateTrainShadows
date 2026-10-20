package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FootprintExpandTest {
    private static final double EPS = 1e-9;

    private static int countCovering(List<ShadowRect> rects, double x, double z) {
        int count = 0;
        for (ShadowRect r : rects) {
            if (x > r.minX() && x < r.maxX() && z > r.minZ() && z < r.maxZ()) {
                count++;
            }
        }
        return count;
    }

    /** Independent definition of the expected result: the lowest bottom among the grown rectangles at a point. */
    private static Integer expectedBottom(List<LocalRect> rects, double margin, double x, double z) {
        Integer bottom = null;
        for (LocalRect r : rects) {
            if (x > r.minX() - margin && x < r.maxX() + margin && z > r.minZ() - margin && z < r.maxZ() + margin) {
                bottom = bottom == null ? r.bottomY() : Math.min(bottom, r.bottomY());
            }
        }
        return bottom;
    }

    private static void assertSameCoverage(List<LocalRect> input, double margin) {
        List<ShadowRect> out = Footprint.expand(input, margin);
        // Sample points that avoid every grid line the inputs could produce.
        for (double x = -6.0 + 0.0137; x < 14; x += 0.0731) {
            for (double z = -6.0 + 0.0291; z < 14; z += 0.0689) {
                Integer expected = expectedBottom(input, margin, x, z);
                int covering = countCovering(out, x, z);
                if (expected == null) {
                    assertEquals(0, covering, "uncovered point (" + x + ", " + z + ") is covered");
                } else {
                    assertEquals(1, covering, "point (" + x + ", " + z + ") must be covered exactly once");
                    for (ShadowRect r : out) {
                        if (x > r.minX() && x < r.maxX() && z > r.minZ() && z < r.maxZ()) {
                            assertEquals(expected, r.bottomY(), "bottom at (" + x + ", " + z + ")");
                        }
                    }
                }
            }
        }
    }

    @Test
    void noRectanglesGiveNothing() {
        assertTrue(Footprint.expand(List.of(), 0.5).isEmpty());
    }

    @Test
    void oneRectangleGrowsOnEverySide() {
        List<ShadowRect> out = Footprint.expand(List.of(new LocalRect(0, 0, 3, 7, 0)), 0.5);
        assertEquals(List.of(new ShadowRect(-0.5, -0.5, 3.5, 7.5, 0)), out);
        assertEquals(32.0, out.get(0).area(), EPS);
    }

    @Test
    void noMarginKeepsTheRectangle() {
        List<ShadowRect> out = Footprint.expand(List.of(new LocalRect(2, -1, 3, 4, 5)), 0);
        assertEquals(List.of(new ShadowRect(2, -1, 5, 3, 5)), out);
    }

    @Test
    void touchingRectanglesWithTheSameBottomBecomeOne() {
        List<LocalRect> input = List.of(new LocalRect(0, 0, 2, 3, 0), new LocalRect(2, 0, 2, 3, 0));
        assertEquals(1, Footprint.expand(input, 0).size());
        List<ShadowRect> grown = Footprint.expand(input, 0.3);
        assertEquals(1, grown.size());
        assertEquals(new ShadowRect(-0.3, -0.3, 4.3, 3.3, 0), grown.get(0));
    }

    @Test
    void overlapsAreResolvedWithTheLowerBottom() {
        List<LocalRect> input = List.of(new LocalRect(0, 0, 2, 3, 0), new LocalRect(2, 0, 2, 3, 1));
        assertSameCoverage(input, 0.3);
        // x between 1.7 and 2.3 belongs to both grown rectangles and takes the lower one
        List<ShadowRect> out = Footprint.expand(input, 0.3);
        boolean seamIsLow = out.stream().anyMatch(r -> r.minX() <= 1.7 + EPS && r.maxX() >= 2.3 - EPS && r.bottomY() == 0);
        assertTrue(seamIsLow);
    }

    @Test
    void resultsNeverOverlapAndMatchTheUnionOfTheGrownRectangles() {
        // An L shape with a step in height and a hole.
        Footprint.Builder b = new Footprint.Builder();
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 5; z++) {
                if ((x == 2 && z == 2) || (x >= 4 && z >= 3)) {
                    continue;
                }
                b.add(x, x < 3 ? 0 : 1, z);
            }
        }
        List<LocalRect> input = b.build();
        for (double margin : new double[] {0, 0.3, 0.5, 1.0}) {
            assertSameCoverage(input, margin);
        }
    }

    @Test
    void separateIslandsStaySeparateWhileTheMarginDoesNotBridgeThem() {
        List<LocalRect> input = List.of(new LocalRect(0, 0, 1, 1, 0), new LocalRect(5, 0, 1, 1, 0));
        List<ShadowRect> out = Footprint.expand(input, 0.5);
        assertEquals(2, out.size());
        assertEquals(2 * 4.0, out.stream().mapToDouble(ShadowRect::area).sum(), EPS);
    }

    @Test
    void marginsThatMeetFillTheGap() {
        List<LocalRect> input = List.of(new LocalRect(0, 0, 1, 1, 0), new LocalRect(2, 0, 1, 1, 0));
        List<ShadowRect> out = Footprint.expand(input, 0.5);
        assertEquals(1, out.size());
        assertEquals(new ShadowRect(-0.5, -0.5, 3.5, 1.5, 0), out.get(0));
    }

    @Test
    void negativeCoordinatesWork() {
        assertSameCoverage(List.of(new LocalRect(-3, -2, 2, 3, 0), new LocalRect(-1, 0, 2, 1, 1)), 0.4);
    }
}
