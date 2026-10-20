package io.github.eyunya11.create_train_shadows.core;

import java.util.List;

/**
 * Projects the footprint of a moving body straight down onto the ground and reports the pieces to draw.
 *
 * <p>Each footprint rectangle is moved into the world, flattened onto the XZ plane (which gives a
 * parallelogram once the body is yawed or pitched), and cut along the world's block grid. Every piece is
 * handed to the {@link Ground} to find the surface that receives it, and to the {@link Sink} with an alpha
 * for each corner: the darkness the ground allows (how far the body is above it, how well it is lit, how far
 * the camera is) times the rectangle's own fade, which is what softens the edge of the shadow.
 *
 * <p>Height is measured from the lowest part of the body: a rectangle whose underside is a block above the
 * lowest one is treated as if it sat at the lowest level, so a body is as dark under its upper floor as under
 * its wheels, and fades only when the whole body is high above the ground.
 *
 * <p>One instance owns scratch buffers, so it must not be shared between threads; the render thread is
 * the only user.
 */
public final class ShadowProjector {
    /**
     * How far a shadow is lifted off the surface it falls on. Vanilla lifts by 1/64 of a block, but at a convex
     * edge two shadows (on a top and on a side face) then leave a gap of that width between them, which shows
     * as a thin bright line along the edge. The shadow render type pulls the shadow towards the camera to
     * avoid z-fighting, so a much smaller lift is enough.
     */
    public static final double LIFT = 1.0 / 512.0;

    private static final double MIN_AREA = 1.0e-4;
    private static final double MIN_DETERMINANT = 1.0e-9;

    public interface Ground {
        /**
         * Finds the surface that receives a shadow in the world cell (cellX, cellZ), looking from the block
         * row {@code startY} downwards for at most {@code depth} rows.
         *
         * @param out receives, when a surface is found: [0] the height of the surface at the middle of the
         *            cell, [1] the light brightness 0..1, [2] and [3] how much the height changes per block
         *            towards +x and +z (both 0 on a level surface; the caller clears them before asking)
         * @return whether a surface that can receive a shadow was found
         */
        boolean sample(int cellX, int cellZ, int startY, int depth, double[] out);
    }

    public interface Sink {
        /**
         * A convex polygon with vertices (xs[i], ys[i], zs[i]) for i in [0, n), with an alpha for each vertex
         * that is to be interpolated across it. It is flat on level ground and tilted on a slope.
         */
        void polygon(double[] xs, double[] ys, double[] zs, float[] alphas, int n);
    }

    private final double[] sourceU = new double[4];
    private final double[] sourceV = new double[4];
    private final double[] polyX = new double[4];
    private final double[] polyZ = new double[4];
    private final double[] bufAX = new double[16];
    private final double[] bufAZ = new double[16];
    private final double[] bufBX = new double[16];
    private final double[] bufBZ = new double[16];
    private final double[] heights = new double[16];
    private final float[] alphas = new float[16];
    private final double[] hit = new double[4];

    // The current rectangle, shared by the helpers below
    private double c00x, c00y, c00z, eUx, eUy, eUz, eVx, eVy, eVz, det;
    private double standoff;
    private int depth;
    private ShadowParams params;
    private double distanceFactor;
    private Ground ground;
    private Sink sink;

