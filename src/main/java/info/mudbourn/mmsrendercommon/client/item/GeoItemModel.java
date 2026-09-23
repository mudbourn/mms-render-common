package info.mudbourn.mmsrendercommon.client.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import info.mudbourn.mmsrendercommon.client.anim.GeoAnimationSpec;
import info.mudbourn.mmsrendercommon.client.anim.GeoAnimations;
import info.mudbourn.mmsrendercommon.client.anim.GeoContext;
import info.mudbourn.mmsrendercommon.client.anim.GeoQueries;
import info.mudbourn.mmsrendercommon.client.geo.BonePose;
import info.mudbourn.mmsrendercommon.client.geo.GeoAssets;
import info.mudbourn.mmsrendercommon.client.geo.GeoModel;
import info.mudbourn.mmsrendercommon.client.geo.GeoTexture;
import info.mudbourn.mmsrendercommon.client.geo.MolangScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The {@code mms_render_common:geo} item model type: a Bedrock geo drawn as an item, animated
 * per holder.
 *
 * <p>An item definition names it like any vanilla item model:
 * <pre>
 * {"model": {
 *   "type": "mms_render_common:geo",
 *   "base": "armoryofdestiny:item/dragon_slayer_held",
 *   "geo": "armoryofdestiny:geo/item/dragon_slayer.geo.json",
 *   "texture": "armoryofdestiny:textures/item/dragon_slayer.png",
 *   "animations": "armoryofdestiny:animations/item/dragonslayer.animation.json",
 *   "idle": "dragonslayer.idle"
 * }}
 * </pre>
 * {@code base} is a block-format item model supplying the display transforms, GUI lighting and
 * particle sprite, as for vanilla {@code minecraft:special}. The geo is placed the way GeckoLib
 * places item models, so display transforms written for a GeckoLib item carry over unchanged.
 * {@code texture} and the optional {@code emissive_texture}, drawn full bright over it, are
 * {@link GeoTexture}s and may be flipbooks. Optional {@code translucent} (default false) draws
 * translucent, and {@code attachments} places registered attachments on bones. The animation
 * fields are those of {@link GeoAnimationSpec}. Texture or model variants by item state are
 * vanilla {@code minecraft:select}, {@code condition} or {@code range_dispatch} models around
 * one of these per variant; the geo and animation files are read once however many variants
 * name them.
 *
 * <p>A held item animates in its holder's {@code MAINHAND} or {@code OFFHAND} slot and an item
 * drawn on the head in {@code HEAD}, which is where triggers for it are addressed. Sound
 * keyframes play at the holder by id from {@code sounds.json}, with no sound event registration
 * needed; an item drawn without a holder plays none.
 */
public final class GeoItemModel implements ItemModel {

    public static final Identifier TYPE = Identifier.fromNamespaceAndPath("mms_render_common", "geo");

