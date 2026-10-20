package io.github.eyunya11.create_train_shadows.core;

/**
 * An axis-aligned rectangle of block columns of a contraption, in contraption-local block coordinates.
 * It covers {@code [minX, minX + sizeX] x [minZ, minZ + sizeZ]} and its underside is at local height
 * {@code bottomY} (the lower face of the lowest block in these columns).
 */
public record LocalRect(int minX, int minZ, int sizeX, int sizeZ, int bottomY) {
    public int maxX() {
        return minX + sizeX;
    }

    public int maxZ() {
        return minZ + sizeZ;
    }
}
