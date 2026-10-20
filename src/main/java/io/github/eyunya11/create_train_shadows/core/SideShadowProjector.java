package io.github.eyunya11.create_train_shadows.core;

import java.util.List;

/**
 * Darkens the sides of walls near a moving body: the vertical faces of blocks next to it (a tunnel wall, a
 * platform edge, a step in the ground). A shadow cast straight down never reaches those, so this is a contact
 * shadow: the darkness of a point on a wall depends on how near it is to the body, not on a light direction.
 *
 * <p>For a point on a wall face, {@code alpha = sideOpacity * light * distanceFactor * near * below * above}:
 * <ul>
 *   <li>{@code near} falls linearly from 1 at the footprint of the body to 0 at {@code sideReach} blocks away,
 *       measured horizontally to the closest point of the footprint;</li>
 *   <li>{@code above} is 1 up to the top of the body and fades out over the next block;</li>
 *   <li>{@code below} is 1 down to the grace height under the body and then fades over the fade height, like the
 *       shadow on the ground does when a body is high above it.</li>
 * </ul>
 * Only faces that look towards the body are drawn, and each is cut into 2 x 2 quads so that the fade shows.
 *
 * <p>One instance owns scratch buffers, so it must not be shared between threads.
 */
public final class SideShadowProjector {
    /** The face of the block on the +X side of an open cell. Likewise for the others. */
    public static final int PLUS_X = 1;
    public static final int MINUS_X = 2;
    public static final int PLUS_Z = 4;
    public static final int MINUS_Z = 8;

    /** Lifted off the wall by the same small distance as the shadow on the ground, against z-fighting. */
    public static final double LIFT = ShadowProjector.LIFT;

    /** Over this many blocks above the top of the body the shadow on a wall fades out. */
    static final double TOP_FADE = 1.0;

    private static final double CELL_SLACK = 0.75; // a bit more than half a cell's diagonal
    private static final double FACING_SLACK = 0.05;
    private static final int SUBDIVISIONS = 2;

    public interface Walls {
        /**
         * What borders the cell (cellX, y, cellZ).
         *
         * @param out receives the brightness of the cell, 0..1, when the result is not 0
         * @return the sides of the cell that are a wall ({@link #PLUS_X} and so on, or'ed together), or 0 if the
         *         cell is itself solid, or no side is a wall, or the cell is too dark to show a shadow
         */
        int walls(int cellX, int y, int cellZ, double[] out);
    }

    public interface Sink {
        /**
         * A quad on a wall, with vertices (xs[i], ys[i], zs[i]) for i in 0..3 and an alpha for each, facing
         * the direction (normalX, 0, normalZ).
         */
        void quad(double[] xs, double[] ys, double[] zs, float[] alphas, int normalX, int normalZ);
    }

    // The footprint rectangles of the current frame
    private double[] c00x = new double[8], c00y = new double[8], c00z = new double[8];
    private double[] eUx = new double[8], eUy = new double[8], eUz = new double[8];
    private double[] eVx = new double[8], eVy = new double[8], eVz = new double[8];
    private double[] det = new double[8];
    private double[] bottomY = new double[8];
    private int count;

    private double topLocalY;
    private double reach;
    private ShadowParams params;
    private double distanceFactor;
    private double brightness;

    // Result of nearest()
    private double nearDistance, nearQx, nearQz, nearUnderside, nearTop;

    private final double[] lightOut = new double[1];
    private final double[] quadX = new double[4];
    private final double[] quadY = new double[4];
    private final double[] quadZ = new double[4];
    private final float[] quadAlpha = new float[4];
    private final double[][] gridAlpha = new double[SUBDIVISIONS + 1][SUBDIVISIONS + 1];

