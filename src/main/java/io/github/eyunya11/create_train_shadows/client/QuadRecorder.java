package io.github.eyunya11.create_train_shadows.client;

import java.util.Arrays;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * A vertex consumer that only remembers where the vertices are. Model code (a block model, Create's curved
 * track) draws into it exactly as it would into a real buffer; what comes out is the shape of the model, in
 * quads, which can then be drawn again as often as needed with other colours.
 */
final class QuadRecorder implements VertexConsumer {
    private float[] positions = new float[3 * 4 * 64];
    private int size;

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (size + 3 > positions.length) {
            positions = Arrays.copyOf(positions, positions.length * 2);
        }
        positions[size++] = x;
        positions[size++] = y;
        positions[size++] = z;
        return this;
    }

    // Everything else about a vertex is of no interest here.

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return this;
    }

    /** The recorded positions, x y z for each vertex; only whole quads are kept. */
    float[] toArray() {
        int wholeQuads = size / 12;
        return Arrays.copyOf(positions, wholeQuads * 12);
    }
}
