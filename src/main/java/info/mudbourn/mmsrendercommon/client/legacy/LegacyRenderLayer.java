package info.mudbourn.mmsrendercommon.client.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;

/** Base for a render layer written as render(pose, buffers, light, entity, limbSwing, limbSwingAmount, partialTick, age, headYaw, headPitch). */
public abstract class LegacyRenderLayer<S extends EntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {

    protected LegacyRenderLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    // The 1.21 render call; a layer overrides it.
    public void submit(PoseStack pose, MultiBufferSource buffers, int light, Entity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float headYaw, float headPitch) {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        Entity entity = entityOf(state);
        if (entity == null || !(state instanceof LivingEntityRenderState living)) {
            return;
        }
        ((EntityModel) getParentModel()).setupAnim(state);
        SubmitBufferSource buffers = new SubmitBufferSource(collector);
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        try {
            submit(pose, buffers, light, entity, living.walkAnimationPos, living.walkAnimationSpeed, partialTick, living.ageInTicks, yRot, xRot);
        } catch (ClassCastException e) {
            // a layer written for players, on a renderer that also draws another avatar entity
            return;
        }
        buffers.flush();
    }

    private static Entity entityOf(EntityRenderState state) {
        if (state instanceof LegacyLivingRenderState legacy) {
            return legacy.entity;
        }
        if (state instanceof AvatarRenderState avatar && Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.getEntity(avatar.id);
        }
        return null;
    }
}
