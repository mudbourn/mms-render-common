package info.mudbourn.mmsrendercommon.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import info.mudbourn.mmsrendercommon.client.anim.GeoAnimations;
import info.mudbourn.mmsrendercommon.client.anim.GeoContext;
import info.mudbourn.mmsrendercommon.client.anim.GeoQueries;
import info.mudbourn.mmsrendercommon.client.geo.BonePose;
import info.mudbourn.mmsrendercommon.client.geo.GeoAssets;
import info.mudbourn.mmsrendercommon.client.geo.GeoModel;
import info.mudbourn.mmsrendercommon.client.geo.MolangScope;
import info.mudbourn.mmsrendercommon.duck.GeoHolderDuck;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Draws one {@link GeoArmorDefinition} for the items {@link GeoArmor#register} bound to it. */
final class GeoArmorRenderer implements ArmorRenderer {

    /** An armor bone, the slot it is drawn for, and the wearer's part it follows. */
    private record Segment(EquipmentSlot slot,
                           String bone,
                           Function<HumanoidModel<?>, ModelPart> part,
                           float offsetX,
                           float offsetY) {}

    private static final List<Segment> SEGMENTS = List.of(
            new Segment(EquipmentSlot.HEAD, "armorHead", model -> model.head, 0.0F, 0.0F),
            new Segment(EquipmentSlot.CHEST, "armorBody", model -> model.body, 0.0F, 0.0F),
            new Segment(EquipmentSlot.CHEST, "armorLeftArm", model -> model.leftArm, -5.0F, 2.0F),
            new Segment(EquipmentSlot.CHEST, "armorRightArm", model -> model.rightArm, 5.0F, 2.0F),
            new Segment(EquipmentSlot.LEGS, "armorLeftLeg", model -> model.leftLeg, -2.0F, 12.0F),
            new Segment(EquipmentSlot.LEGS, "armorRightLeg", model -> model.rightLeg, 2.0F, 12.0F),
            new Segment(EquipmentSlot.FEET, "armorLeftBoot", model -> model.leftLeg, -2.0F, 12.0F),
            new Segment(EquipmentSlot.FEET, "armorRightBoot", model -> model.rightLeg, 2.0F, 12.0F)
    );

    private final Identifier definition;

    GeoArmorRenderer(Identifier definition) {
        this.definition = definition.withPath(path -> "geo_armor/" + path + ".json");
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack,
                       HumanoidRenderState state, EquipmentSlot slot, int light,
                       HumanoidModel<HumanoidRenderState> contextModel) {
        GeoArmorDefinition definition = GeoAssets.definition(this.definition, GeoArmorDefinition.CODEC);
        List<Segment> segments = SEGMENTS.stream().filter(segment -> segment.slot() == slot).toList();
        if (definition == null || segments.isEmpty()) {
            return;
        }
        Entity wearer = wearer(state);
        boolean slim = wearer instanceof AbstractClientPlayer player
                && player.getSkin().model() == PlayerModelType.SLIM;
        Identifier geo = definition.geo(slim);
        GeoModel model = GeoAssets.model(geo);
        if (model == null) {
            return;
        }

        GeoContext context = new GeoContext(stack, wearer, slot, null);
        MolangScope scope = new MolangScope();
        if (wearer != null) {
            GeoQueries.fill(scope, wearer, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        }
        GeoQueries.fillParts(scope, contextModel);
        Map<String, BonePose> poses = new HashMap<>(GeoAnimations.animate(
                definition.animation(),
                geo,
                this,
                context,
                scope
        ));
        List<String> bones = segments.stream().map(Segment::bone).toList();
        for (Segment segment : segments) {
            followPart(model, poses, segment, segment.part().apply(contextModel));
        }

        long ticks = wearer != null ? wearer.tickCount : (long) state.ageInTicks;
        Identifier texture = definition.texture(slim).at(ticks);
        RenderType renderType = definition.translucent()
                ? RenderTypes.armorTranslucent(texture)
                : RenderTypes.armorCutoutNoCull(texture);
        int color = ARGB.opaque(DyedItemColor.getOrDefault(stack, -1));
        Map<String, Identifier> attachments = new HashMap<>(definition.attachments());
        attachments.keySet().removeIf(bone -> !model.inSubtrees(bone, bones));

        poseStack.pushPose();
        poseStack.translate(0.0F, 1.5F, 0.0F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        submit(collector, poseStack, renderType, model, poses, bones, light, color);
        definition.emissiveTexture().ifPresent(emissive -> submit(
                collector,
                poseStack,
                RenderTypes.entityTranslucentEmissive(emissive.at(ticks)),
                model,
                poses,
                bones,
                LightTexture.FULL_BRIGHT,
                -1
        ));
        if (stack.hasFoil()) {
            submit(collector, poseStack, RenderTypes.armorEntityGlint(), model, poses, bones, light, -1);
        }
        GeoAnimations.submitAttachments(
                attachments,
                model,
                poses,
                context,
                poseStack,
                collector,
                light,
                OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    private static void followPart(GeoModel model, Map<String, BonePose> poses, Segment segment, ModelPart part) {
        float[] rest = model.restRotation(segment.bone());
        if (rest == null) {
            return;
        }
        BonePose animated = poses.getOrDefault(segment.bone(), BonePose.REST);
        poses.put(segment.bone(), new BonePose(
                new float[] {
                        (float) Math.toDegrees(part.xRot) - rest[0],
                        (float) Math.toDegrees(part.yRot) - rest[1],
                        (float) Math.toDegrees(part.zRot) - rest[2]
                },
                new float[] {
                        part.x + segment.offsetX(),
                        segment.offsetY() - part.y,
                        part.z
                },
                animated.scale()
        ));
    }

    private static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType,
                               GeoModel model, Map<String, BonePose> poses, List<String> bones,
                               int light, int color) {
        collector.submitCustomGeometry(
                poseStack,
                renderType,
                (pose, consumer) -> model.drawSubtrees(
                        pose,
                        consumer,
                        light,
                        OverlayTexture.NO_OVERLAY,
                        color,
                        poses,
                        bones
                )
        );
    }

    private static Entity wearer(HumanoidRenderState state) {
        ClientLevel level = Minecraft.getInstance().level;
        int id = ((GeoHolderDuck) state).mmsRenderCommon$geoHolderId();
        return level == null || id < 0 ? null : level.getEntity(id);
    }
}
