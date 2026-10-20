package io.github.eyunya11.create_train_shadows.core;

import java.util.List;

/**
 * How dark a body's shadow is at any point of the world that lies under it: the same darkness the ground gets,
 * worked out for a point on some other surface, such as a rail. The point is brought into the body's local
 * frame; where the footprint is not, there is no shadow, and where it is, the darkness follows the footprint's
 * fade, the height of the point under the body (measured from the body's lowest part, with the grace height)
 * and the light.
 *
 * <p>Set once per body and frame with {@link #set}, then asked for any number of points.
 */
public final class ShadowField {
    private List<ShadowRect> mesh = List.of();
    private Mat3x4 localToWorld;
    private Mat3x4 worldToLocal;
    private ShadowParams params;
    private double distanceFactor;
    private int lowestBottom;

    // The box around the footprint, in the world
    private double minX, maxX, minY, maxY, minZ, maxZ;

    public void set(List<ShadowRect> mesh, Mat3x4 localToWorld, ShadowParams params, double distanceFactor) {
        this.mesh = mesh;
        this.localToWorld = localToWorld;
        this.worldToLocal = localToWorld.inverse();
        this.params = params;
        this.distanceFactor = distanceFactor;

        lowestBottom = Integer.MAX_VALUE;
        minX = minY = minZ = Double.MAX_VALUE;
        maxX = maxY = maxZ = -Double.MAX_VALUE;
        for (ShadowRect rect : mesh) {
            lowestBottom = Math.min(lowestBottom, rect.bottomY());
            for (int corner = 0; corner < 4; corner++) {
                double lx = (corner & 1) == 0 ? rect.minX() : rect.maxX();
                double lz = (corner & 2) == 0 ? rect.minZ() : rect.maxZ();
                double x = localToWorld.x(lx, rect.bottomY(), lz);
                double y = localToWorld.y(lx, rect.bottomY(), lz);
                double z = localToWorld.z(lx, rect.bottomY(), lz);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
    }

    /** The box around the footprint in the world (its undersides): lowest and highest corner. */
    public double minX() {
        return minX;
    }

    public double maxX() {
        return maxX;
    }

    public double minY() {
        return minY;
    }

    public double maxY() {
        return maxY;
    }

    public double minZ() {
        return minZ;
    }

    public double maxZ() {
        return maxZ;
    }

    /**
     * A quick test that rules out points far from the footprint: whether the point is within {@code slack} blocks
     * of the box around it, sideways, and not above it or further under it than any shadow can reach.
     */
    public boolean isNear(double x, double y, double z, double slack) {
        return !mesh.isEmpty() && x >= minX - slack && x <= maxX + slack && z >= minZ - slack && z <= maxZ + slack
            && y <= maxY + 1.0 && y >= minY - ShadowOpacity.MAX_DEPTH;
    }

    /**
     * The alpha of the shadow at a point.
     *
     * @param brightness the light at the point, 0..1 ({@link ShadowOpacity#MIN_LIGHT} is the caller's concern)
     * @return alpha, 0 where there is no shadow
     */
    public double alphaAt(double x, double y, double z, double brightness) {
        double localX = worldToLocal.x(x, y, z);
        double localZ = worldToLocal.z(x, y, z);
        for (ShadowRect rect : mesh) {
            double fade = rect.fadeAt(localX, localZ);
            if (Double.isNaN(fade)) {
                continue;
            }
            double underside = localToWorld.y(localX, rect.bottomY(), localZ);
            double height = underside - y - (rect.bottomY() - lowestBottom);
            double alpha = ShadowOpacity.alpha(params, height, brightness) * distanceFactor * Math.max(0.0, fade);
            return Math.max(0.0, alpha);
        }
        return 0;
    }
}
