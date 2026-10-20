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