    /**
     * @param rects         the footprint of the body, without margin, in local block coordinates
     * @param topLocalY     the local height of the top of the body
     * @param localToWorld  the body's local-to-world transform for this frame
     * @param distanceFactor {@link ShadowOpacity#distanceFactor} for the camera, multiplied into every alpha
     */
    public void project(List<ShadowRect> rects, double topLocalY, Mat3x4 localToWorld, ShadowParams params,
        double distanceFactor, Walls walls, Sink sink) {
        if (params.sideReach() <= 0 || params.sideOpacity() <= 0 || distanceFactor <= 0 || rects.isEmpty()) {
            return;
        }
        this.topLocalY = topLocalY;
        this.reach = params.sideReach();
        this.params = params;
        this.distanceFactor = distanceFactor;
        if (!loadRects(rects, localToWorld)) {
            return;
        }

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        double lowest = Double.MAX_VALUE, highest = -Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double top = topLocalY - bottomY[i];
            for (int corner = 0; corner < 4; corner++) {
                double u = corner & 1, v = corner >> 1;
                double x = c00x[i] + u * eUx[i] + v * eVx[i];
                double y = c00y[i] + u * eUy[i] + v * eVy[i];
                double z = c00z[i] + u * eUz[i] + v * eVz[i];
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                lowest = Math.min(lowest, y);
                highest = Math.max(highest, y + top);
            }
        }

