package info.mudbourn.mmsrendercommon.client.legacy;

import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.Arrays;

/** A vertex consumer that keeps what it is given, to be replayed into the real consumer when the geometry is drawn. */
final class RecordingVertexConsumer implements VertexConsumer {
    private static final int FLOATS = 9;
    private static final int INTS = 5;

    private float[] floats = new float[FLOATS * 64];
    private int[] ints = new int[INTS * 64];
    private int count;

    boolean isEmpty() {
        return count == 0;
    }

    private int last() {
        return count - 1;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (count * FLOATS == floats.length) {
            floats = Arrays.copyOf(floats, floats.length * 2);
            ints = Arrays.copyOf(ints, ints.length * 2);
        }
        int f = count * FLOATS;
        int i = count * INTS;
        floats[f] = x;
        floats[f + 1] = y;
        floats[f + 2] = z;
        floats[f + 3] = 0.0F;
        floats[f + 4] = 0.0F;
        floats[f + 5] = 0.0F;
        floats[f + 6] = 1.0F;
        floats[f + 7] = 0.0F;
        floats[f + 8] = 1.0F;
        ints[i] = -1;
        ints[i + 1] = 0;
        ints[i + 2] = 10;
        ints[i + 3] = 240;
        ints[i + 4] = 240;
        count++;
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        ints[last() * INTS] = (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255;
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        ints[last() * INTS] = argb;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        floats[last() * FLOATS + 3] = u;
        floats[last() * FLOATS + 4] = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        ints[last() * INTS + 1] = u;
        ints[last() * INTS + 2] = v;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        ints[last() * INTS + 3] = u;
        ints[last() * INTS + 4] = v;
        return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        floats[last() * FLOATS + 5] = x;
        floats[last() * FLOATS + 6] = y;
        floats[last() * FLOATS + 7] = z;
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        floats[last() * FLOATS + 8] = width;
        return this;
    }

    void replay(VertexConsumer target) {
        for (int n = 0; n < count; n++) {
            int f = n * FLOATS;
            int i = n * INTS;
            target.addVertex(floats[f], floats[f + 1], floats[f + 2])
                    .setColor(ints[i])
                    .setUv(floats[f + 3], floats[f + 4])
                    .setUv1(ints[i + 1], ints[i + 2])
                    .setUv2(ints[i + 3], ints[i + 4])
                    .setNormal(floats[f + 5], floats[f + 6], floats[f + 7]);
        }
    }
}
