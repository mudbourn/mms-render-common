package info.mudbourn.mmsrendercommon.client.legacy;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;

/** The living-entity form of {@link LegacyEntityRenderState}. */
public class LegacyLivingRenderState extends LivingEntityRenderState {
    public Entity entity;
    public float partialTick;
    public float yaw;
}