        // Down to where the fade under the body has reached zero (a pillar's side is shaded all the way)
        int firstRow = (int) Math.floor(lowest) - ShadowOpacity.rowsBelow(params);
        int lastRow = (int) Math.ceil(highest + TOP_FADE) - 1;
        for (int cellX = (int) Math.floor(minX - reach); cellX <= (int) Math.floor(maxX + reach); cellX++) {
            for (int cellZ = (int) Math.floor(minZ - reach); cellZ <= (int) Math.floor(maxZ + reach); cellZ++) {
                nearest(cellX + 0.5, cellZ + 0.5);
                if (nearDistance > reach + CELL_SLACK) {
                    continue;
                }
                for (int row = firstRow; row <= lastRow; row++) {
                    int sides = walls.walls(cellX, row, cellZ, lightOut);
                    if (sides == 0) {
                        continue;
                    }
                    brightness = lightOut[0];
                    if ((sides & PLUS_X) != 0) {
                        face(cellX, row, cellZ, true, cellX + 1 - LIFT, -1, 0, sink);
                    }
                    if ((sides & MINUS_X) != 0) {
                        face(cellX, row, cellZ, true, cellX + LIFT, 1, 0, sink);
                    }
                    if ((sides & PLUS_Z) != 0) {
                        face(cellX, row, cellZ, false, cellZ + 1 - LIFT, 0, -1, sink);
                    }
                    if ((sides & MINUS_Z) != 0) {
                        face(cellX, row, cellZ, false, cellZ + LIFT, 0, 1, sink);
                    }
                }
            }
        }
    }

    private boolean loadRects(List<ShadowRect> rects, Mat3x4 m) {
        if (rects.size() > c00x.length) {
            int n = rects.size();
            c00x = new double[n];
            c00y = new double[n];
            c00z = new double[n];
            eUx = new double[n];
            eUy = new double[n];
            eUz = new double[n];
            eVx = new double[n];
            eVy = new double[n];
            eVz = new double[n];
            det = new double[n];
            bottomY = new double[n];
        }
        count = 0;
        for (ShadowRect r : rects) {
            double y = r.bottomY();
            int i = count;
            c00x[i] = m.x(r.minX(), y, r.minZ());
            c00y[i] = m.y(r.minX(), y, r.minZ());
            c00z[i] = m.z(r.minX(), y, r.minZ());
            eUx[i] = m.x(r.maxX(), y, r.minZ()) - c00x[i];
            eUy[i] = m.y(r.maxX(), y, r.minZ()) - c00y[i];
            eUz[i] = m.z(r.maxX(), y, r.minZ()) - c00z[i];
            eVx[i] = m.x(r.minX(), y, r.maxZ()) - c00x[i];
            eVy[i] = m.y(r.minX(), y, r.maxZ()) - c00y[i];
            eVz[i] = m.z(r.minX(), y, r.maxZ()) - c00z[i];
            det[i] = eUx[i] * eVz[i] - eUz[i] * eVx[i];
            bottomY[i] = y;
            if (Math.abs(det[i]) >= 1.0e-9) {
                count++;
            }
        }
        return count > 0;
    }

    /**
     * Finds the point of the footprint that is closest to (px, pz) on the ground plane. Every rectangle is a
     * rectangle in the plane (the body only turns about the vertical axis and tips about its own axis), so
     * the closest point is found by clamping the point's coordinates in the rectangle.
     */
    private void nearest(double px, double pz) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double dx = px - c00x[i], dz = pz - c00z[i];
            double u = clamp01((dx * eVz[i] - dz * eVx[i]) / det[i]);
            double v = clamp01((eUx[i] * dz - eUz[i] * dx) / det[i]);
            double qx = c00x[i] + u * eUx[i] + v * eVx[i];
            double qz = c00z[i] + u * eUz[i] + v * eVz[i];
            double d2 = (px - qx) * (px - qx) + (pz - qz) * (pz - qz);
            if (d2 < best) {
                best = d2;
                nearQx = qx;
                nearQz = qz;
                nearUnderside = c00y[i] + u * eUy[i] + v * eVy[i];
                nearTop = nearUnderside + (topLocalY - bottomY[i]);
            }
        }
        nearDistance = Math.sqrt(best);
    }

    /** The alpha at a point of a wall, which must be preceded by {@link #nearest} for that point. */
    private float alphaHere(double py) {
        double near = 1.0 - nearDistance / reach;
        if (near <= 0) {
            return 0;
        }
        double drop = nearUnderside - py;
        double below = clamp01(1.0 - Math.max(0.0, drop - params.graceHeight()) / params.fadeHeight());
        double above = clamp01(1.0 - Math.max(0.0, py - nearTop) / TOP_FADE);
        return (float) (params.sideOpacity() * brightness * distanceFactor * near * below * above);
    }

    /**
     * One face of the cell: the plane is {@code x = plane} when {@code alongX}, else {@code z = plane}; the
     * normal points into the cell, towards the body.
     */
    private void face(int cellX, int row, int cellZ, boolean alongX, double plane, int normalX, int normalZ,
        Sink sink) {
        double centreAlong = (alongX ? cellZ : cellX) + 0.5;
        double centreX = alongX ? plane : centreAlong;
        double centreZ = alongX ? centreAlong : plane;
        nearest(centreX, centreZ);
        // The body has to be on the open side of the wall
        if ((nearQx - centreX) * normalX + (nearQz - centreZ) * normalZ < -FACING_SLACK) {
            return;
        }

        boolean any = false;
        for (int j = 0; j <= SUBDIVISIONS; j++) {
            for (int i = 0; i <= SUBDIVISIONS; i++) {
                double along = (alongX ? cellZ : cellX) + (double) i / SUBDIVISIONS;
                nearest(alongX ? plane : along, alongX ? along : plane);
                gridAlpha[j][i] = alphaHere(row + (double) j / SUBDIVISIONS);
                any |= gridAlpha[j][i] > 0;
            }
        }
        if (!any) {
            return;
        }

        for (int j = 0; j < SUBDIVISIONS; j++) {
            for (int i = 0; i < SUBDIVISIONS; i++) {
                if (gridAlpha[j][i] <= 0 && gridAlpha[j][i + 1] <= 0 && gridAlpha[j + 1][i + 1] <= 0
                    && gridAlpha[j + 1][i] <= 0) {
                    continue;
                }
                for (int corner = 0; corner < 4; corner++) {
                    int ci = i + (corner == 1 || corner == 2 ? 1 : 0);
                    int cj = j + (corner >= 2 ? 1 : 0);
                    double along = (alongX ? cellZ : cellX) + (double) ci / SUBDIVISIONS;
                    quadX[corner] = alongX ? plane : along;
                    quadY[corner] = row + (double) cj / SUBDIVISIONS;
                    quadZ[corner] = alongX ? along : plane;
                    quadAlpha[corner] = (float) Math.max(0.0, gridAlpha[cj][ci]);
                }
                sink.quad(quadX, quadY, quadZ, quadAlpha, normalX, normalZ);
            }
        }
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
