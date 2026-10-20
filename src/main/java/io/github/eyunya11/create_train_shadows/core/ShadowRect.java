package io.github.eyunya11.create_train_shadows.core;

/**
 * A rectangle of the shadow in contraption-local coordinates: {@code [minX, maxX] x [minZ, maxZ]}, with its
 * underside at local height {@code bottomY}. Unlike {@link LocalRect} the bounds need not be whole blocks.
 */
public record ShadowRect(double minX, double minZ, double maxX, double maxZ, int bottomY) {
    public double area() {
        return (maxX - minX) * (maxZ - minZ);
    }
}
