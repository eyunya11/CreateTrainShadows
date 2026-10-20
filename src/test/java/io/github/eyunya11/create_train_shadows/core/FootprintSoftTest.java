package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class FootprintSoftTest {
    private static final double EPS = 1e-9;

    /** The fade of the mesh at a point, evaluated triangle by triangle the way the projector draws it. */
    private static double fadeAt(List<ShadowRect> mesh, double x, double z) {
        for (ShadowRect r : mesh) {
            if (x < r.minX() - EPS || x > r.maxX() + EPS || z < r.minZ() - EPS || z > r.maxZ() + EPS) {
                continue;
            }
            double u = (x - r.minX()) / (r.maxX() - r.minX());
            double v = (z - r.minZ()) / (r.maxZ() - r.minZ());
            double[][] tri = r.splitsAlongMinToMax()
                ? (u >= v ? new double[][] {{0, 0, r.a00()}, {1, 0, r.a10()}, {1, 1, r.a11()}}
                          : new double[][] {{0, 0, r.a00()}, {1, 1, r.a11()}, {0, 1, r.a01()}})
                : (u + v <= 1 ? new double[][] {{0, 0, r.a00()}, {1, 0, r.a10()}, {0, 1, r.a01()}}
                              : new double[][] {{1, 0, r.a10()}, {1, 1, r.a11()}, {0, 1, r.a01()}});
            return barycentric(tri, u, v);
        }
        return Double.NaN;
    }

    private static double barycentric(double[][] t, double u, double v) {
        double d = (t[1][0] - t[0][0]) * (t[2][1] - t[0][1]) - (t[2][0] - t[0][0]) * (t[1][1] - t[0][1]);
        double s = ((u - t[0][0]) * (t[2][1] - t[0][1]) - (t[2][0] - t[0][0]) * (v - t[0][1])) / d;
        double w = ((t[1][0] - t[0][0]) * (v - t[0][1]) - (u - t[0][0]) * (t[1][1] - t[0][1])) / d;
        return t[0][2] + s * (t[1][2] - t[0][2]) + w * (t[2][2] - t[0][2]);
    }

    private static double area(List<ShadowRect> mesh) {
        return mesh.stream().mapToDouble(ShadowRect::area).sum();
    }

    private static boolean covered(List<LocalRect> input, double margin, double x, double z) {
        for (LocalRect r : input) {
            if (x > r.minX() - margin && x < r.maxX() + margin && z > r.minZ() - margin && z < r.maxZ() + margin) {
                return true;
            }
        }
        return false;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    @Test
    void theFadeOfARectangleIsTheOneTheMeshIsDrawnWith() {
        List<ShadowRect> mesh = Footprint.soft(List.of(new LocalRect(0, 0, 6, 2, 0), new LocalRect(0, 2, 2, 5, 0)), 0.5, 0.5);
        for (double x = -0.6; x < 6.6; x += 0.0731) {
            for (double z = -0.6; z < 7.6; z += 0.0689) {
                double viaHelper = fadeAt(mesh, x, z);
                double viaRect = Double.NaN;
                for (ShadowRect r : mesh) {
                    double f = r.fadeAt(x, z);
                    if (!Double.isNaN(f)) {
                        viaRect = f;
                        break;
                    }
                }
                if (Double.isNaN(viaHelper)) {
                    assertTrue(Double.isNaN(viaRect), "at (" + x + ", " + z + ")");
                } else {
                    assertEquals(viaHelper, viaRect, 1e-9, "at (" + x + ", " + z + ")");
                }
            }
        }
    }

    @Test
    void noSoftnessIsTheHardEdge() {
        List<LocalRect> input = List.of(new LocalRect(0, 0, 3, 7, 0), new LocalRect(3, 0, 2, 2, 1));
        assertEquals(Footprint.expand(input, 0.5), Footprint.soft(input, 0.5, 0));
    }

    @Test
    void emptyInputGivesNothing() {
        assertTrue(Footprint.soft(List.of(), 0.5, 0.5).isEmpty());
    }

    @Test
    void aRectangleFadesOutLinearlyTowardsItsEdgeWithMitredCorners() {
        List<LocalRect> input = List.of(new LocalRect(0, 0, 3, 7, 0));
        double margin = 0.6;
        double softness = 0.5;
        List<ShadowRect> mesh = Footprint.soft(input, margin, softness);

        assertEquals((3 + 2 * margin) * (7 + 2 * margin), area(mesh), 1e-9);
        for (double x = -margin + 0.0137; x < 3 + margin; x += 0.0731) {
            for (double z = -margin + 0.0291; z < 7 + margin; z += 0.0689) {
                double d = Math.min(Math.min(x + margin, 3 + margin - x), Math.min(z + margin, 7 + margin - z));
                assertEquals(clamp01(d / softness), fadeAt(mesh, x, z), 1e-9, "at (" + x + ", " + z + ")");
            }
        }
    }

    @Test
    void theInsideStaysFullyDarkAsFewRectanglesAsPossible() {
        List<ShadowRect> mesh = Footprint.soft(List.of(new LocalRect(0, 0, 3, 7, 0)), 0.5, 0.5);
        // one full-dark middle, four edge strips and four corners
        assertEquals(9, mesh.size());
        assertEquals(1, mesh.stream().filter(ShadowRect::isUniform).count());
    }

    @Test
    void nothingOutsideTheOutlineIsCovered() {
        List<ShadowRect> mesh = Footprint.soft(List.of(new LocalRect(0, 0, 3, 7, 0)), 0.5, 0.5);
        assertTrue(Double.isNaN(fadeAt(mesh, -0.6, 3)));
        assertTrue(Double.isNaN(fadeAt(mesh, 1.5, 8.0)));
    }

    @Test
    void theFadeStartsFromNothingAtTheEdge() {
        List<ShadowRect> mesh = Footprint.soft(List.of(new LocalRect(0, 0, 3, 7, 0)), 0.5, 0.5);
        assertEquals(0.0, fadeAt(mesh, -0.5, 3.0), 1e-9);
        assertEquals(0.0, fadeAt(mesh, 1.5, 7.5), 1e-9);
        assertEquals(0.0, fadeAt(mesh, -0.5, -0.5), 1e-9);
        assertEquals(1.0, fadeAt(mesh, 0.0, 3.0), 1e-9); // at the outline of the blocks themselves
    }

    @Test
    void aNarrowShapeStillReachesItsHighestPointInTheMiddle() {
        // 1 wide with a margin of 0.5 and a softness of 2: the two fades meet halfway, at a quarter of the way up
        List<ShadowRect> mesh = Footprint.soft(List.of(new LocalRect(0, 0, 1, 10, 0)), 0.5, 2.0);
        assertEquals(clamp01(1.0 / 2.0), fadeAt(mesh, 0.5, 5.0), 1e-9);
        assertEquals(clamp01(0.5 / 2.0), fadeAt(mesh, 0.0, 5.0), 1e-9);
    }

    @Test
    void partsOfTheSameShadowDoNotFadeWhereTheyMeet() {
        List<LocalRect> input = List.of(new LocalRect(0, 0, 2, 3, 0), new LocalRect(2, 0, 2, 3, 1));
        List<ShadowRect> mesh = Footprint.soft(input, 0.5, 0.5);
        for (double z = 0.3; z < 2.8; z += 0.2) {
            assertEquals(1.0, fadeAt(mesh, 2.0, z), 1e-9, "seam at z=" + z);
        }
        assertEquals(0.0, fadeAt(mesh, 4.5, 1.5), 1e-9);
    }

    @Test
    void theUndersideOfEachPartIsKept() {
        List<LocalRect> input = List.of(new LocalRect(0, 0, 2, 3, 0), new LocalRect(5, 0, 2, 3, 1));
        for (ShadowRect r : Footprint.soft(input, 0.5, 0.5)) {
            double centre = (r.minX() + r.maxX()) / 2;
            assertEquals(centre < 3.5 ? 0 : 1, r.bottomY(), "bottom at x=" + centre);
        }
    }

    @Test
    void anLShapeIsDarkOnlyWhereItIsAwayFromItsEdge() {
        // An L made of two rectangles, with a reflex corner
        List<LocalRect> input = List.of(new LocalRect(0, 0, 6, 2, 0), new LocalRect(0, 2, 2, 5, 0));
        double margin = 0.5;
        double softness = 0.5;
        List<ShadowRect> mesh = Footprint.soft(input, margin, softness);

        for (double x = -margin + 0.0137; x < 6 + margin; x += 0.0731) {
            for (double z = -margin + 0.0291; z < 7 + margin; z += 0.0689) {
                if (!covered(input, margin, x, z)) {
                    continue;
                }
                // Chebyshev distance to the nearest uncovered point, found by sampling
                double nearest = Double.MAX_VALUE;
                for (double dx = -softness - 0.1; dx <= softness + 0.1; dx += 0.05) {
                    for (double dz = -softness - 0.1; dz <= softness + 0.1; dz += 0.05) {
                        if (!covered(input, margin, x + dx, z + dz)) {
                            nearest = Math.min(nearest, Math.max(Math.abs(dx), Math.abs(dz)));
                        }
                    }
                }
                double fade = fadeAt(mesh, x, z);
                // the mesh is piecewise linear, so near the creases it is only close to the distance
                assertEquals(clamp01(nearest / softness), fade, 0.2, "at (" + x + ", " + z + ")");
                if (nearest > softness + 0.06) {
                    assertEquals(1.0, fade, 1e-9, "deep inside at (" + x + ", " + z + ")");
                }
            }
        }
    }

    @Test
    void everythingStaysWithinTheBoundsOfTheOutline() {
        List<LocalRect> input = List.of(new LocalRect(0, 0, 6, 2, 0), new LocalRect(0, 2, 2, 5, 0));
        List<ShadowRect> mesh = Footprint.soft(input, 0.5, 0.5);
        List<ShadowRect> hard = Footprint.expand(input, 0.5);
        assertEquals(area(hard), area(mesh), 1e-9);
        for (ShadowRect r : mesh) {
            assertTrue(r.a00() >= 0 && r.a00() <= 1 && r.a10() >= 0 && r.a10() <= 1
                && r.a01() >= 0 && r.a01() <= 1 && r.a11() >= 0 && r.a11() <= 1);
        }
    }

    @Test
    void shapesTooComplicatedForAFeatherKeepAHardEdge() {
        List<LocalRect> scattered = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            scattered.add(new LocalRect(i * 3, (i * 7) % 11 * 3, 1, 1, 0));
        }
        List<ShadowRect> mesh = Footprint.soft(scattered, 0.5, 0.5);
        assertEquals(Footprint.expand(scattered, 0.5), mesh);
    }
}
