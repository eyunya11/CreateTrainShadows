package io.github.eyunya11.create_train_shadows.core;

/**
 * Where a Create track piece takes a shadow. The rails are the highest part of the track model, about 0.35
 * blocks above the line the track graph runs along, and a shadow lying at the height of the rails covers the
 * sleepers and the bed below them too. On a slope that surface is tilted.
 */
public final class TrackSurface {
    /** Height of the top of the rails above the graph's line of the track, perpendicular to the track, in blocks. */
    public static final double RAIL_TOP = 0.346;

    private TrackSurface() {
    }

    /**
     * The plane of the top of the rails in a block.
     *
     * @param normal         the up direction of the track (any length): (0, 1, 0) on the level, tilted on a slope
     * @param elevation      the height of the track line at the middle of the block above the bottom of the
     *                       block: 0 on the level, 0.5 on a slope
     * @param out            receives [height of the plane above the bottom of the block at its middle,
     *                       height change per block towards +x, height change per block towards +z]
     * @return false when the normal does not point upwards, and there is no such plane
     */
    public static boolean plane(double normalX, double normalY, double normalZ, double elevation, double[] out) {
        double length = Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (length < 1.0e-9) {
            return false;
        }
        double nx = normalX / length, ny = normalY / length, nz = normalZ / length;
        if (ny < 1.0e-3) {
            return false;
        }
        // The plane through (middle of the track line + normal * RAIL_TOP) with this normal
        out[0] = elevation + RAIL_TOP / ny;
        out[1] = -nx / ny;
        out[2] = -nz / ny;
        return true;
    }
}
