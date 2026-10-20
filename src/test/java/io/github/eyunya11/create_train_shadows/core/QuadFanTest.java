package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class QuadFanTest {
    private static int[] quad(int n, int quad) {
        return new int[] {QuadFan.index(n, quad, 0), QuadFan.index(n, quad, 1), QuadFan.index(n, quad, 2),
            QuadFan.index(n, quad, 3)};
    }

    @Test
    void polygonsWithFewerThanThreeVerticesGiveNothing() {
        assertEquals(0, QuadFan.quadCount(0));
        assertEquals(0, QuadFan.quadCount(2));
    }

    @Test
    void aTriangleIsOneQuadWithTheLastVertexRepeated() {
        assertEquals(1, QuadFan.quadCount(3));
        assertArrayEquals(new int[] {0, 1, 2, 2}, quad(3, 0));
    }

    @Test
    void aQuadIsItself() {
        assertEquals(1, QuadFan.quadCount(4));
        assertArrayEquals(new int[] {0, 1, 2, 3}, quad(4, 0));
    }

    @Test
    void aPentagonIsAQuadAndATriangle() {
        assertEquals(2, QuadFan.quadCount(5));
        assertArrayEquals(new int[] {0, 1, 2, 3}, quad(5, 0));
        assertArrayEquals(new int[] {0, 3, 4, 4}, quad(5, 1));
    }

    @Test
    void anOctagonIsThreeQuadsAndATriangle() {
        assertEquals(3, QuadFan.quadCount(8));
        assertArrayEquals(new int[] {0, 1, 2, 3}, quad(8, 0));
        assertArrayEquals(new int[] {0, 3, 4, 5}, quad(8, 1));
        assertArrayEquals(new int[] {0, 5, 6, 7}, quad(8, 2));
    }

    @Test
    void everyVertexIsUsedAndNoIndexIsOutOfRange() {
        for (int n = 3; n <= 16; n++) {
            Set<Integer> used = new HashSet<>();
            for (int q = 0; q < QuadFan.quadCount(n); q++) {
                for (int corner = 0; corner < 4; corner++) {
                    int i = QuadFan.index(n, q, corner);
                    assertTrue(i >= 0 && i < n, "index " + i + " out of range for " + n);
                    used.add(i);
                }
            }
            assertEquals(n, used.size(), "vertices used for n=" + n);
        }
    }
}
