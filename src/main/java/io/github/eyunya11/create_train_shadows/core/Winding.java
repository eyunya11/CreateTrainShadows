package io.github.eyunya11.create_train_shadows.core;

/**
 * Which way round to write the vertices of a polygon. A render type that culls back faces draws a polygon
 * only when its vertices run counter-clockwise as seen from the front, that is when the right-hand rule on
 * (p1 - p0) x (p2 - p0) points at the viewer.
 */
public final class Winding {
    private Winding() {
    }

    /**
     * Whether a polygon lying flat, with the vertices (xs[i], y, zs[i]) in the given order, faces down and has to
     * be written in reverse to face up.
     */
    public static boolean needsReversingToFaceUp(double[] xs, double[] zs, int n) {
        // For (0,0), (1,0), (0,1) in (x, z) the sum is +1 and the right-hand rule gives -Y, so a positive sum is down
        double twiceArea = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            twiceArea += xs[i] * zs[j] - xs[j] * zs[i];
        }
        return twiceArea > 0;
    }

    /**
     * Whether a vertical quad (the first three vertices decide) faces away from the horizontal direction
     * (normalX, 0, normalZ) and has to be written in reverse to face it.
     */
    public static boolean needsReversingToFace(double[] xs, double[] ys, double[] zs, int normalX, int normalZ) {
        double ax = xs[1] - xs[0], ay = ys[1] - ys[0], az = zs[1] - zs[0];
        double bx = xs[2] - xs[0], by = ys[2] - ys[0], bz = zs[2] - zs[0];
        double facingX = ay * bz - az * by;
        double facingZ = ax * by - ay * bx;
        return facingX * normalX + facingZ * normalZ < 0;
    }
}
