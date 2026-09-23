package info.mudbourn.mmsrendercommon.mixin.geoholder;

import info.mudbourn.mmsrendercommon.duck.GeoHolderDuck;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stamps {@link GeoHolderDuck} on every extraction, since render states are pooled. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"))
    private void mmsRenderCommon$stampGeoHolder(LivingEntity entity,
                                                LivingEntityRenderState state,
                                                float partialTick,
                                                CallbackInfo ci) {
        ((GeoHolderDuck) state).mmsRenderCommon$setGeoHolderId(entity.getId());
    }
}