    /**
     * @param rects          the shadow footprint, in the body's local block coordinates
     * @param localToWorld   the body's local-to-world transform for this frame
     * @param params         how the shadow looks
     * @param distanceFactor {@link ShadowOpacity#distanceFactor} for the camera, multiplied into every alpha
     */
    public void project(List<ShadowRect> rects, Mat3x4 localToWorld, ShadowParams params, double distanceFactor,
        Ground ground, Sink sink) {
        if (distanceFactor <= 0 || rects.isEmpty()) {
            return;
        }
        this.params = params;
        this.distanceFactor = distanceFactor;
        this.ground = ground;
        this.sink = sink;

        int lowest = Integer.MAX_VALUE;
        for (ShadowRect rect : rects) {
            lowest = Math.min(lowest, rect.bottomY());
        }

        for (ShadowRect rect : rects) {
            standoff = rect.bottomY() - lowest;
            depth = ShadowOpacity.searchDepth(params, standoff);

            double y = rect.bottomY();
            c00x = localToWorld.x(rect.minX(), y, rect.minZ());
            c00y = localToWorld.y(rect.minX(), y, rect.minZ());
            c00z = localToWorld.z(rect.minX(), y, rect.minZ());
            eUx = localToWorld.x(rect.maxX(), y, rect.minZ()) - c00x;
            eUy = localToWorld.y(rect.maxX(), y, rect.minZ()) - c00y;
            eUz = localToWorld.z(rect.maxX(), y, rect.minZ()) - c00z;
            eVx = localToWorld.x(rect.minX(), y, rect.maxZ()) - c00x;
            eVy = localToWorld.y(rect.minX(), y, rect.maxZ()) - c00y;
            eVz = localToWorld.z(rect.minX(), y, rect.maxZ()) - c00z;

            // The underside is the parallelogram P(u, v) = c00 + u * eU + v * eV, u and v in [0, 1].
            det = eUx * eVz - eUz * eVx;
            if (Math.abs(det) < MIN_DETERMINANT) {
                continue; // standing on its side; there is no footprint
            }

            double a00 = rect.a00(), a10 = rect.a10(), a01 = rect.a01(), a11 = rect.a11();
            if (rect.isUniform()) {
                setSource(4, 0, 0, 1, 0, 1, 1, 0, 1);
                projectSource(a00, 0, 0);
            } else if (rect.splitsAlongMinToMax()) {
                // (0,0) (1,0) (1,1) and (0,0) (1,1) (0,1)
                setSource(3, 0, 0, 1, 0, 1, 1, 0, 0);
                projectSource(a00, a10 - a00, a11 - a10);
                setSource(3, 0, 0, 1, 1, 0, 1, 0, 0);
                projectSource(a00, a11 - a01, a01 - a00);
            } else {
                // (0,0) (1,0) (0,1) and (1,0) (1,1) (0,1)
                setSource(3, 0, 0, 1, 0, 0, 1, 0, 0);
                projectSource(a00, a10 - a00, a01 - a00);
                setSource(3, 1, 0, 1, 1, 0, 1, 0, 0);
                projectSource(a01 + a10 - a11, a11 - a01, a11 - a10);
            }
        }
    }

    /** Sets the polygon to project, as corners given in the rectangle's (u, v) coordinates. */
    private void setSource(int n, double u0, double v0, double u1, double v1, double u2, double v2, double u3,
        double v3) {
        sourceU[0] = u0;
        sourceV[0] = v0;
        sourceU[1] = u1;
        sourceV[1] = v1;
        sourceU[2] = u2;
        sourceV[2] = v2;
        sourceU[3] = u3;
        sourceV[3] = v3;
        sourceCount = n;
    }

    private int sourceCount;

