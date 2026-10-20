package io.github.eyunya11.create_train_shadows.core;

/**
 * A rectangle of the shadow in contraption-local coordinates: {@code [minX, maxX] x [minZ, maxZ]}, with its
 * underside at local height {@code bottomY}. Unlike {@link LocalRect} the bounds need not be whole blocks.
 *
 * <p>The four corner values scale the shadow's darkness, 0 to 1, and are interpolated across the rectangle.
 * The rectangle is drawn as two triangles, split along one diagonal: from (minX, minZ) to (maxX, maxZ) when
 * {@code splitsAlongMinToMax} is set, otherwise from (maxX, minZ) to (minX, maxZ). Which one is right depends
 * on the shape of the fade (a ridge along a diagonal needs that diagonal), so whoever builds the rectangle
 * chooses. {@code a00} is the corner at (minX, minZ), {@code a10} at (maxX, minZ), {@code a01} at
 * (minX, maxZ), {@code a11} at (maxX, maxZ).
 */
public record ShadowRect(double minX, double minZ, double maxX, double maxZ, int bottomY,
    double a00, double a10, double a01, double a11, boolean splitsAlongMinToMax) {

    /** A rectangle that is equally dark everywhere. */
    public ShadowRect(double minX, double minZ, double maxX, double maxZ, int bottomY) {
        this(minX, minZ, maxX, maxZ, bottomY, 1.0, 1.0, 1.0, 1.0, true);
    }

    /** A rectangle with a fade, split along the diagonal that joins the corners with the larger sum. */
    public ShadowRect(double minX, double minZ, double maxX, double maxZ, int bottomY,
        double a00, double a10, double a01, double a11) {
        this(minX, minZ, maxX, maxZ, bottomY, a00, a10, a01, a11, a00 + a11 >= a10 + a01);
    }

    public boolean isUniform() {
        return a00 == a10 && a10 == a01 && a01 == a11;
    }

    /**
     * The darkness factor at a point of the rectangle, as it is drawn: interpolated within the triangle of the
     * split that contains the point. NaN when the point is outside the rectangle.
     */
    public double fadeAt(double x, double z) {
        final double slack = 1.0e-9;
        if (x < minX - slack || x > maxX + slack || z < minZ - slack || z > maxZ + slack) {
            return Double.NaN;
        }
        double u = (x - minX) / (maxX - minX);
        double v = (z - minZ) / (maxZ - minZ);
        if (splitsAlongMinToMax) {
            return u >= v ? a00 + u * (a10 - a00) + v * (a11 - a10) : a00 + u * (a11 - a01) + v * (a01 - a00);
        }
        return u + v <= 1 ? a00 + u * (a10 - a00) + v * (a01 - a00) : a01 + a10 - a11 + u * (a11 - a01) + v * (a11 - a10);
    }

    public double area() {
        return (maxX - minX) * (maxZ - minZ);
    }
}
