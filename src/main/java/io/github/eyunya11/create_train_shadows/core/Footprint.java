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
 * one or two quads per frame instead of one per column. {@link #expand} grows that into the shadow's outline
 * and {@link #soft} fades its edge.
 */
public final class Footprint {
    /** Past this many cells a feathered outline is not worth its cost and the edge stays hard. */
    private static final int MAX_SOFT_CELLS = 4096;

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

    /** A grid whose lines are the edges of the grown rectangles; each cell is covered or not. */
    private static final class Grid {
        final double[] xs;
        final double[] zs;
        final int nx;
        final int nz;
        final boolean[] covered;
        final int[] bottom;

        Grid(List<LocalRect> rects, double margin) {
            double[] lineX = new double[rects.size() * 2];
            double[] lineZ = new double[rects.size() * 2];
            for (int i = 0; i < rects.size(); i++) {
                LocalRect r = rects.get(i);
                lineX[2 * i] = r.minX() - margin;
                lineX[2 * i + 1] = r.maxX() + margin;
                lineZ[2 * i] = r.minZ() - margin;
                lineZ[2 * i + 1] = r.maxZ() + margin;
            }
            xs = sortedDistinct(lineX);
            zs = sortedDistinct(lineZ);
            nx = xs.length - 1;
            nz = zs.length - 1;
            covered = new boolean[nx * nz];
            bottom = new int[nx * nz];
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
        }

        boolean isCovered(int ix, int iz) {
            return ix >= 0 && ix < nx && iz >= 0 && iz < nz && covered[iz * nx + ix];
        }

        /** Whether grid line {@code line} along X separates a covered cell from an uncovered one or the outside. */
        boolean isBoundaryX(int line) {
            for (int iz = 0; iz < nz; iz++) {
                if (isCovered(line - 1, iz) != isCovered(line, iz)) {
                    return true;
                }
            }
            return false;
        }

        boolean isBoundaryZ(int line) {
            for (int ix = 0; ix < nx; ix++) {
                if (isCovered(ix, line - 1) != isCovered(ix, line)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Distance from a point inside the grown outline to the nearest point outside it, measured as the
         * largest of the distances along X and Z (so a rectangle's edge fades in straight bands with mitred
         * corners).
         */
        double distanceToOutside(double px, double pz) {
            double d = Math.min(Math.min(px - xs[0], xs[nx] - px), Math.min(pz - zs[0], zs[nz] - pz));
            for (int iz = 0; iz < nz; iz++) {
                for (int ix = 0; ix < nx; ix++) {
                    if (covered[iz * nx + ix]) {
                        continue;
                    }
                    double gapX = Math.max(0.0, Math.max(xs[ix] - px, px - xs[ix + 1]));
                    double gapZ = Math.max(0.0, Math.max(zs[iz] - pz, pz - zs[iz + 1]));
                    d = Math.min(d, Math.max(gapX, gapZ));
                }
            }
            return Math.max(0.0, d);
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
        Grid grid = new Grid(rects, margin);
        List<ShadowRect> merged = new ArrayList<>();
        mergeUniformCells(grid.xs, grid.zs, grid.covered.clone(), grid.bottom, merged);
        return merged;
    }

    /**
     * Like {@link #expand}, but the outer edge of the shadow fades out: darkness rises from nothing at the
     * outline to full {@code softness} blocks inside it, and stays full inside that. Edges between rectangles
     * that are part of the same shadow do not fade. With a softness of 0 this is {@link #expand}.
     */
    public static List<ShadowRect> soft(List<LocalRect> rects, double margin, double softness) {
        if (rects.isEmpty()) {
            return List.of();
        }
        if (softness <= 0) {
            return expand(rects, margin);
        }
        Grid grid = new Grid(rects, margin);

        // Refine the grid so that darkness changes only along grid lines: next to every outline edge, a line
        // `softness` inside it.
        // Where the fades from two sides meet in a narrow part, the darkest line is in the middle: add it.
        List<Double> lineX = new ArrayList<>();
        for (int i = 0; i <= grid.nx; i++) {
            lineX.add(grid.xs[i]);
            if (grid.isBoundaryX(i)) {
                lineX.add(Math.max(grid.xs[0], grid.xs[i] - softness));
                lineX.add(Math.min(grid.xs[grid.nx], grid.xs[i] + softness));
            }
            if (i < grid.nx && grid.xs[i + 1] - grid.xs[i] < 2 * softness) {
                lineX.add((grid.xs[i] + grid.xs[i + 1]) / 2);
            }
        }
        List<Double> lineZ = new ArrayList<>();
        for (int i = 0; i <= grid.nz; i++) {
            lineZ.add(grid.zs[i]);
            if (grid.isBoundaryZ(i)) {
                lineZ.add(Math.max(grid.zs[0], grid.zs[i] - softness));
                lineZ.add(Math.min(grid.zs[grid.nz], grid.zs[i] + softness));
            }
            if (i < grid.nz && grid.zs[i + 1] - grid.zs[i] < 2 * softness) {
                lineZ.add((grid.zs[i] + grid.zs[i + 1]) / 2);
            }
        }
        double[] xs = sortedDistinct(lineX.stream().mapToDouble(Double::doubleValue).toArray());
        double[] zs = sortedDistinct(lineZ.stream().mapToDouble(Double::doubleValue).toArray());
        int nx = xs.length - 1;
        int nz = zs.length - 1;
        if ((long) nx * nz > MAX_SOFT_CELLS) {
            return expand(rects, margin);
        }

        double[][] node = new double[nz + 1][nx + 1];
        for (int j = 0; j <= nz; j++) {
            for (int i = 0; i <= nx; i++) {
                node[j][i] = fadeAt(grid, xs[i], zs[j], softness);
            }
        }

        List<ShadowRect> result = new ArrayList<>();
        boolean[] uniform = new boolean[nx * nz];
        int[] bottom = new int[nx * nz];
        for (int iz = 0; iz < nz; iz++) {
            for (int ix = 0; ix < nx; ix++) {
                int original = floorIndex(grid.zs, (zs[iz] + zs[iz + 1]) / 2) * grid.nx
                    + floorIndex(grid.xs, (xs[ix] + xs[ix + 1]) / 2);
                if (!grid.covered[original]) {
                    continue;
                }
                double a00 = node[iz][ix];
                double a10 = node[iz][ix + 1];
                double a01 = node[iz + 1][ix];
                double a11 = node[iz + 1][ix + 1];
                int cell = iz * nx + ix;
                bottom[cell] = grid.bottom[original];
                if (a00 == 1.0 && a10 == 1.0 && a01 == 1.0 && a11 == 1.0) {
                    uniform[cell] = true;
                } else {
                    // Split along the diagonal whose end points give the better value in the middle of the cell:
                    // the ridge of a convex corner runs along the diagonal of its brighter corners, but a
                    // fade around a reflex corner dips along the one with the dark corner.
                    double middle = fadeAt(grid, (xs[ix] + xs[ix + 1]) / 2, (zs[iz] + zs[iz + 1]) / 2, softness);
                    boolean minToMax = Math.abs((a00 + a11) / 2 - middle) <= Math.abs((a10 + a01) / 2 - middle);
                    result.add(new ShadowRect(xs[ix], zs[iz], xs[ix + 1], zs[iz + 1], bottom[cell],
                        a00, a10, a01, a11, minToMax));
                }
            }
        }
        mergeUniformCells(xs, zs, uniform, bottom, result);
        return result;
    }

    /** The darkness factor at a point: 0 on the outline, rising linearly to 1 at {@code softness} inside it. */
    private static double fadeAt(Grid grid, double x, double z, double softness) {
        double d = grid.distanceToOutside(x, z);
        return d >= softness - 1e-9 ? 1.0 : d / softness;
    }

    /** Merges the marked cells of a grid into rectangles of equal underside, greedily, like {@link Builder#build}. */
    private static void mergeUniformCells(double[] xs, double[] zs, boolean[] marked, int[] bottom,
        List<ShadowRect> out) {
        int nx = xs.length - 1;
        int nz = zs.length - 1;
        boolean[] used = new boolean[nx * nz];
        for (int iz = 0; iz < nz; iz++) {
            for (int ix = 0; ix < nx; ix++) {
                int start = iz * nx + ix;
                if (!marked[start] || used[start]) {
                    continue;
                }
                int level = bottom[start];
                int width = 1;
                while (ix + width < nx && canMerge(marked, used, bottom, iz * nx + ix + width, level)) {
                    width++;
                }
                int depth = 1;
                while (iz + depth < nz && rowCanMerge(marked, used, bottom, nx, ix, iz + depth, width, level)) {
                    depth++;
                }
                for (int dz = 0; dz < depth; dz++) {
                    for (int dx = 0; dx < width; dx++) {
                        used[(iz + dz) * nx + ix + dx] = true;
                    }
                }
                out.add(new ShadowRect(xs[ix], zs[iz], xs[ix + width], zs[iz + depth], level));
            }
        }
    }

    private static boolean rowCanMerge(boolean[] marked, boolean[] used, int[] bottom, int nx, int ix, int iz,
        int width, int level) {
        for (int dx = 0; dx < width; dx++) {
            if (!canMerge(marked, used, bottom, iz * nx + ix + dx, level)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canMerge(boolean[] marked, boolean[] used, int[] bottom, int cell, int level) {
        return marked[cell] && !used[cell] && bottom[cell] == level;
    }

    /** The index of the grid cell that contains {@code value}. */
    private static int floorIndex(double[] lines, double value) {
        int i = Arrays.binarySearch(lines, value);
        if (i < 0) {
            i = -i - 2;
        }
        return Math.max(0, Math.min(lines.length - 2, i));
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
