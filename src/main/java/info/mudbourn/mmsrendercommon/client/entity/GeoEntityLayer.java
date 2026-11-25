package info.mudbourn.mmsrendercommon.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * A render layer of a {@link GeoEntityRenderer}, drawn in the space the geo draws in: block
 * units, Y up, origin at the entity's feet, turned with its body.
 */
public abstract class GeoEntityLayer extends RenderLayer<GeoEntityRenderState, EntityModel<GeoEntityRenderState>> {

    protected GeoEntityLayer(RenderLayerParent<GeoEntityRenderState, EntityModel<GeoEntityRenderState>> parent) {
        super(parent);
    }

    @Override
    public final void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
                             GeoEntityRenderState state, float yRot, float xRot) {
        if (state.definition == null || state.model == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, 1.501F, 0.0F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        submitGeo(poseStack, collector, light, state);
        poseStack.popPose();
    }

    /** Submits this layer for a state whose definition and model are present. */
    protected abstract void submitGeo(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                      GeoEntityRenderState state);
}
