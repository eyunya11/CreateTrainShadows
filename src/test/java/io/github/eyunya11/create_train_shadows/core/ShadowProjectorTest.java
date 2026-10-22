package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

class ShadowProjectorTest {
    private record Piece(double[] xs, double[] ys, double[] zs, float[] alphas, int n) {
        double y() {
            return ys[0];
        }

        /** The alpha of a piece that is equally dark everywhere. */
        float alpha() {
            for (int i = 1; i < n; i++) {
                assertEquals(alphas[0], alphas[i], 1e-6f, "alpha varies across the piece");
            }
            return alphas[0];
        }

        double area() {
            double twice = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                twice += xs[i] * zs[j] - xs[j] * zs[i];
            }
            return Math.abs(twice) / 2;
        }

        double centreX() {
            double s = 0;
            for (int i = 0; i < n; i++) {
                s += xs[i];
            }
            return s / n;
        }

        double centreZ() {
            double s = 0;
            for (int i = 0; i < n; i++) {
                s += zs[i];
            }
            return s / n;
        }
    }

    private static final ShadowParams VANILLA = ShadowParams.VANILLA;
    private static final ShadowParams DEFAULT = ShadowParams.DEFAULT;

    private static final ShadowProjector.Ground FLAT_GROUND_AT_4 = (cx, cz, startY, depth, out) -> {
        out[0] = 4.0;
        out[1] = 1.0;
        return true;
    };

    private static ShadowRect rect(double minX, double minZ, double sizeX, double sizeZ, int bottomY) {
        return new ShadowRect(minX, minZ, minX + sizeX, minZ + sizeZ, bottomY);
    }

    private static List<Piece> run(List<ShadowRect> rects, Mat3x4 m, ShadowParams params, double distanceFactor,
        ShadowProjector.Ground ground) {
        List<Piece> pieces = new ArrayList<>();
        new ShadowProjector().project(rects, m, params, distanceFactor, ground,
            (xs, ys, zs, alphas, n) -> pieces.add(new Piece(xs.clone(), ys.clone(), zs.clone(), alphas.clone(), n)));
        return pieces;
    }

    private static List<Piece> run(List<ShadowRect> rects, Mat3x4 m, ShadowParams params,
        ShadowProjector.Ground ground) {
        return run(rects, m, params, 1.0, ground);
    }

    private static double totalArea(List<Piece> pieces) {
        return pieces.stream().mapToDouble(Piece::area).sum();
    }

    private static final List<ShadowRect> CARRIAGE = List.of(rect(0, 0, 3, 7, 0));

    @Test
    void axisAlignedRectangleKeepsItsAreaAndIsCutIntoCells() {
        List<Piece> pieces = run(CARRIAGE, Mat3x4.translation(10.3, 5, 20.6), VANILLA, FLAT_GROUND_AT_4);
        assertEquals(21.0, totalArea(pieces), 1e-9);
        // x from 10.3 to 13.3 touches 4 columns, z from 20.6 to 27.6 touches 8 rows
        assertEquals(4 * 8, pieces.size());
        for (Piece p : pieces) {
            assertEquals(4.0 + ShadowProjector.LIFT, p.y(), 1e-12);
            assertEquals(0.25f, p.alpha(), 1e-6f); // 1 block above the ground
        }
    }

    @Test
    void piecesNeverCrossCellBorders() {
        Mat3x4 m = Mat3x4.translation(3.3, 5, -8.8).after(Mat3x4.rotationY(Math.toRadians(37)));
        for (Piece p : run(CARRIAGE, m, VANILLA, FLAT_GROUND_AT_4)) {
            int cellX = (int) Math.floor(p.centreX());
            int cellZ = (int) Math.floor(p.centreZ());
            for (int i = 0; i < p.n(); i++) {
                assertTrue(p.xs()[i] >= cellX - 1e-9 && p.xs()[i] <= cellX + 1 + 1e-9, "x outside cell");
                assertTrue(p.zs()[i] >= cellZ - 1e-9 && p.zs()[i] <= cellZ + 1 + 1e-9, "z outside cell");
            }
        }
    }

    @Test
    void yawKeepsTheTotalArea() {
        for (double degrees : new double[] {0, 15, 37, 45, 90, 123, 180, 271}) {
            Mat3x4 m = Mat3x4.translation(-7.25, 5, 100.5).after(Mat3x4.rotationY(Math.toRadians(degrees)));
            assertEquals(21.0, totalArea(run(CARRIAGE, m, VANILLA, FLAT_GROUND_AT_4)), 1e-9, "yaw " + degrees);
        }
    }

    @Test
    void pitchShrinksTheFootprintByCosine() {
        double pitch = Math.toRadians(10);
        Mat3x4 m = Mat3x4.translation(10, 4.5, 20).after(Mat3x4.rotationZ(pitch));
        assertEquals(21.0 * Math.cos(pitch), totalArea(run(CARRIAGE, m, VANILLA, FLAT_GROUND_AT_4)), 1e-9);
    }

    @Test
    void theHigherEndOfASlopedBodyGetsAFainterShadow() {
        Mat3x4 m = Mat3x4.translation(10, 4.2, 20).after(Mat3x4.rotationZ(Math.toRadians(10)));
        List<Piece> pieces = run(CARRIAGE, m, VANILLA, FLAT_GROUND_AT_4);
        pieces.sort(Comparator.comparingDouble(Piece::centreX));
        Piece low = pieces.get(0);
        Piece high = pieces.get(pieces.size() - 1);
        assertTrue(low.centreX() < high.centreX());
        assertTrue(high.alpha() < low.alpha(), "alpha " + low.alpha() + " -> " + high.alpha());
        for (Piece p : pieces) {
            assertTrue(p.alpha() > 0);
        }
    }

    @Test
    void cellsWithoutAReceivingSurfaceAreSkipped() {
        ShadowProjector.Ground onlyEvenColumns = (cx, cz, startY, depth, out) -> {
            out[0] = 4.0;
            out[1] = 1.0;
            return Math.floorMod(cx, 2) == 0;
        };
        List<Piece> pieces = run(List.of(rect(0, 0, 4, 1, 0)), Mat3x4.translation(0, 5, 0), VANILLA, onlyEvenColumns);
        assertEquals(2, pieces.size());
        assertEquals(2.0, totalArea(pieces), 1e-9);
    }

    @Test
    void eachPieceSitsOnItsOwnSurface() {
        ShadowProjector.Ground steps = (cx, cz, startY, depth, out) -> {
            out[0] = 5.0 + 0.25 * cx; // the ground climbs a quarter block per column
            out[1] = 1.0;
            return true;
        };
        List<Piece> pieces = run(List.of(rect(0, 0, 3, 1, 0)), Mat3x4.translation(0, 6, 0), VANILLA, steps);
        assertEquals(3, pieces.size());
        pieces.sort(Comparator.comparingDouble(Piece::centreX));
        for (int i = 0; i < 3; i++) {
            assertEquals(5.0 + 0.25 * i + ShadowProjector.LIFT, pieces.get(i).y(), 1e-12);
            // the closer the ground, the darker: heights 1, 0.75, 0.5 above it
            assertEquals((1.0 - (1.0 - 0.25 * i) / 2) * 0.5, pieces.get(i).alpha(), 1e-6);
        }
    }

    @Test
    void lightScalesTheAlphaPerCell() {
        ShadowProjector.Ground dimRight = (cx, cz, startY, depth, out) -> {
            out[0] = 4.0;
            out[1] = cx == 0 ? 1.0 : 0.5;
            return true;
        };
        List<Piece> pieces = run(List.of(rect(0, 0, 2, 1, 0)), Mat3x4.translation(0, 4, 0), VANILLA, dimRight);
        pieces.sort(Comparator.comparingDouble(Piece::centreX));
        assertEquals(0.5f, pieces.get(0).alpha(), 1e-6f);
        assertEquals(0.25f, pieces.get(1).alpha(), 1e-6f);
    }

    @Test
    void tooHighAboveTheGroundDrawsNothing() {
        // 2 blocks up is where vanilla's alpha reaches zero
        assertTrue(run(CARRIAGE, Mat3x4.translation(0, 6.0, 0), VANILLA, FLAT_GROUND_AT_4).isEmpty());
        assertFalse(run(CARRIAGE, Mat3x4.translation(0, 5.9, 0), VANILLA, FLAT_GROUND_AT_4).isEmpty());
    }

    @Test
    void noDistanceFactorDrawsNothingAndNeverAsksTheGround() {
        ShadowProjector.Ground forbidden = (cx, cz, startY, depth, out) -> {
            throw new AssertionError("ground must not be queried");
        };
        assertTrue(run(CARRIAGE, Mat3x4.translation(0, 5, 0), DEFAULT, 0.0, forbidden).isEmpty());
        assertTrue(run(CARRIAGE, Mat3x4.translation(0, 5, 0), DEFAULT, -0.3, forbidden).isEmpty());
    }

    @Test
    void groundIsSearchedFromTheBlockRowOfTheUndersideForTheVisibleDepth() {
        List<int[]> calls = new ArrayList<>();
        ShadowProjector.Ground recorder = (cx, cz, startY, depth, out) -> {
            calls.add(new int[] {startY, depth});
            return false;
        };
        run(List.of(rect(0, 0, 1, 1, 0)), Mat3x4.translation(0.2, 5.5, 0.2), VANILLA, recorder);
        assertFalse(calls.isEmpty());
        for (int[] call : calls) {
            assertEquals(5, call[0]);
            assertEquals(3, call[1]);
        }
    }

    @Test
    void aBodyStandingOnItsSideHasNoFootprint() {
        Mat3x4 onSide = Mat3x4.translation(0, 5, 0).after(Mat3x4.rotationZ(Math.toRadians(90)));
        // Local x now points straight up and local z stays horizontal: a vertical wall, zero area.
        assertTrue(run(CARRIAGE, onSide, VANILLA, FLAT_GROUND_AT_4).isEmpty());
    }

    @Test
    void severalRectanglesAreAllProjected() {
        List<ShadowRect> two = List.of(rect(0, 0, 2, 2, 0), rect(5, 0, 1, 3, 0));
        assertEquals(4.0 + 3.0, totalArea(run(two, Mat3x4.translation(0, 5, 0), VANILLA, FLAT_GROUND_AT_4)), 1e-9);
    }

    @Test
    void aStandardTrainIsAsDarkAsPossibleWhereverTheFloorIs() {
        // Create's layout: the bogey's cell is one block above the track, the body floor one more above that.
        List<ShadowRect> train = List.of(rect(0, 0, 1, 1, 0), rect(2, 0, 6, 1, 1));
        List<Piece> pieces = run(train, Mat3x4.translation(0, 5.0, 0), DEFAULT, FLAT_GROUND_AT_4);
        assertFalse(pieces.isEmpty());
        for (Piece p : pieces) {
            assertEquals(0.6f, p.alpha(), 1e-6f, "piece at x=" + p.centreX());
        }
        assertEquals(1.0 + 6.0, totalArea(pieces), 1e-9);
    }

    @Test
    void aTrainHighAboveTheGroundFadesOut() {
        List<Piece> pieces = run(CARRIAGE, Mat3x4.translation(0, 9.0, 0), DEFAULT, FLAT_GROUND_AT_4);
        assertFalse(pieces.isEmpty());
        for (Piece p : pieces) {
            assertEquals(0.3f, p.alpha(), 1e-6f); // 5 blocks up: 4 beyond the grace height, half of the fade
        }
        assertTrue(run(CARRIAGE, Mat3x4.translation(0, 13.0, 0), DEFAULT, FLAT_GROUND_AT_4).isEmpty());
    }

    // --- tilted surfaces (a sloping track): the height of every vertex follows the slope of its cell

    private static final ShadowProjector.Ground SLOPE_TOWARDS_PLUS_Z = (cx, cz, startY, depth, out) -> {
        out[0] = 4.0 + 0.5 * (cz + 0.5 - 20); // height at the middle of the cell on the plane 4 + 0.5 * (z - 20)
        out[1] = 1.0;
        out[2] = 0.0;
        out[3] = 0.5;
        return true;
    };

    @Test
    void aTiltedSurfaceGivesTiltedPolygons() {
        // 3 blocks above the low end of the slope and 1 above its high end, within the default fade
        List<Piece> pieces = run(List.of(rect(3, 20, 2, 4, 0)), Mat3x4.translation(0, 7, 0), DEFAULT,
            SLOPE_TOWARDS_PLUS_Z);
        assertFalse(pieces.isEmpty());
        for (Piece p : pieces) {
            for (int i = 0; i < p.n(); i++) {
                assertEquals(4.0 + 0.5 * (p.zs()[i] - 20) + ShadowProjector.LIFT, p.ys()[i], 1e-9,
                    "vertex at z=" + p.zs()[i]);
            }
        }
        assertEquals(8.0, totalArea(pieces), 1e-9);
    }

    @Test
    void theHeightAboveATiltedSurfaceIsMeasuredAtTheMiddleOfEachPiece() {
        // the underside is level at y = 6; the surface climbs half a block per block, so higher up it is closer
        List<Piece> pieces = run(List.of(rect(3, 20, 1, 4, 0)), Mat3x4.translation(0, 6, 0), VANILLA,
            SLOPE_TOWARDS_PLUS_Z);
        pieces.sort(Comparator.comparingDouble(Piece::centreZ));
        assertEquals(4, pieces.size());
        for (int i = 0; i + 1 < pieces.size(); i++) {
            assertTrue(pieces.get(i + 1).alpha() > pieces.get(i).alpha(), "closer ground is darker");
        }
        Piece first = pieces.get(0);
        double height = 6 - (4.0 + 0.5 * (first.centreZ() - 20));
        assertEquals((1.0 - height / 2) * 0.5, first.alpha(), 1e-6);
    }

    @Test
    void aLevelSurfaceDoesNotInheritASlopeFromTheLastCell() {
        // the second and third values are cleared before each question, so a ground that does not set them is level
        ShadowProjector.Ground staleSlope = new ShadowProjector.Ground() {
            private boolean first = true;

            @Override
            public boolean sample(int cx, int cz, int startY, int depth, double[] out) {
                out[0] = 4.0;
                out[1] = 1.0;
                if (first) {
                    out[2] = 5.0;
                    out[3] = 5.0;
                    first = false;
                } else {
                    assertEquals(0.0, out[2]);
                    assertEquals(0.0, out[3]);
                }
                return true;
            }
        };
        run(List.of(rect(0, 0, 3, 3, 0)), Mat3x4.translation(0, 5, 0), VANILLA, staleSlope);
    }

    @Test
    void theDistanceFactorScalesEveryAlpha() {
        for (Piece p : run(CARRIAGE, Mat3x4.translation(0, 5, 0), DEFAULT, 0.5, FLAT_GROUND_AT_4)) {
            assertEquals(0.3f, p.alpha(), 1e-6f);
        }
    }

    @Test
    void thePartsHigherThanTheLowestSearchDeeper() {
        List<int[]> calls = new ArrayList<>();
        ShadowProjector.Ground recorder = (cx, cz, startY, depth, out) -> {
            calls.add(new int[] {startY, depth});
            return false;
        };
        run(List.of(rect(0, 0, 1, 1, 0), rect(3, 0, 1, 1, 2)), Mat3x4.translation(0.2, 5.5, 0.2), DEFAULT, recorder);
        int lowestDepth = ShadowOpacity.searchDepth(DEFAULT, 0);
        int upperDepth = ShadowOpacity.searchDepth(DEFAULT, 2);
        assertTrue(calls.stream().anyMatch(c -> c[1] == lowestDepth && c[0] == 5));
        assertTrue(calls.stream().anyMatch(c -> c[1] == upperDepth && c[0] == 7));
    }

    // --- soft edges: the rectangle's own fade is interpolated across it and multiplied into every vertex

    private static final double BASE_ALPHA = 0.25; // VANILLA, one block above flat ground

    @Test
    void aFadeAcrossARectangleIsLinearAndMeetsTheCellBorders() {
        // dark on the right, nothing on the left, over four cells
        ShadowRect fade = new ShadowRect(0, 0, 4, 1, 0, 0.0, 1.0, 0.0, 1.0);
        List<Piece> pieces = run(List.of(fade), Mat3x4.translation(0, 5, 0), VANILLA, FLAT_GROUND_AT_4);
        assertEquals(4.0, totalArea(pieces), 1e-9);
        for (Piece p : pieces) {
            for (int i = 0; i < p.n(); i++) {
                assertEquals(BASE_ALPHA * p.xs()[i] / 4.0, p.alphas()[i], 1e-6, "vertex at x=" + p.xs()[i]);
            }
        }
    }

    @Test
    void aCornerThatFadesTowardsTwoEdgesHasAMitredRidge() {
        // 0 on three corners and 1 at the far one: the fade is min(u, v), not u * v
        ShadowRect corner = new ShadowRect(0, 0, 2, 2, 0, 0.0, 0.0, 0.0, 1.0);
        List<Piece> pieces = run(List.of(corner), Mat3x4.translation(0, 5, 0), VANILLA, FLAT_GROUND_AT_4);
        assertEquals(4.0, totalArea(pieces), 1e-9);
        boolean checkedMiddle = false;
        for (Piece p : pieces) {
            for (int i = 0; i < p.n(); i++) {
                double expected = BASE_ALPHA * Math.min(p.xs()[i], p.zs()[i]) / 2.0;
                assertEquals(expected, p.alphas()[i], 1e-6, "vertex at " + p.xs()[i] + ", " + p.zs()[i]);
                checkedMiddle |= Math.abs(p.xs()[i] - 1) < 1e-9 && Math.abs(p.zs()[i] - 1) < 1e-9;
            }
        }
        assertTrue(checkedMiddle, "the middle of the corner is a vertex of the pieces");
    }

    @Test
    void theOtherDiagonalIsUsedWhenItsCornersAreTheDarker() {
        // dark on the (maxX, minZ) and (minX, maxZ) corners
        ShadowRect ridge = new ShadowRect(0, 0, 2, 2, 0, 0.0, 1.0, 1.0, 0.0);
        List<Piece> pieces = run(List.of(ridge), Mat3x4.translation(0, 5, 0), VANILLA, FLAT_GROUND_AT_4);
        assertEquals(4.0, totalArea(pieces), 1e-9);
        for (Piece p : pieces) {
            for (int i = 0; i < p.n(); i++) {
                if (Math.abs(p.xs()[i] - 1) < 1e-9 && Math.abs(p.zs()[i] - 1) < 1e-9) {
                    // on the ridge between the two dark corners the fade is 1, not the 0.5 of the other split
                    assertEquals(BASE_ALPHA, p.alphas()[i], 1e-6);
                }
            }
        }
    }

    @Test
    void anyFadeKeepsTheAreaOfTheRectangle() {
        double[][] corners = {{0, 0, 0, 1}, {1, 0, 0, 0}, {0.2, 0.9, 0.4, 0.7}, {0, 1, 1, 0}};
        for (double[] a : corners) {
            ShadowRect r = new ShadowRect(0.3, 0.4, 5.1, 3.9, 0, a[0], a[1], a[2], a[3]);
            Mat3x4 m = Mat3x4.translation(2.2, 5, 7.7).after(Mat3x4.rotationY(Math.toRadians(41)));
            // slivers under 1e-4 are dropped on purpose, so the total may fall a little short
            assertEquals(r.area(), totalArea(run(List.of(r), m, VANILLA, FLAT_GROUND_AT_4)), 1e-3);
        }
    }

    @Test
    void aFadeFollowsTheRectangleWhenItIsYawed() {
        // The fade belongs to the rectangle, not to the world: after turning, the dark end is still the far end.
        ShadowRect fade = new ShadowRect(0, 0, 6, 1, 0, 0.0, 1.0, 0.0, 1.0);
        Mat3x4 m = Mat3x4.translation(20, 5, 20).after(Mat3x4.rotationY(Math.toRadians(90)));
        // rotation by 90 degrees about Y sends local +x to world -z
        for (Piece p : run(List.of(fade), m, VANILLA, FLAT_GROUND_AT_4)) {
            for (int i = 0; i < p.n(); i++) {
                double localX = 20 - p.zs()[i];
                assertEquals(BASE_ALPHA * localX / 6.0, p.alphas()[i], 1e-6, "vertex at z=" + p.zs()[i]);
            }
        }
    }

    @Test
    void theFadeIsMultipliedByTheDarknessTheGroundAllows() {
        ShadowRect fade = new ShadowRect(0, 0, 2, 1, 0, 0.0, 1.0, 0.0, 1.0);
        ShadowProjector.Ground dim = (cx, cz, startY, depth, out) -> {
            out[0] = 4.0;
            out[1] = 0.5;
            return true;
        };
        for (Piece p : run(List.of(fade), Mat3x4.translation(0, 5, 0), VANILLA, 0.5, dim)) {
            for (int i = 0; i < p.n(); i++) {
                assertEquals(0.25 * 0.5 * 0.5 * p.xs()[i] / 2.0, p.alphas()[i], 1e-6);
            }
        }
    }

    /**
     * Surfaces one above the other in every column: the tops of blocks, highest first. Like the real ground, each
     * answer is the first one whose air row is at or below the row asked from and within the depth.
     */
    private static ShadowProjector.Ground layers(double... tops) {
        return (cx, cz, startY, depth, out) -> {
            for (double top : tops) {
                int airRow = (int) Math.floor(top - 1.0e-6) + 1;
                if (airRow <= startY && airRow > startY - depth) {
                    out[0] = top;
                    out[1] = 1.0;
                    return true;
                }
            }
            return false;
        };
    }

    @Test
    void theFloorUnderABlockThatHangsUnderTheBodyTakesAFainterShadowToo() {
        // A block hung under the carriage (its top at y = 4) and the floor of the pit under it (at y = 1).
        List<Piece> pieces = run(CARRIAGE, Mat3x4.translation(10.3, 5, 20.6), DEFAULT, layers(4.0, 1.0));
        List<Piece> upper = pieces.stream().filter(p -> Math.abs(p.y() - (4.0 + ShadowProjector.LIFT)) < 1e-9).toList();
        List<Piece> lower = pieces.stream().filter(p -> Math.abs(p.y() - (1.0 + ShadowProjector.LIFT)) < 1e-9).toList();
        assertEquals(pieces.size(), upper.size() + lower.size());
        assertEquals(21.0, totalArea(upper), 1e-9);
        assertEquals(21.0, totalArea(lower), 1e-9);
        for (Piece p : upper) {
            assertEquals(0.6f, p.alpha(), 1e-6f); // 1 block above it: inside the grace height
        }
        for (Piece p : lower) {
            assertEquals(0.6f * (1 - 3.0f / 8.0f), p.alpha(), 1e-6f); // 4 blocks above it: 3 beyond the grace height
        }
    }

    @Test
    void aColumnTakesAtMostThreeSurfaces() {
        List<Piece> pieces = run(rect1(), Mat3x4.translation(10.2, 7, 20.2), DEFAULT, layers(5.0, 4.0, 3.0, 2.0, 1.0));
        assertEquals(ShadowProjector.MAX_SURFACES, pieces.size());
    }

    @Test
    void aSurfaceBeyondTheFadeGetsNothingAndEndsTheSearch() {
        // The second surface is 11 blocks under the body: past the fade height
        List<Piece> pieces = run(CARRIAGE, Mat3x4.translation(10.3, 5, 20.6), DEFAULT, layers(4.0, -6.0));
        assertEquals(21.0, totalArea(pieces), 1e-9);
        for (Piece p : pieces) {
            assertEquals(4.0 + ShadowProjector.LIFT, p.y(), 1e-9);
        }
    }

    @Test
    void aGroundThatAnswersWithTheSameSurfaceAgainIsTakenOnce() {
        // The projector asks again from under the surface it was given; an answer that is not under it ends it
        assertEquals(21.0, totalArea(run(CARRIAGE, Mat3x4.translation(10.3, 5, 20.6), DEFAULT, FLAT_GROUND_AT_4)), 1e-9);
    }

    private static List<ShadowRect> rect1() {
        return List.of(rect(0, 0, 0.5, 0.5, 0));
    }
}

