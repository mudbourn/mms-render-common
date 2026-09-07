package info.mudbourn.mmsrendercommon.mixin.headhide;

import info.mudbourn.mmsrendercommon.duck.FirstPersonSelfDuck;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Storage for {@link FirstPersonSelfDuck} on every living render state.
 *
 * <p>Render states are pooled and reused across entities, so the flag is
 * rewritten on every extraction rather than left to decay — see
 * {@link LivingEntityRendererMixin}, which sets it unconditionally.
 */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements FirstPersonSelfDuck {

    @Unique
    private boolean mmsCompat$firstPersonSelf;

    @Override
    public boolean mmsCompat$isFirstPersonSelf() {
        return this.mmsCompat$firstPersonSelf;
    }

    @Override
    public void mmsCompat$setFirstPersonSelf(boolean firstPersonSelf) {
        this.mmsCompat$firstPersonSelf = firstPersonSelf;
    }
}
