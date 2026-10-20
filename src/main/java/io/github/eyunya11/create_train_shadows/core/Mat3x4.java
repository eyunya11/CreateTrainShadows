package io.github.eyunya11.create_train_shadows.core;

/**
 * An affine transform of 3D points: {@code p' = A * p + t}, stored as three rows of {@code [a b c t]}.
 * No Minecraft types, so the shadow maths can be reused on other versions and loaders.
 */
public final class Mat3x4 {
    private final double m00, m01, m02, m03;
    private final double m10, m11, m12, m13;
    private final double m20, m21, m22, m23;

    public Mat3x4(double m00, double m01, double m02, double m03,
        double m10, double m11, double m12, double m13,
        double m20, double m21, double m22, double m23) {
        this.m00 = m00;
        this.m01 = m01;
        this.m02 = m02;
        this.m03 = m03;
        this.m10 = m10;
        this.m11 = m11;
        this.m12 = m12;
        this.m13 = m13;
        this.m20 = m20;
        this.m21 = m21;
        this.m22 = m22;
        this.m23 = m23;
    }

    /**
     * Builds the transform from where it sends the origin and the three unit axes, which is how a matrix
     * library's output is easiest to read without caring about its row/column order.
     */
    public static Mat3x4 fromImages(double[] origin, double[] unitX, double[] unitY, double[] unitZ) {
        return new Mat3x4(
            unitX[0] - origin[0], unitY[0] - origin[0], unitZ[0] - origin[0], origin[0],
            unitX[1] - origin[1], unitY[1] - origin[1], unitZ[1] - origin[1], origin[1],
            unitX[2] - origin[2], unitY[2] - origin[2], unitZ[2] - origin[2], origin[2]);
    }

    public static Mat3x4 translation(double x, double y, double z) {
        return new Mat3x4(1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z);
    }

    /** Rotation about the Y axis by {@code radians}, counter-clockwise seen from +Y (as in a right-handed system). */
    public static Mat3x4 rotationY(double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new Mat3x4(c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0);
    }

    /** Rotation about the Z axis by {@code radians}, counter-clockwise seen from +Z. */
    public static Mat3x4 rotationZ(double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new Mat3x4(c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0);
    }

    /** The transform that applies {@code first}, then {@code this}. */
    public Mat3x4 after(Mat3x4 first) {
        return new Mat3x4(
            m00 * first.m00 + m01 * first.m10 + m02 * first.m20,
            m00 * first.m01 + m01 * first.m11 + m02 * first.m21,
            m00 * first.m02 + m01 * first.m12 + m02 * first.m22,
            m00 * first.m03 + m01 * first.m13 + m02 * first.m23 + m03,
            m10 * first.m00 + m11 * first.m10 + m12 * first.m20,
            m10 * first.m01 + m11 * first.m11 + m12 * first.m21,
            m10 * first.m02 + m11 * first.m12 + m12 * first.m22,
            m10 * first.m03 + m11 * first.m13 + m12 * first.m23 + m13,
            m20 * first.m00 + m21 * first.m10 + m22 * first.m20,
            m20 * first.m01 + m21 * first.m11 + m22 * first.m21,
            m20 * first.m02 + m21 * first.m12 + m22 * first.m22,
            m20 * first.m03 + m21 * first.m13 + m22 * first.m23 + m23);
    }

    public double x(double x, double y, double z) {
        return m00 * x + m01 * y + m02 * z + m03;
    }

    public double y(double x, double y, double z) {
        return m10 * x + m11 * y + m12 * z + m13;
    }

    public double z(double x, double y, double z) {
        return m20 * x + m21 * y + m22 * z + m23;
    }
}
