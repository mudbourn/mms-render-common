package info.mudbourn.mmsrendercommon.mixin.headhide;

import info.mudbourn.mmsrendercommon.duck.FirstPersonSelfDuck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stamps {@link FirstPersonSelfDuck} while the entity is still in hand.
 *
 * <p>Injected on the base-class {@code extractRenderState}, which every
 * subclass reaches through {@code super}, so the avatar renderer and any
 * modded living renderer are all covered by this one seam.
 *
 * <p>{@code @At("RETURN")} rather than {@code TAIL}: the flag has to be
 * written on every exit path, and TAIL only takes the last return — the
 * distinction that caused the stuck-slow-train bug.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"))
    private void mmsCompat$markFirstPersonSelf(LivingEntity entity,
                                               LivingEntityRenderState state,
                                               float partialTick,
                                               CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean self = minecraft.getCameraEntity() == entity
            && minecraft.options.getCameraType().isFirstPerson();
        ((FirstPersonSelfDuck) state).mmsCompat$setFirstPersonSelf(self);
    }
}