    /** The item definition's fields. */
    public record Unbaked(Identifier base,
                          Identifier geo,
                          GeoTexture texture,
                          Optional<GeoTexture> emissiveTexture,
                          boolean translucent,
                          Map<String, Identifier> attachments,
                          GeoAnimationSpec animation) implements ItemModel.Unbaked {

        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
                        Identifier.CODEC.fieldOf("geo").forGetter(Unbaked::geo),
                        GeoTexture.CODEC.fieldOf("texture").forGetter(Unbaked::texture),
                        GeoTexture.CODEC.optionalFieldOf("emissive_texture").forGetter(Unbaked::emissiveTexture),
                        Codec.BOOL.optionalFieldOf("translucent", false).forGetter(Unbaked::translucent),
                        Codec.unboundedMap(Codec.STRING, Identifier.CODEC)
                                .optionalFieldOf("attachments", Map.of())
                                .forGetter(Unbaked::attachments),
                        GeoAnimationSpec.MAP_CODEC.forGetter(Unbaked::animation)
                ).apply(instance, Unbaked::new)
        );

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(this.base);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context) {
            ModelBaker baker = context.blockModelBaker();
            ResolvedModel resolved = baker.getModel(this.base);
            ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(
                    baker,
                    resolved,
                    resolved.getTopTextureSlots()
            );
            return new GeoItemModel(this, properties);
        }
    }

    /** What one frame of one item draws. */
    private record Frame(GeoModel model,
                         RenderType renderType,
                         RenderType emissiveType,
                         Map<String, BonePose> poses,
                         Map<String, Identifier> attachments,
                         GeoContext context) {}

    private static final SpecialModelRenderer<Frame> RENDERER = new SpecialModelRenderer<>() {

        @Override
        public void submit(Frame frame, ItemDisplayContext context, PoseStack poseStack,
                           SubmitNodeCollector collector, int light, int overlay,
                           boolean glint, int outline) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.51F, 0.5F);
            submitGeometry(frame, poseStack, collector, frame.renderType(), light, overlay);
            if (frame.emissiveType() != null) {
                submitGeometry(frame, poseStack, collector, frame.emissiveType(), LightTexture.FULL_BRIGHT, overlay);
            }
            if (glint) {
                submitGeometry(frame, poseStack, collector, RenderTypes.entityGlint(), light, overlay);
            }
            GeoAnimations.submitAttachments(
                    frame.attachments(),
                    frame.model(),
                    frame.poses(),
                    frame.context(),
                    poseStack,
                    collector,
                    light,
                    overlay
            );
            poseStack.popPose();
        }

        @Override
        public void getExtents(Consumer<Vector3fc> output) {
        }

        @Override
        public Frame extractArgument(ItemStack stack) {
            return null;
        }
    };

    private final Unbaked source;
    private final ModelRenderProperties properties;

    private GeoItemModel(Unbaked source, ModelRenderProperties properties) {
        this.source = source;
        this.properties = properties;
    }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed) {
        GeoModel model = GeoAssets.model(this.source.geo());
        if (model == null) {
            return;
        }
        state.appendModelIdentityElement(this);
        state.setAnimated();
        ItemStackRenderState.LayerRenderState layer = state.newLayer();
        if (stack.hasFoil()) {
            layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
        }

        Entity holder = owner instanceof Entity entity ? entity : null;
        GeoContext geoContext = new GeoContext(stack, holder, slot(context, holder), context);
        MolangScope scope = new MolangScope();
        if (holder != null) {
            GeoQueries.fill(scope, holder, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        }
        Map<String, BonePose> poses = GeoAnimations.animate(
                this.source.animation(),
                this.source.geo(),
                this,
                geoContext,
                scope
        );

        long ticks = holder != null ? holder.tickCount : level != null ? level.getGameTime() : 0L;
        Identifier texture = this.source.texture().at(ticks);
        RenderType renderType = this.source.translucent()
                ? RenderTypes.entityTranslucent(texture)
                : RenderTypes.entityCutoutNoCull(texture);
        RenderType emissiveType = this.source.emissiveTexture()
                .map(emissive -> RenderTypes.entityTranslucentEmissive(emissive.at(ticks)))
                .orElse(null);
        Frame frame = new Frame(model, renderType, emissiveType, poses, this.source.attachments(), geoContext);
        layer.setupSpecialModel(RENDERER, frame);
        this.properties.applyToLayer(layer, context);
    }

    private static void submitGeometry(Frame frame, PoseStack poseStack, SubmitNodeCollector collector,
                                       RenderType renderType, int light, int overlay) {
        collector.submitCustomGeometry(
                poseStack,
                renderType,
                (pose, consumer) -> frame.model().draw(pose, consumer, light, overlay, -1, frame.poses())
        );
    }

    private static EquipmentSlot slot(ItemDisplayContext context, Entity holder) {
        if (context == ItemDisplayContext.HEAD) {
            return EquipmentSlot.HEAD;
        }
        if (!isHand(context)) {
            return null;
        }
        HumanoidArm arm = context.leftHand() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        if (holder instanceof LivingEntity living && arm != living.getMainArm()) {
            return EquipmentSlot.OFFHAND;
        }
        return EquipmentSlot.MAINHAND;
    }

    private static boolean isHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}
