package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SideShadowProjectorTest {
    private record Quad(double[] xs, double[] ys, double[] zs, float[] alphas, int normalX, int normalZ) {
    }

    private static final ShadowParams PARAMS = ShadowParams.DEFAULT; // reach 2, opacity 0.5, grace 1, fade height 8
    private static final double LIFT = SideShadowProjector.LIFT;

    // A carriage 3 wide and 7 long, one block above the ground at y = 4, 3 blocks tall:
    // x in [10.5, 13.5], z in [20.5, 27.5], underside at y = 5, top at y = 8.
    private static final List<ShadowRect> BODY = List.of(new ShadowRect(0, 0, 3, 7, 0));
    private static final double TOP = 3;
    private static final Mat3x4 PLACE = Mat3x4.translation(10.5, 5, 20.5);

    private static List<Quad> run(List<ShadowRect> rects, Mat3x4 m, ShadowParams params, double distanceFactor,
        SideShadowProjector.Walls walls) {
        List<Quad> quads = new ArrayList<>();
        new SideShadowProjector().project(rects, TOP, m, params, distanceFactor, walls,
            (xs, ys, zs, alphas, nx, nz) -> quads.add(new Quad(xs.clone(), ys.clone(), zs.clone(), alphas.clone(), nx, nz)));
        return quads;
    }

    /** Open air west of x = 15, solid from 15 on: a wall facing the carriage. Only the column next to it has a face. */
    private static SideShadowProjector.Walls wallAt(int wallX, double brightness) {
        return (cx, y, cz, out) -> {
            if (cx == wallX - 1) {
                out[0] = brightness;
                return SideShadowProjector.PLUS_X;
            }
            return 0;
        };
    }

    /** The expected alpha of a point of the wall, worked out from the geometry of the axis-aligned body. */
    private static double expected(double px, double py, double pz, double brightness, double distanceFactor) {
        double dx = Math.max(0, Math.max(10.5 - px, px - 13.5));
        double dz = Math.max(0, Math.max(20.5 - pz, pz - 27.5));
        double near = Math.max(0, 1 - Math.hypot(dx, dz) / PARAMS.sideReach());
        double below = Math.max(0, Math.min(1, 1 - Math.max(0, (5 - py) - PARAMS.graceHeight()) / PARAMS.fadeHeight()));
        double above = Math.max(0, Math.min(1, 1 - Math.max(0, py - 8)));
        return PARAMS.sideOpacity() * brightness * distanceFactor * near * below * above;
    }

    @Test
    void aWallNextToTheBodyIsDarkenedByDistanceAndHeight() {
        List<Quad> quads = run(BODY, PLACE, PARAMS, 1.0, wallAt(15, 1.0));
        assertFalse(quads.isEmpty());
        for (Quad q : quads) {
            assertEquals(-1, q.normalX());
            assertEquals(0, q.normalZ());
            for (int i = 0; i < 4; i++) {
                assertEquals(15 - LIFT, q.xs()[i], 1e-12, "quads lie just in front of the wall");
                assertEquals(expected(q.xs()[i], q.ys()[i], q.zs()[i], 1.0, 1.0), q.alphas()[i], 1e-6,
                    "at y=" + q.ys()[i] + " z=" + q.zs()[i]);
            }
        }
    }

    @Test
    void theDarkestPointIsRightNextToTheBodyAndItFadesAlongTheWall() {
        List<Quad> quads = run(BODY, PLACE, PARAMS, 1.0, wallAt(14, 1.0)); // 0.5 blocks from the body
        double darkest = 0;
        double atEnd = Double.MAX_VALUE;
        for (Quad q : quads) {
            for (int i = 0; i < 4; i++) {
                if (q.ys()[i] == 5.0 && q.zs()[i] == 24.0) {
                    darkest = q.alphas()[i];
                }
                if (q.ys()[i] == 5.0 && q.zs()[i] <= 19.5 + 1e-9) {
                    atEnd = Math.min(atEnd, q.alphas()[i]);
                }
            }
        }
        assertEquals(expected(14 - LIFT, 5.0, 24.0, 1.0, 1.0), darkest, 1e-6);
        assertEquals(0.5 * (1 - 0.5 / 2), darkest, 0.005); // about 0.5 blocks away: three quarters of 0.5
        assertTrue(atEnd < darkest, "further along the wall, past the end of the body, it is lighter");
    }

    @Test
    void aWallBeyondTheReachGetsNothing() {
        assertTrue(run(BODY, PLACE, PARAMS, 1.0, wallAt(17, 1.0)).isEmpty()); // 3.5 blocks away
    }

    @Test
    void aWallFacingAwayFromTheBodyGetsNothing() {
        // The open cell is east of a wall at x = 15 whose solid side is the body's: the face looks away from it.
        SideShadowProjector.Walls behind = (cx, y, cz, out) -> {
            if (cx == 15) {
                out[0] = 1.0;
                return SideShadowProjector.MINUS_X;
            }
            return 0;
        };
        assertTrue(run(BODY, PLACE, PARAMS, 1.0, behind).isEmpty());
    }

    @Test
    void wallsOnTheOtherSidesFaceTheBodyToo() {
        // Walls 0.5 blocks away on the west (solid up to x = 10, faces +X), the low-z end (solid up to z = 20,
        // faces +Z) and the high-z end (solid from z = 28, faces -Z)
        SideShadowProjector.Walls around = (cx, y, cz, out) -> {
            out[0] = 1.0;
            if (cx == 10 && cz == 24 && y == 5) {
                return SideShadowProjector.MINUS_X;
            }
            if (cx == 12 && cz == 20 && y == 5) {
                return SideShadowProjector.MINUS_Z;
            }
            if (cx == 12 && cz == 27 && y == 5) {
                return SideShadowProjector.PLUS_Z;
            }
            return 0;
        };
        List<Quad> quads = run(BODY, PLACE, PARAMS, 1.0, around);
        boolean west = false, lowZ = false, highZ = false;
        for (Quad q : quads) {
            west |= q.normalX() == 1 && q.normalZ() == 0 && Math.abs(q.xs()[0] - (10 + LIFT)) < 1e-12;
            lowZ |= q.normalX() == 0 && q.normalZ() == 1 && Math.abs(q.zs()[0] - (20 + LIFT)) < 1e-12;
            highZ |= q.normalX() == 0 && q.normalZ() == -1 && Math.abs(q.zs()[0] - (28 - LIFT)) < 1e-12;
            for (int i = 0; i < 4; i++) {
                assertTrue(q.alphas()[i] >= 0);
            }
        }
        assertTrue(west && lowZ && highZ, "west=" + west + " lowZ=" + lowZ + " highZ=" + highZ);
    }

    @Test
    void theShadowFadesOutOverTheBlockAboveTheTopOfTheBody() {
        // the face in the row from y = 8 to 9, vertically 8, 8.5 and 9, next to the middle of the body
        SideShadowProjector.Walls row = (cx, y, cz, out) -> {
            if (cx == 13 && cz == 24 && y == 8) {
                out[0] = 1.0;
                return SideShadowProjector.PLUS_X;
            }
            return 0;
        };
        List<Quad> quads = run(BODY, PLACE, PARAMS, 1.0, row);
        assertEquals(4, quads.size());
        double full = expected(14 - LIFT, 7.0, 24.0, 1.0, 1.0); // the same spot, inside the height of the body
        for (Quad q : quads) {
            for (int i = 0; i < 4; i++) {
                double expectedFactor = q.ys()[i] == 8.0 ? 1.0 : q.ys()[i] == 8.5 ? 0.5 : 0.0;
                double x = q.xs()[i];
                assertEquals(expected(x, 7.0, q.zs()[i], 1.0, 1.0) * expectedFactor, q.alphas()[i], 1e-6,
                    "y=" + q.ys()[i]);
            }
        }
        assertTrue(full > 0.3);
    }

    @Test
    void underTheBodyTheShadowStaysFullForTheGraceHeightAndThenFades() {
        // the face in the row from y = 2 to 3: 2 to 3 blocks under the underside at y = 5
        SideShadowProjector.Walls row = (cx, y, cz, out) -> {
            if (cx == 13 && cz == 24 && y == 2) {
                out[0] = 1.0;
                return SideShadowProjector.PLUS_X;
            }
            return 0;
        };
        for (Quad q : run(BODY, PLACE, PARAMS, 1.0, row)) {
            for (int i = 0; i < 4; i++) {
                double drop = 5 - q.ys()[i];
                double atGrace = expected(q.xs()[i], 4.5, q.zs()[i], 1.0, 1.0); // within the grace height: full
                assertEquals(atGrace * (1 - (drop - 1) / 8), q.alphas()[i], 1e-6, "y=" + q.ys()[i]);
            }
        }
    }

    @Test
    void aFaceIsCutIntoFourQuads() {
        SideShadowProjector.Walls one = (cx, y, cz, out) -> {
            if (cx == 13 && cz == 24 && y == 5) {
                out[0] = 1.0;
                return SideShadowProjector.PLUS_X;
            }
            return 0;
        };
        assertEquals(4, run(BODY, PLACE, PARAMS, 1.0, one).size());
    }

    @Test
    void brightnessAndDistanceFactorScaleTheShadow() {
        List<Quad> dim = run(BODY, PLACE, PARAMS, 0.5, wallAt(15, 0.5));
        assertFalse(dim.isEmpty());
        for (Quad q : dim) {
            for (int i = 0; i < 4; i++) {
                assertEquals(expected(q.xs()[i], q.ys()[i], q.zs()[i], 0.5, 0.5), q.alphas()[i], 1e-6);
            }
        }
    }

    @Test
    void aYawedBodyIsMeasuredFromItsRealOutline() {
        Mat3x4 yawed = Mat3x4.translation(12, 5, 24).after(Mat3x4.rotationY(Math.toRadians(35)));
        List<Quad> quads = run(BODY, yawed, PARAMS, 1.0, wallAt(15, 1.0));
        assertFalse(quads.isEmpty());
        for (Quad q : quads) {
            for (int i = 0; i < 4; i++) {
                if (q.ys()[i] < 5 || q.ys()[i] > 8) {
                    continue; // the height profile is covered elsewhere
                }
                double nearest = Double.MAX_VALUE;
                for (double lx = 0; lx <= 3.0001; lx += 0.03) {
                    for (double lz = 0; lz <= 7.0001; lz += 0.03) {
                        double wx = yawed.x(lx, 0, lz);
                        double wz = yawed.z(lx, 0, lz);
                        nearest = Math.min(nearest, Math.hypot(wx - q.xs()[i], wz - q.zs()[i]));
                    }
                }
                double expected = 0.5 * Math.max(0, 1 - nearest / 2);
                assertEquals(expected, q.alphas()[i], 0.01, "at z=" + q.zs()[i] + " y=" + q.ys()[i]);
            }
        }
    }

    @Test
    void noReachMeansNoWallShadowAndNoLookups() {
        SideShadowProjector.Walls forbidden = (cx, y, cz, out) -> {
            throw new AssertionError("walls must not be queried");
        };
        ShadowParams off = new ShadowParams(0.6, 128, 8, 1, 0.5, 0.5, 0, 0.5, TrackMode.OFF);
        assertTrue(run(BODY, PLACE, off, 1.0, forbidden).isEmpty());
        ShadowParams invisible = new ShadowParams(0.6, 128, 8, 1, 0.5, 0.5, 2, 0, TrackMode.OFF);
        assertTrue(run(BODY, PLACE, invisible, 1.0, forbidden).isEmpty());
        assertTrue(run(BODY, PLACE, PARAMS, 0.0, forbidden).isEmpty());
    }

    @Test
    void onlyCellsNearTheBodyAreLookedAt() {
        List<int[]> asked = new ArrayList<>();
        SideShadowProjector.Walls recorder = (cx, y, cz, out) -> {
            asked.add(new int[] {cx, y, cz});
            return 0;
        };
        run(BODY, PLACE, PARAMS, 1.0, recorder);
        assertFalse(asked.isEmpty());
        for (int[] cell : asked) {
            assertTrue(cell[0] >= 8 && cell[0] <= 15, "x " + cell[0]);
            assertTrue(cell[2] >= 18 && cell[2] <= 29, "z " + cell[2]);
            assertTrue(cell[1] >= 2 && cell[1] <= 8, "row " + cell[1]);
        }
    }

    @Test
    void theTopOfTheBodyFollowsEachRectanglesOwnUnderside() {
        // The upper part of a train hangs a block higher than its wheels, with the same top.
        List<ShadowRect> train = List.of(new ShadowRect(0, 0, 1, 1, 0), new ShadowRect(2, 0, 8, 1, 1));
        SideShadowProjector.Walls row = (cx, y, cz, out) -> {
            if (cx == 14 && cz == 0 && y == 8) {
                out[0] = 1.0;
                return SideShadowProjector.PLUS_X;
            }
            return 0;
        };
        List<Quad> quads = new ArrayList<>();
        new SideShadowProjector().project(train, 3, Mat3x4.translation(10.5, 5, 0.25), PARAMS, 1.0, row,
            (xs, ys, zs, alphas, nx, nz) -> quads.add(new Quad(xs.clone(), ys.clone(), zs.clone(), alphas.clone(), nx, nz)));
        assertFalse(quads.isEmpty());
        // beside the upper part (x up to 20.5 > 15, so the wall at x = 15 is inside it): the nearest part is the
        // body with its underside at y = 6 and top at 8, so the wall is dark up to y = 8 and gone at 9
        for (Quad q : quads) {
            for (int i = 0; i < 4; i++) {
                if (q.ys()[i] == 9.0) {
                    assertEquals(0.0, q.alphas()[i], 1e-9);
                }
                if (q.ys()[i] == 8.0) {
                    assertTrue(q.alphas()[i] > 0.4, "alpha at the top: " + q.alphas()[i]);
                }
            }
        }
    }
}
