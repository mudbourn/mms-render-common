package info.mudbourn.mmsrendercommon.mixin.geoarmor;

import com.mojang.blaze3d.vertex.PoseStack;
import info.mudbourn.mmsrendercommon.client.armor.GeoArmor;
import info.mudbourn.mmsrendercommon.duck.FirstPersonSelfDuck;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws armor whose equipment asset names a geo armor definition, in place of the vanilla layers. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @SuppressWarnings("unchecked")
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void mms$renderGeoArmor(PoseStack poseStack,
                                    SubmitNodeCollector collector,
                                    ItemStack stack,
                                    EquipmentSlot slot,
                                    int light,
                                    HumanoidRenderState state,
                                    CallbackInfo ci) {
        if (!GeoArmor.drawsByAsset(stack, slot)) {
            return;
        }
        Object parent = ((RenderLayer<?, ?>) (Object) this).getParentModel();
        if (!FirstPersonSelfDuck.hidesHeadPiece(state, slot) && parent instanceof HumanoidModel<?> model) {
            GeoArmor.renderByAsset(
                    poseStack,
                    collector,
                    stack,
                    state,
                    slot,
                    light,
                    (HumanoidModel<HumanoidRenderState>) model
            );
        }
        ci.cancel();
    }
}
