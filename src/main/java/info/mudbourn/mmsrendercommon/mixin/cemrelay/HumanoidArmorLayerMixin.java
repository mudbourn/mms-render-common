package info.mudbourn.mmsrendercommon.mixin.cemrelay;

import info.mudbourn.mmsrendercommon.client.CemLayerPoseRelay;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Poses worn armour from the CEM-animated body underneath it.
 *
 * <p>EMF ships its own version of this — {@code EMFBipedPose}, captured from the
 * base model after it animates and re-applied to the armour model — but it gives
 * up the moment the armour model has an animation of its own:
 *
 * <pre>  !(model.root() instanceof EMFModelPartRoot root &amp;&amp; root.hasAnimation())</pre>
 *
 * <p>DetailedAnimations' four player armour jems all declare one, and it is not
 * decorative: it is the block that copies the player root's swim/crawl rotation
 * onto the armour, which EMF's snapshot does not deliver. Stripping it to buy the
 * biped relay is the trade this class exists to avoid having to make — the jems
 * keep their root animation, and the limb poses EMF would have relayed come from
 * here instead.
 *
 * <p>Injected at {@code HEAD} of {@code submit}, i.e. during node collection,
 * after {@code LivingEntityRenderer} has animated the base model and while the
 * armour models still hold their rest pose. Nothing between here and the deferred
 * render touches the mapped parts: EMF's own relay is gated off by the jem's root
 * animation, {@code root.animate()} at render time evaluates that root-only
 * animation and writes nothing but {@code root.*}, and
 * {@code resetVanillaPartsToDefaults} — the one thing that would wipe this — is
 * held off by {@code resetPlayerModelEachRender_v2: false} in the pack's EMF
 * config. That config value is load-bearing; flipping it on un-poses the armour.
 *
 * <p>All four slot models are relayed rather than just the one about to be drawn,
 * because the slot is resolved further down in {@code renderArmorPiece}. The base
 * tree is walked once for the whole set, and a set member the relay cannot match
 * is left stock.
 *
 * <p>The relay accumulates from the root's children down and never folds the root
 * in, so it cannot compound with the root transform the jem applies itself. On any
 * entity whose base model is not an animated CEM model — no EMF, no jem — every
 * call here is a no-op and EMF's own path, or plain vanilla, is left to it.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @Shadow @Final private ArmorModelSet<?> modelSet;

    @Shadow @Final private ArmorModelSet<?> babyModelSet;

    /**
     * The wearer's own model, reached by cast rather than {@code @Shadow}.
     *
     * <p>{@code getParentModel()} is declared on {@link RenderLayer}, the
     * superclass, not on the layer this mixin targets. Mixin resolves
     * {@code @Shadow} members against the target class itself, so shadowing it
     * threw {@code InvalidMixinException} at apply time and silently disabled
     * this entire mixin. It is {@code public} on {@code RenderLayer}, so a cast
     * reaches it with no shadow at all.
     */
    @Unique
    private EntityModel<?> mms$parentModel() {
        return ((RenderLayer<?, ?>) (Object) this).getParentModel();
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void mms$relayArmorPose(CallbackInfo ci) {
        EntityModel<?> parent = this.mms$parentModel();
        mms$relaySet(parent, this.modelSet);
        mms$relaySet(parent, this.babyModelSet);
    }

    private static void mms$relaySet(EntityModel<?> parent, ArmorModelSet<?> set) {
        if (set == null) {
            return;
        }
        CemLayerPoseRelay.relay(parent, CemLayerPoseRelay.HUMANOID_ARMOR,
                mms$asModel(set.head()), mms$asModel(set.chest()),
                mms$asModel(set.legs()), mms$asModel(set.feet()));
    }

    private static Model mms$asModel(Object model) {
        return model instanceof Model m ? m : null;
    }
}
