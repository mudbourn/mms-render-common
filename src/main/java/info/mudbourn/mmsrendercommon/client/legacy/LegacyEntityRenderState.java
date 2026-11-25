package info.mudbourn.mmsrendercommon.client.legacy;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/** The render state of a renderer written for 1.21: it keeps the entity and the values the old render call took. */
public class LegacyEntityRenderState extends EntityRenderState {
    public Entity entity;
    public float partialTick;
    public float yaw;
}
