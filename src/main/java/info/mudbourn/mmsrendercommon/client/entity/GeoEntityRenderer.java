package info.mudbourn.mmsrendercommon.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import info.mudbourn.mmsrendercommon.client.anim.GeoAnimations;
import info.mudbourn.mmsrendercommon.client.anim.GeoContext;
import info.mudbourn.mmsrendercommon.client.anim.GeoDriver;
import info.mudbourn.mmsrendercommon.client.anim.GeoQueries;
import info.mudbourn.mmsrendercommon.client.geo.BonePose;
import info.mudbourn.mmsrendercommon.client.geo.GeoAssets;
import info.mudbourn.mmsrendercommon.client.geo.MolangScope;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a living entity as a Bedrock geo model, from a {@link GeoEntityDefinition}.
 *
 * <p>Built on {@link LivingEntityRenderer}, so the entity turns, dies, flashes red when hurt and
 * shows its name as a vanilla mob does. The model itself is drawn by {@link GeoEntityLayer}s:
 * the texture, the emissive texture and the held items and attachments. A subclass adds its own
 * with {@link #addLayer}. Poses, scale and the shadow follow the definition, which is re-read
 * after every resource reload. {@code GeoTriggers.trigger(entity, animation)} plays a one-off
 * animation over the base one.
 */
public class GeoEntityRenderer<T extends LivingEntity>
        extends LivingEntityRenderer<T, GeoEntityRenderState, EntityModel<GeoEntityRenderState>> {

    private static final float WALK_THRESHOLD = 0.15F;

    private final Identifier definition;
    private float flipDegrees = 90.0F;

    /**
     * A renderer for the definition at {@code assets/<ns>/geo_entity/<path>.json}.
     *
     * @param definition the definition id, {@code <ns>:<path>}
     */
    public GeoEntityRenderer(EntityRendererProvider.Context context, Identifier definition) {
        super(context, new EntityModel<>(new ModelPart(List.of(), Map.of())) {}, 0.5F);
        this.definition = definition.withPath(path -> "geo_entity/" + path + ".json");
        addLayer(GeoTextureLayer.base(this));
        addLayer(GeoTextureLayer.glow(this));
        addLayer(new GeoHeldItemLayer(this));
    }

    /** The definition this renderer draws, or null when its file is missing or invalid. */
    public GeoEntityDefinition definition() {
        return GeoAssets.definition(this.definition, GeoEntityDefinition.CODEC);
    }

    @Override
    public GeoEntityRenderState createRenderState() {
        return new GeoEntityRenderState();
    }

    @Override
    public void extractRenderState(T entity, GeoEntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        GeoEntityDefinition definition = definition();
        state.definition = definition;
        state.model = definition == null ? null : GeoAssets.model(definition.geo());
        state.ticks = entity.tickCount;
        state.context = new GeoContext(ItemStack.EMPTY, entity, null, null);
        if (state.model == null) {
            state.poses = Map.of();
            state.heldItems.clear();
            return;
        }

        MolangScope scope = new MolangScope();
        GeoQueries.fill(scope, entity, partialTick);
        Map<String, BonePose> poses = new HashMap<>(GeoAnimations.animate(
                definition.animation(),
                definition.geo(),
                this,
                state.context,
                scope,
                presetDriver(definition, partialTick)
        ));
        definition.headBone().ifPresent(bone -> lookWith(poses, bone, state.xRot, state.yRot));
        state.poses = poses;

        state.heldItems.keySet().retainAll(definition.heldItems().keySet());
        for (Map.Entry<String, GeoHeldItem> entry : definition.heldItems().entrySet()) {
            EquipmentSlot slot = entry.getValue().slot();
            ItemStackRenderState item = state.heldItems.computeIfAbsent(
                    entry.getKey(),
                    bone -> new ItemStackRenderState()
            );
            this.itemModelResolver.updateForLiving(item, entity.getItemBySlot(slot), displayContext(slot), entity);
        }
    }

    @Override
    public Identifier getTextureLocation(GeoEntityRenderState state) {
        return state.definition == null ? null : state.definition.texture().at(state.ticks);
    }

    @Override
    protected RenderType getRenderType(GeoEntityRenderState state, boolean isBodyVisible,
                                       boolean forceTransparent, boolean appearGlowing) {
        return null;
    }

    @Override
    protected void setupRotations(GeoEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        this.flipDegrees = state.definition != null && state.definition.death().isPresent() ? 0.0F : 90.0F;
        super.setupRotations(state, poseStack, bodyRot, entityScale);
    }

    @Override
    protected float getFlipDegrees() {
        return this.flipDegrees;
    }

    @Override
    protected void scale(GeoEntityRenderState state, PoseStack poseStack) {
        if (state.definition != null) {
            float scale = state.definition.scale();
            poseStack.scale(scale, scale, scale);
        }
    }

    @Override
    protected float getShadowRadius(GeoEntityRenderState state) {
        float radius = state.definition == null ? this.shadowRadius : state.definition.shadowRadius();
        float scale = state.definition == null ? 1.0F : state.definition.scale();
        return radius * scale * state.scale;
    }

    private static GeoDriver presetDriver(GeoEntityDefinition definition, float partialTick) {
        return context -> {
            if (!(context.holder() instanceof LivingEntity living)) {
                return null;
            }
            if (living.deathTime > 0 && definition.death().isPresent()) {
                return definition.death().get();
            }
            if (living.getSwingAnimation(partialTick) > 0.0F && definition.attack().isPresent()) {
                return definition.attack().get();
            }
            if (living.walkAnimation.speed(partialTick) > WALK_THRESHOLD && definition.walk().isPresent()) {
                return definition.walk().get();
            }
            return null;
        };
    }

    private static void lookWith(Map<String, BonePose> poses, String bone, float pitch, float yaw) {
        BonePose animated = poses.getOrDefault(bone, BonePose.REST);
        float[] rotation = animated.rotation();
        poses.put(bone, new BonePose(
                new float[] {rotation[0] - pitch, rotation[1] - yaw, rotation[2]},
                animated.position(),
                animated.scale()
        ));
    }

    private static ItemDisplayContext displayContext(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            case OFFHAND -> ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            case HEAD -> ItemDisplayContext.HEAD;
            default -> ItemDisplayContext.FIXED;
        };
    }

    // Shows a mob's name tag only the way vanilla mob renderers do.
    @Override
    protected boolean shouldShowName(T entity, double distanceSquared) {
        if (!super.shouldShowName(entity, distanceSquared)) {
            return false;
        }
        if (!(entity instanceof Mob mob)) {
            return true;
        }
        return mob.shouldShowName() || mob.hasCustomName() && mob == this.entityRenderDispatcher.crosshairPickEntity;
    }
}
