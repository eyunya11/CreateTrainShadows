package io.github.eyunya11.create_train_shadows.core;

/**
 * Splits a convex polygon into quads, for render types that only draw quads.
 * The polygon is fanned out from its first vertex; when a triangle is left over its last vertex is repeated.
 */
public final class QuadFan {
    private QuadFan() {
    }

    /** How many quads a polygon with {@code n} vertices becomes. */
    public static int quadCount(int n) {
        return n < 3 ? 0 : (n - 1) / 2;
    }

    /**
     * The vertex indices of quad {@code quad} (0-based, below {@link #quadCount}): index {@code corner} in 0..3.
     */
    public static int index(int n, int quad, int corner) {
        int i = 1 + 2 * quad;
        return switch (corner) {
            case 0 -> 0;
            case 1 -> i;
            case 2 -> i + 1;
            default -> Math.min(i + 2, n - 1);
        };
    }
}
