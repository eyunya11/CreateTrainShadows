package io.github.eyunya11.create_train_shadows.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ground footprint of a set of blocks: which (x, z) columns are occupied, and how low each one reaches.
 * {@link Builder#build()} merges the columns into as few rectangles as it can, so a boxy carriage costs
 * one or two quads per frame instead of one per column.
 */
public final class Footprint {
    private Footprint() {
    }

    public static final class Builder {
        private final Map<Long, Integer> bottoms = new HashMap<>();

        /** Adds one block at local position (x, y, z). */
        public void add(int x, int y, int z) {
            bottoms.merge(pack(x, z), y, Math::min);
        }

        /**
         * Splits the columns into disjoint rectangles. Columns are only merged when their lowest block is
         * at the same height, so the underside of every rectangle is flat.
         */
        public List<LocalRect> build() {
            Long[] keys = bottoms.keySet().toArray(new Long[0]);
            Arrays.sort(keys, (a, b) -> {
                int byZ = Integer.compare(unpackZ(a), unpackZ(b));
                return byZ != 0 ? byZ : Integer.compare(unpackX(a), unpackX(b));
            });

            Set<Long> used = new HashSet<>();
            List<LocalRect> rects = new ArrayList<>();
            for (long key : keys) {
                if (used.contains(key)) {
                    continue;
                }
                int x0 = unpackX(key);
                int z0 = unpackZ(key);
                int bottom = bottoms.get(key);

                int width = 1;
                while (canTake(x0 + width, z0, bottom, used)) {
                    width++;
                }
                int depth = 1;
                while (rowCanBeTaken(x0, z0 + depth, width, bottom, used)) {
                    depth++;
                }

                for (int dz = 0; dz < depth; dz++) {
                    for (int dx = 0; dx < width; dx++) {
                        used.add(pack(x0 + dx, z0 + dz));
                    }
                }
                rects.add(new LocalRect(x0, z0, width, depth, bottom));
            }
            return rects;
        }

        private boolean rowCanBeTaken(int x0, int z, int width, int bottom, Set<Long> used) {
            for (int dx = 0; dx < width; dx++) {
                if (!canTake(x0 + dx, z, bottom, used)) {
                    return false;
                }
            }
            return true;
        }

        private boolean canTake(int x, int z, int bottom, Set<Long> used) {
            long key = pack(x, z);
            Integer columnBottom = bottoms.get(key);
            return columnBottom != null && columnBottom == bottom && !used.contains(key);
        }
    }

    /**
     * Grows every rectangle by {@code margin} on all sides and returns the union as disjoint rectangles, so
     * that drawing them with transparency never doubles up along a seam. Where grown rectangles overlap, the
     * lower underside wins. With a margin of 0 this only merges rectangles that touch and share an underside.
     */
    public static List<ShadowRect> expand(List<LocalRect> rects, double margin) {
        if (rects.isEmpty()) {
            return List.of();
        }

        // Every edge of every grown rectangle is a grid line; between two lines a cell is either covered or not.
        double[] xs = new double[rects.size() * 2];
        double[] zs = new double[rects.size() * 2];
        for (int i = 0; i < rects.size(); i++) {
            LocalRect r = rects.get(i);
            xs[2 * i] = r.minX() - margin;
            xs[2 * i + 1] = r.maxX() + margin;
            zs[2 * i] = r.minZ() - margin;
            zs[2 * i + 1] = r.maxZ() + margin;
        }
        xs = sortedDistinct(xs);
        zs = sortedDistinct(zs);

        int nx = xs.length - 1;
        int nz = zs.length - 1;
        boolean[] covered = new boolean[nx * nz];
        int[] bottom = new int[nx * nz];
        for (LocalRect r : rects) {
            int x0 = Arrays.binarySearch(xs, r.minX() - margin);
            int x1 = Arrays.binarySearch(xs, r.maxX() + margin);
            int z0 = Arrays.binarySearch(zs, r.minZ() - margin);
            int z1 = Arrays.binarySearch(zs, r.maxZ() + margin);
            for (int ix = x0; ix < x1; ix++) {
                for (int iz = z0; iz < z1; iz++) {
                    int cell = iz * nx + ix;
                    bottom[cell] = covered[cell] ? Math.min(bottom[cell], r.bottomY()) : r.bottomY();
                    covered[cell] = true;
                }
            }
        }

        // Merge cells with the same underside into rectangles, the same way build() merges columns.
        boolean[] used = new boolean[nx * nz];
        List<ShadowRect> merged = new ArrayList<>();
        for (int iz = 0; iz < nz; iz++) {
            for (int ix = 0; ix < nx; ix++) {
                int start = iz * nx + ix;
                if (!covered[start] || used[start]) {
                    continue;
                }
                int level = bottom[start];
                int width = 1;
                while (ix + width < nx && canMerge(covered, used, bottom, iz * nx + ix + width, level)) {
                    width++;
                }
                int depth = 1;
                while (iz + depth < nz && rowCanMerge(covered, used, bottom, nx, ix, iz + depth, width, level)) {
                    depth++;
                }
                for (int dz = 0; dz < depth; dz++) {
                    for (int dx = 0; dx < width; dx++) {
                        used[(iz + dz) * nx + ix + dx] = true;
                    }
                }
                merged.add(new ShadowRect(xs[ix], zs[iz], xs[ix + width], zs[iz + depth], level));
            }
        }
        return merged;
    }

    private static boolean rowCanMerge(boolean[] covered, boolean[] used, int[] bottom, int nx, int ix, int iz,
        int width, int level) {
        for (int dx = 0; dx < width; dx++) {
            if (!canMerge(covered, used, bottom, iz * nx + ix + dx, level)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canMerge(boolean[] covered, boolean[] used, int[] bottom, int cell, int level) {
        return covered[cell] && !used[cell] && bottom[cell] == level;
    }

    private static double[] sortedDistinct(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (i == 0 || sorted[i] != sorted[i - 1]) {
                sorted[n++] = sorted[i];
            }
        }
        return Arrays.copyOf(sorted, n);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}
