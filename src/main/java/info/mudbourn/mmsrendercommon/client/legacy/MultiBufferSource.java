package info.mudbourn.mmsrendercommon.client.legacy;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/** The 1.21 buffer source handed to renderers: a vertex consumer per render type. */
public interface MultiBufferSource {

    VertexConsumer getBuffer(RenderType type);
}
