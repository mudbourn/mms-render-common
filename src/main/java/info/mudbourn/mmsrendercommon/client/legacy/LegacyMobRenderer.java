package info.mudbourn.mmsrendercommon.client.legacy;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Base for a mob renderer written for 1.21: a model, a shadow size and a texture per entity. */
public abstract class LegacyMobRenderer<T extends Mob> extends MobRenderer<T, LegacyLivingRenderState, EntityModel<LegacyLivingRenderState>> {

    protected LegacyMobRenderer(EntityRendererProvider.Context context, EntityModel<LegacyLivingRenderState> model, float shadowRadius) {
        super(context, model, shadowRadius);
        if (model instanceof HierarchicalModel<?> hierarchical) {
            hierarchical.bind();
        }
    }

    // The 1.21 texture call; a renderer overrides it.
    public Identifier getTextureLocation(Entity entity) {
        return null;
    }

    @Override
    public Identifier getTextureLocation(LegacyLivingRenderState state) {
        return getTextureLocation(state.entity);
    }

    @Override
    public LegacyLivingRenderState createRenderState() {
        return new LegacyLivingRenderState();
    }

    @Override
    public void extractRenderState(T entity, LegacyLivingRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entity = entity;
        state.partialTick = partialTick;
        state.yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
    }
}
