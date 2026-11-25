package info.mudbourn.mmsrendercommon.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import info.mudbourn.mmsrendercommon.client.anim.GeoAnimations;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

import java.util.Map;

/** Draws a geo entity's held items and attachments on their bones. */
public class GeoHeldItemLayer extends GeoEntityLayer {

    public GeoHeldItemLayer(RenderLayerParent<GeoEntityRenderState, EntityModel<GeoEntityRenderState>> parent) {
        super(parent);
    }

    @Override
    protected void submitGeo(PoseStack poseStack, SubmitNodeCollector collector, int light,
                             GeoEntityRenderState state) {
        for (Map.Entry<String, GeoHeldItem> entry : state.definition.heldItems().entrySet()) {
            ItemStackRenderState item = state.heldItems.get(entry.getKey());
            Matrix4f frame = state.model.boneFrame(entry.getKey(), state.poses);
            if (item == null || item.isEmpty() || frame == null) {
                continue;
            }
            GeoHeldItem held = entry.getValue();
            poseStack.pushPose();
            poseStack.mulPose(frame);
            poseStack.translate(
                    held.offset().get(0) / 16.0F,
                    held.offset().get(1) / 16.0F,
                    held.offset().get(2) / 16.0F
            );
            poseStack.rotateDegrees(Axis.XP, held.rotation().get(0));
            poseStack.rotateDegrees(Axis.YP, held.rotation().get(1));
            poseStack.rotateDegrees(Axis.ZP, held.rotation().get(2));
            poseStack.scale(held.scale(), held.scale(), held.scale());
            item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
        GeoAnimations.submitAttachments(
                state.definition.attachments(),
                state.model,
                state.poses,
                state.context,
                poseStack,
                collector,
                light,
                LivingEntityRenderer.getOverlayCoords(state, 0.0F)
        );
    }
}