    /**
     * Projects the current source polygon, whose fade is {@code fade0 + fadeU * u + fadeV * v}.
     */
    private void projectSource(double fade0, double fadeU, double fadeV) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < sourceCount; i++) {
            polyX[i] = c00x + sourceU[i] * eUx + sourceV[i] * eVx;
            polyZ[i] = c00z + sourceU[i] * eUz + sourceV[i] * eVz;
            minX = Math.min(minX, polyX[i]);
            maxX = Math.max(maxX, polyX[i]);
            minZ = Math.min(minZ, polyZ[i]);
            maxZ = Math.max(maxZ, polyZ[i]);
        }

        for (int cellX = (int) Math.floor(minX); cellX <= (int) Math.floor(maxX); cellX++) {
            for (int cellZ = (int) Math.floor(minZ); cellZ <= (int) Math.floor(maxZ); cellZ++) {
                int n = clipToCell(cellX, cellZ);
                if (n < 3 || area(bufAX, bufAZ, n) < MIN_AREA) {
                    continue;
                }

                // Height of the underside above the middle of this piece.
                double centreX = 0, centreZ = 0;
                for (int i = 0; i < n; i++) {
                    centreX += bufAX[i];
                    centreZ += bufAZ[i];
                }
                centreX /= n;
                centreZ /= n;
                double undersideY = c00y + u(centreX, centreZ) * eUy + v(centreX, centreZ) * eVy;

                int startY = (int) Math.floor(undersideY + 1.0e-6);
                hit[2] = 0;
                hit[3] = 0;
                if (!ground.sample(cellX, cellZ, startY, depth, hit)) {
                    continue;
                }
                // The surface may be tilted (a sloping track): its height at a point of the cell
                double middleX = cellX + 0.5, middleZ = cellZ + 0.5;
                double surfaceHere = hit[0] + hit[2] * (centreX - middleX) + hit[3] * (centreZ - middleZ);
                double height = undersideY - surfaceHere - standoff;
                double base = ShadowOpacity.alpha(params, height, hit[1]) * distanceFactor;
                if (base <= 0) {
                    continue;
                }

                for (int i = 0; i < n; i++) {
                    double fade = fade0 + fadeU * u(bufAX[i], bufAZ[i]) + fadeV * v(bufAX[i], bufAZ[i]);
                    alphas[i] = (float) (base * Math.max(0.0, Math.min(1.0, fade)));
                    heights[i] = hit[0] + hit[2] * (bufAX[i] - middleX) + hit[3] * (bufAZ[i] - middleZ) + LIFT;
                }
                sink.polygon(bufAX, heights, bufAZ, alphas, n);
            }
        }
    }

    /** The rectangle coordinate u of a point on the ground plane. */
    private double u(double x, double z) {
        return ((x - c00x) * eVz - (z - c00z) * eVx) / det;
    }

    /** The rectangle coordinate v of a point on the ground plane. */
    private double v(double x, double z) {
        return (eUx * (z - c00z) - eUz * (x - c00x)) / det;
    }

    /**
     * Cuts the current source polygon to the cell [cellX, cellX + 1] x [cellZ, cellZ + 1]. The result is left
     * in bufA and its vertex count is returned.
     */
    private int clipToCell(int cellX, int cellZ) {
        System.arraycopy(polyX, 0, bufAX, 0, sourceCount);
        System.arraycopy(polyZ, 0, bufAZ, 0, sourceCount);
        int n = sourceCount;
        n = clip(bufAX, bufAZ, n, bufBX, bufBZ, true, cellX, true);
        n = clip(bufBX, bufBZ, n, bufAX, bufAZ, true, cellX + 1, false);
        n = clip(bufAX, bufAZ, n, bufBX, bufBZ, false, cellZ, true);
        n = clip(bufBX, bufBZ, n, bufAX, bufAZ, false, cellZ + 1, false);
        return n;
    }

    /**
     * One Sutherland-Hodgman step: keeps the part of the polygon with coordinate >= bound (keepAbove) or
     * <= bound, on the X axis (alongX) or the Z axis.
     */
    private static int clip(double[] inX, double[] inZ, int n, double[] outX, double[] outZ,
        boolean alongX, double bound, boolean keepAbove) {
        int m = 0;
        for (int i = 0; i < n && m < outX.length - 1; i++) {
            int j = (i + 1) % n;
            double ai = alongX ? inX[i] : inZ[i];
            double aj = alongX ? inX[j] : inZ[j];
            boolean insideI = keepAbove ? ai >= bound : ai <= bound;
            boolean insideJ = keepAbove ? aj >= bound : aj <= bound;

            if (insideI) {
                outX[m] = inX[i];
                outZ[m] = inZ[i];
                m++;
            }
            if (insideI != insideJ) {
                double t = (bound - ai) / (aj - ai);
                outX[m] = inX[i] + t * (inX[j] - inX[i]);
                outZ[m] = inZ[i] + t * (inZ[j] - inZ[i]);
                m++;
            }
        }
        return m;
    }

    private static double area(double[] xs, double[] zs, int n) {
        double twice = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            twice += xs[i] * zs[j] - xs[j] * zs[i];
        }
        return Math.abs(twice) / 2.0;
    }
}
