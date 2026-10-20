package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

class ShadowProjectorTest {
    private record Piece(double[] xs, double[] zs, int n, double y, float alpha) {
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
            (xs, zs, n, y, alpha) -> pieces.add(new Piece(xs.clone(), zs.clone(), n, y, alpha)));
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
}
