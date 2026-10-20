package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TrackRibbonTest {
    private record Quad(double[] xs, double[] ys, double[] zs, float[] alphas) {
        double area() {
            // projected onto the ground
            double twice = 0;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                twice += xs[i] * zs[j] - xs[j] * zs[i];
            }
            return Math.abs(twice) / 2;
        }
    }

    private static List<Quad> build(double[][] points, double[][] normals, double halfWidth, int steps, double lift,
        TrackRibbon.Surface surface) {
        int n = points.length;
        double[] px = new double[n], py = new double[n], pz = new double[n];
        double[] nx = new double[n], ny = new double[n], nz = new double[n];
        for (int i = 0; i < n; i++) {
            px[i] = points[i][0];
            py[i] = points[i][1];
            pz[i] = points[i][2];
            nx[i] = normals[i][0];
            ny[i] = normals[i][1];
            nz[i] = normals[i][2];
        }
        List<Quad> quads = new ArrayList<>();
        new TrackRibbon().build(px, py, pz, nx, ny, nz, n, halfWidth, steps, lift, surface,
            (xs, ys, zs, alphas, count) -> {
                assertEquals(4, count);
                quads.add(new Quad(xs.clone(), ys.clone(), zs.clone(), alphas.clone()));
            });
        return quads;
    }

    private static double[][] straight(int n, double y) {
        double[][] p = new double[n][];
        for (int i = 0; i < n; i++) {
            p[i] = new double[] {i, y, 10};
        }
        return p;
    }

    private static double[][] up(int n) {
        double[][] u = new double[n][];
        for (int i = 0; i < n; i++) {
            u[i] = new double[] {0, 1, 0};
        }
        return u;
    }

    @Test
    void aStraightStripCoversItsLengthAndWidthAtTheHeightOfTheRails() {
        List<Quad> quads = build(straight(5, 5.0), up(5), 1.4, 4, 0.01, (x, y, z) -> 0.5);
        assertEquals(4 * 4, quads.size());
        double area = 0;
        for (Quad q : quads) {
            area += q.area();
            for (int i = 0; i < 4; i++) {
                assertEquals(5.0 + TrackSurface.RAIL_TOP + 0.01, q.ys()[i], 1e-12);
                assertTrue(q.zs()[i] >= 10 - 1.4 - 1e-9 && q.zs()[i] <= 10 + 1.4 + 1e-9);
                assertEquals(0.5f, q.alphas()[i], 1e-6f);
            }
        }
        assertEquals(4 * 2.8, area, 1e-9);
    }

    @Test
    void theAlphaOfEachVertexIsWhatTheSurfaceSaysThere() {
        TrackRibbon.Surface peak = (x, y, z) -> Math.max(0, 1 - Math.abs(x - 2) / 2) * (1 - Math.abs(z - 10) / 2);
        for (Quad q : build(straight(5, 5.0), up(5), 1.4, 4, 0.0, peak)) {
            for (int i = 0; i < 4; i++) {
                assertEquals((float) peak.alphaAt(q.xs()[i], q.ys()[i], q.zs()[i]), q.alphas()[i], 1e-6f);
            }
        }
    }

    @Test
    void quadsWhereThereIsNoShadowAreLeftOut() {
        List<Quad> quads = build(straight(9, 5.0), up(9), 1.0, 2, 0.0, (x, y, z) -> x >= 3 && x <= 5 ? 0.4 : 0.0);
        assertFalse(quads.isEmpty());
        for (Quad q : quads) {
            double minX = Math.min(Math.min(q.xs()[0], q.xs()[1]), Math.min(q.xs()[2], q.xs()[3]));
            double maxX = Math.max(Math.max(q.xs()[0], q.xs()[1]), Math.max(q.xs()[2], q.xs()[3]));
            assertTrue(maxX >= 3 - 1e-9 && minX <= 5 + 1e-9, "quad from " + minX + " to " + maxX);
        }
        // from x = 2 to 6 there are segments that touch the shadow: 2-3, 3-4, 4-5, 5-6, each in two bands
        assertEquals(4 * 2, quads.size());
    }

    @Test
    void aCurveGivesAStripThatFollowsItAtAConstantDistanceFromItsCentre() {
        // a quarter circle of radius 10 around (0, 5, 0), starting at (10, 5, 0)
        int n = 31;
        double[][] points = new double[n][];
        for (int i = 0; i < n; i++) {
            double angle = Math.PI / 2 * i / (n - 1);
            points[i] = new double[] {10 * Math.cos(angle), 5.0, 10 * Math.sin(angle)};
        }
        double halfWidth = 1.4;
        for (Quad q : build(points, up(n), halfWidth, 4, 0.0, (x, y, z) -> 1.0)) {
            for (int i = 0; i < 4; i++) {
                double radius = Math.hypot(q.xs()[i], q.zs()[i]);
                assertTrue(radius >= 10 - halfWidth - 0.01 && radius <= 10 + halfWidth + 0.01,
                    "radius " + radius);
            }
        }
    }

    @Test
    void aTiltedTrackLiftsTheStripAlongItsNormal() {
        // a track climbing at 45 degrees along x; its up direction is (-1, 1, 0) / sqrt 2
        int n = 4;
        double[][] points = new double[n][], normals = new double[n][];
        for (int i = 0; i < n; i++) {
            points[i] = new double[] {i, 5.0 + i, 10};
            normals[i] = new double[] {-1, 1, 0};
        }
        double s = 1 / Math.sqrt(2);
        for (Quad q : build(points, normals, 1.0, 2, 0.0, (x, y, z) -> 1.0)) {
            // each vertex is one rail height from the line of the track, along the normal, then sideways
            for (int i = 0; i < 4; i++) {
                double along = q.xs()[i] + s * TrackSurface.RAIL_TOP; // undo the normal's x
                double expectedY = 5.0 + along + s * TrackSurface.RAIL_TOP;
                assertEquals(expectedY, q.ys()[i], 1e-9);
            }
        }
    }

    @Test
    void tooFewSamplesGiveNothing() {
        assertTrue(build(straight(1, 5.0), up(1), 1.0, 2, 0.0, (x, y, z) -> 1.0).isEmpty());
    }
}
