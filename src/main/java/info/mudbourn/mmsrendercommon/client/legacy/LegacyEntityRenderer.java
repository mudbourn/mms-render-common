package info.mudbourn.mmsrendercommon.client.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Base for an entity renderer written as render(entity, yaw, partialTick, pose, buffers, light); the game's submit calls it. */
public abstract class LegacyEntityRenderer<T extends Entity> extends EntityRenderer<T, LegacyEntityRenderState> {

    protected LegacyEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    // The 1.21 render call; a renderer overrides it, and its super call lands here.
    public void submit(Entity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
    }

    @Override
    public LegacyEntityRenderState createRenderState() {
        return new LegacyEntityRenderState();
    }

    @Override
    public void extractRenderState(T entity, LegacyEntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entity = entity;
        state.partialTick = partialTick;
        state.yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
    }

    @Override
    public void submit(LegacyEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        SubmitBufferSource buffers = new SubmitBufferSource(collector);
        submit(state.entity, state.yaw, state.partialTick, pose, buffers, state.lightCoords);
        buffers.flush();
        super.submit(state, pose, collector, camera);
    }
}
