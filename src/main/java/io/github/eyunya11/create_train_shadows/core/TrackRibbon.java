package io.github.eyunya11.create_train_shadows.core;

/**
 * A shadow on a curved stretch of track. A curve is drawn from the track's block entity across cells that hold
 * no block, so there is nothing for {@link ShadowProjector} to land on; instead the shadow is laid along the
 * curve itself, as a strip at the height of the rails.
 *
 * <p>The strip follows samples of the curve (a position and the up direction of the track at each) and is
 * {@code 2 * halfWidth} wide, cut into {@code lateralSteps} bands. The caller says how dark the shadow is at any
 * point of space, and quads with a darkness for each vertex are handed to the same {@link ShadowProjector.Sink}.
 */
public final class TrackRibbon {
    public interface Surface {
        /** The alpha of the shadow at a point in the world; zero or less where there is none. */
        double alphaAt(double x, double y, double z);
    }

    private final double[] xs = new double[4];
    private final double[] ys = new double[4];
    private final double[] zs = new double[4];
    private final float[] alphas = new float[4];

    private double[] rowX = new double[0], rowY = new double[0], rowZ = new double[0];
    private double[] rowAlpha = new double[0];

    /**
     * @param count        how many samples there are (the arrays may be longer)
     * @param lateralSteps how many bands the width is cut into (at least 1)
     * @param lift         how far the strip is lifted off the top of the rails
     */
    public void build(double[] px, double[] py, double[] pz, double[] nx, double[] ny, double[] nz, int count,
        double halfWidth, int lateralSteps, double lift, Surface surface, ShadowProjector.Sink sink) {
        if (count < 2 || lateralSteps < 1) {
            return;
        }
        int columns = lateralSteps + 1;
        ensureRows(columns * count);

        for (int k = 0; k < count; k++) {
            int before = Math.max(0, k - 1), after = Math.min(count - 1, k + 1);
            double tx = px[after] - px[before], ty = py[after] - py[before], tz = pz[after] - pz[before];
            double normalLength = Math.sqrt(nx[k] * nx[k] + ny[k] * ny[k] + nz[k] * nz[k]);
            if (normalLength < 1.0e-9) {
                normalLength = 1;
            }
            double upX = nx[k] / normalLength, upY = ny[k] / normalLength, upZ = nz[k] / normalLength;

            // across the track: the tangent crossed with the up direction
            double sideX = ty * upZ - tz * upY;
            double sideY = tz * upX - tx * upZ;
            double sideZ = tx * upY - ty * upX;
            double sideLength = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
            if (sideLength < 1.0e-9) {
                sideLength = 1;
            }
            sideX /= sideLength;
            sideY /= sideLength;
            sideZ /= sideLength;

            double up = TrackSurface.RAIL_TOP + lift;
            for (int j = 0; j < columns; j++) {
                double across = -halfWidth + 2 * halfWidth * j / lateralSteps;
                int i = k * columns + j;
                rowX[i] = px[k] + upX * up + sideX * across;
                rowY[i] = py[k] + upY * up + sideY * across;
                rowZ[i] = pz[k] + upZ * up + sideZ * across;
                rowAlpha[i] = surface.alphaAt(rowX[i], rowY[i], rowZ[i]);
            }
        }

        for (int k = 0; k + 1 < count; k++) {
            for (int j = 0; j < lateralSteps; j++) {
                int a = k * columns + j, b = k * columns + j + 1, c = (k + 1) * columns + j + 1,
                    d = (k + 1) * columns + j;
                if (rowAlpha[a] <= 0 && rowAlpha[b] <= 0 && rowAlpha[c] <= 0 && rowAlpha[d] <= 0) {
                    continue;
                }
                put(0, a);
                put(1, b);
                put(2, c);
                put(3, d);
                sink.polygon(xs, ys, zs, alphas, 4);
            }
        }
    }

    private void put(int corner, int row) {
        xs[corner] = rowX[row];
        ys[corner] = rowY[row];
        zs[corner] = rowZ[row];
        alphas[corner] = (float) Math.max(0.0, Math.min(1.0, rowAlpha[row]));
    }

    private void ensureRows(int n) {
        if (rowX.length < n) {
            rowX = new double[n];
            rowY = new double[n];
            rowZ = new double[n];
            rowAlpha = new double[n];
        }
    }
}
