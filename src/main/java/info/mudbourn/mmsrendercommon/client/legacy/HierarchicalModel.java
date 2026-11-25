package info.mudbourn.mmsrendercommon.client.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import info.mudbourn.mmsrendercommon.mixin.legacy.ModelAccessor;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** The 1.21 model with a root part and keyframe animation helpers, on 26.3's model. */
public abstract class HierarchicalModel<T extends Entity> extends EntityModel<LegacyLivingRenderState> {
    private final Map<AnimationDefinition, KeyframeAnimation> baked = new IdentityHashMap<>();

    protected HierarchicalModel() {
        super(new ModelPart(List.of(), Map.of()), RenderTypes::entityCutout);
    }

    // The 1.21 root accessor, renamed by the uproot rules because 26.3's root() is final.
    public abstract ModelPart legacyRoot();

    // The 1.21 per-entity pose call; a model overrides it.
    public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    // The 1.21 draw call, renamed by the uproot rules because 26.3's is final; the root part is drawn by the game.
    public void legacyRenderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
    }

    // Points the game's model at the root the subclass built, which it only knows after construction.
    void bind() {
        ModelPart root = legacyRoot();
        ModelAccessor accessor = (ModelAccessor) this;
        accessor.mms$setRoot(root);
        accessor.mms$setAllParts(root.getAllParts());
    }

    @Override
    public void setupAnim(LegacyLivingRenderState state) {
        super.setupAnim(state);
        legacyRoot().resetPose();
        setupAnim(state.entity, state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
    }

    protected void animate(AnimationState state, AnimationDefinition definition, float ageInTicks) {
        animate(state, definition, ageInTicks, 1.0F);
    }

    protected void animate(AnimationState state, AnimationDefinition definition, float ageInTicks, float speed) {
        baked(definition).apply(state, ageInTicks, speed);
    }

    protected void animateWalk(AnimationDefinition definition, float limbSwing, float limbSwingAmount, float maxSpeed, float scale) {
        baked(definition).applyWalk(limbSwing, limbSwingAmount, maxSpeed, scale);
    }

    protected void applyStatic(AnimationDefinition definition) {
        baked(definition).applyStatic();
    }

    private KeyframeAnimation baked(AnimationDefinition definition) {
        return baked.computeIfAbsent(definition, key -> key.bake(legacyRoot()));
    }
}
