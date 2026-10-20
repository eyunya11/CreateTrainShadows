package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FootprintTest {
    private static int totalColumns(List<LocalRect> rects) {
        int total = 0;
        for (LocalRect r : rects) {
            total += r.sizeX() * r.sizeZ();
        }
        return total;
    }

    @Test
    void solidBoxBecomesOneRectangle() {
        Footprint.Builder b = new Footprint.Builder();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = -2; z < 5; z++) {
                    b.add(x, y, z);
                }
            }
        }
        List<LocalRect> rects = b.build();
        assertEquals(1, rects.size());
        assertEquals(new LocalRect(0, -2, 3, 7, 0), rects.get(0));
    }

    @Test
    void bottomIsTheLowestBlockOfTheColumn() {
        Footprint.Builder b = new Footprint.Builder();
        b.add(0, 5, 0);
        b.add(0, 2, 0);
        b.add(0, 9, 0);
        assertEquals(List.of(new LocalRect(0, 0, 1, 1, 2)), b.build());
    }

    @Test
    void rectanglesAreDisjointAndCoverEveryColumnOnce() {
        // An L shape with a hole and a step in height.
        Set<String> expected = new HashSet<>();
        Footprint.Builder b = new Footprint.Builder();
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 5; z++) {
                boolean hole = x == 2 && z == 2;
                boolean cutOut = x >= 4 && z >= 3;
                if (hole || cutOut) {
                    continue;
                }
                int y = x < 3 ? 0 : 1;
                b.add(x, y, z);
                expected.add(x + "," + z);
            }
        }
        List<LocalRect> rects = b.build();

        Set<String> covered = new HashSet<>();
        for (LocalRect r : rects) {
            for (int x = r.minX(); x < r.maxX(); x++) {
                for (int z = r.minZ(); z < r.maxZ(); z++) {
                    assertTrue(covered.add(x + "," + z), "column covered twice: " + x + "," + z);
                    int expectedBottom = x < 3 ? 0 : 1;
                    assertEquals(expectedBottom, r.bottomY(), "bottom of column " + x + "," + z);
                }
            }
        }
        assertEquals(expected, covered);
        assertEquals(expected.size(), totalColumns(rects));
    }

    @Test
    void columnsAtDifferentHeightsAreNotMerged() {
        Footprint.Builder b = new Footprint.Builder();
        b.add(0, 0, 0);
        b.add(1, 1, 0);
        List<LocalRect> rects = b.build();
        assertEquals(2, rects.size());
    }

    @Test
    void emptyInputGivesNoRectangles() {
        assertTrue(new Footprint.Builder().build().isEmpty());
    }

    @Test
    void negativeCoordinatesWork() {
        Footprint.Builder b = new Footprint.Builder();
        b.add(-3, 0, -3);
        b.add(-2, 0, -3);
        b.add(-3, 0, -2);
        b.add(-2, 0, -2);
        assertEquals(List.of(new LocalRect(-3, -3, 2, 2, 0)), b.build());
    }
}
