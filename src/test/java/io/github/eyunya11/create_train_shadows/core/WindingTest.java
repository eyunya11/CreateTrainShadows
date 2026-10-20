package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class WindingTest {
    /** The normal of (p1 - p0) x (p2 - p0), by the right-hand rule: the front of a counter-clockwise polygon. */
    private static double[] normal(double[] p0, double[] p1, double[] p2) {
        double[] a = {p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]};
        double[] b = {p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]};
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    @Test
    void aFlatPolygonIsOrientedByTheRightHandRule() {
        // (0,0) (1,0) (0,1) in (x, z): the right-hand rule gives -Y, so it faces down
        double[] xs = {0, 1, 0};
        double[] zs = {0, 0, 1};
        assertEquals(-1.0, normal(new double[] {0, 0, 0}, new double[] {1, 0, 0}, new double[] {0, 0, 1})[1], 1e-12);
        assertTrue(Winding.needsReversingToFaceUp(xs, zs, 3));

        double[] reversedX = {0, 0, 1};
        double[] reversedZ = {0, 1, 0};
        assertEquals(1.0, normal(new double[] {0, 0, 0}, new double[] {0, 0, 1}, new double[] {1, 0, 0})[1], 1e-12);
        assertFalse(Winding.needsReversingToFaceUp(reversedX, reversedZ, 3));
    }

    @Test
    void theAnswerAgreesWithTheRealNormalForAnyConvexPolygonAndRotation() {
        for (int n = 3; n <= 7; n++) {
            for (double turn = 0; turn < 360; turn += 37) {
                for (boolean flip : new boolean[] {false, true}) {
                    double[] xs = new double[n];
                    double[] zs = new double[n];
                    for (int i = 0; i < n; i++) {
                        double angle = Math.toRadians(turn) + 2 * Math.PI * (flip ? n - 1 - i : i) / n;
                        xs[i] = 5 + 2 * Math.cos(angle);
                        zs[i] = -3 + 2 * Math.sin(angle);
                    }
                    double[] normal = normal(new double[] {xs[0], 0, zs[0]}, new double[] {xs[1], 0, zs[1]},
                        new double[] {xs[2], 0, zs[2]});
                    assertEquals(normal[1] < 0, Winding.needsReversingToFaceUp(xs, zs, n),
                        "n=" + n + " turn=" + turn + " flip=" + flip);
                }
            }
        }
    }

    @Test
    void aWallQuadIsOrientedTowardsItsNormal() {
        // a quad in the plane x = 5, z from 0 to 1 and y from 0 to 1, written going up in z then up in y
        double[] xs = {5, 5, 5, 5};
        double[] ys = {0, 0, 1, 1};
        double[] zs = {0, 1, 1, 0};
        double[] n = normal(new double[] {xs[0], ys[0], zs[0]}, new double[] {xs[1], ys[1], zs[1]},
            new double[] {xs[2], ys[2], zs[2]});
        boolean facesPlusX = n[0] > 0;
        assertEquals(!facesPlusX, Winding.needsReversingToFace(xs, ys, zs, 1, 0));
        assertEquals(facesPlusX, Winding.needsReversingToFace(xs, ys, zs, -1, 0));
    }

    @Test
    void everyWallDirectionAndVertexOrderGivesAFrontFacingQuad() {
        int[][] normals = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] normal : normals) {
            for (boolean flip : new boolean[] {false, true}) {
                // quad on a wall whose normal is (nx, 0, nz): it spans the horizontal direction (-nz, nx) and y
                List<double[]> corners = new ArrayList<>(List.of(new double[] {0, 0}, new double[] {1, 0},
                    new double[] {1, 1}, new double[] {0, 1}));
                if (flip) {
                    java.util.Collections.reverse(corners);
                }
                double[] xs = new double[4], ys = new double[4], zs = new double[4];
                for (int i = 0; i < 4; i++) {
                    double along = corners.get(i)[0];
                    xs[i] = 10 + along * -normal[1];
                    zs[i] = 20 + along * normal[0];
                    ys[i] = corners.get(i)[1];
                }
                boolean reverse = Winding.needsReversingToFace(xs, ys, zs, normal[0], normal[1]);
                int[] order = reverse ? new int[] {3, 2, 1, 0} : new int[] {0, 1, 2, 3};
                double[] n = normal(new double[] {xs[order[0]], ys[order[0]], zs[order[0]]},
                    new double[] {xs[order[1]], ys[order[1]], zs[order[1]]},
                    new double[] {xs[order[2]], ys[order[2]], zs[order[2]]});
                assertTrue(n[0] * normal[0] + n[2] * normal[1] > 0,
                    "normal " + normal[0] + "," + normal[1] + " flip=" + flip);
                assertEquals(0.0, n[1], 1e-12);
            }
        }
    }
}
