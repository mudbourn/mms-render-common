package info.mudbourn.mmsrendercommon.client.legacy;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Passes vertices on with the texture coordinates multiplied, to tile a texture over a model. */
public final class UvScalingVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float scale;

    public UvScalingVertexConsumer(VertexConsumer delegate, float scale) {
        this.delegate = delegate;
        this.scale = scale;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        delegate.setColor(argb);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u * scale, v * scale);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        delegate.setUv3(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }
}
