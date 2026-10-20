package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Mat3x4Test {
    @Test
    void theInverseUndoesTheTransform() {
        Mat3x4 m = Mat3x4.translation(12.5, -3, 40.25)
            .after(Mat3x4.rotationY(Math.toRadians(37)))
            .after(Mat3x4.rotationZ(Math.toRadians(-12)))
            .after(Mat3x4.translation(1, 2, 3));
        Mat3x4 inverse = m.inverse();
        for (double[] p : new double[][] {{0, 0, 0}, {1, 2, 3}, {-4.5, 7, 0.25}, {100, -20, 33}}) {
            double x = m.x(p[0], p[1], p[2]), y = m.y(p[0], p[1], p[2]), z = m.z(p[0], p[1], p[2]);
            assertEquals(p[0], inverse.x(x, y, z), 1e-9);
            assertEquals(p[1], inverse.y(x, y, z), 1e-9);
            assertEquals(p[2], inverse.z(x, y, z), 1e-9);
        }
    }

    @Test
    void theInverseOfARotationIsItsTranspose() {
        Mat3x4 inverse = Mat3x4.rotationY(Math.toRadians(90)).inverse();
        // rotating by 90 degrees sends (1, 0, 0) to (0, 0, -1); the inverse sends it back
        assertEquals(1.0, inverse.x(0, 0, -1), 1e-12);
        assertEquals(0.0, inverse.z(0, 0, -1), 1e-12);
    }

    @Test
    void aFlattenedTransformHasNoInverse() {
        Mat3x4 flat = new Mat3x4(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0);
        assertThrows(IllegalStateException.class, flat::inverse);
    }
}
