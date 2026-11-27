package info.mudbourn.mmsrendercommon.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Draws the armor a geo entity wears on the bones its definition names. */
public class GeoArmorLayer extends GeoEntityLayer {

    private final EntityRendererProvider.Context context;
    private final Map<GeoArmorBone, EntityModel<GeoEntityRenderState>> models = new HashMap<>();
    private ArmorModelSet<HumanoidModel<HumanoidRenderState>> modelSet;

    public GeoArmorLayer(RenderLayerParent<GeoEntityRenderState, EntityModel<GeoEntityRenderState>> parent,
                         EntityRendererProvider.Context context) {
        super(parent);
        this.context = context;
    }

    @Override
    protected void submitGeo(PoseStack poseStack, SubmitNodeCollector collector, int light,
                             GeoEntityRenderState state) {
        EquipmentLayerRenderer equipment = this.context.getEquipmentRenderer();
        for (Map.Entry<String, GeoArmorBone> entry : state.definition.armorBones().entrySet()) {
            GeoArmorBone target = entry.getValue();
            ItemStack stack = state.armor.get(entry.getKey());
            Matrix4f frame = state.model.boneFrame(entry.getKey(), state.poses);
            Equippable equippable = stack == null ? null : stack.get(DataComponents.EQUIPPABLE);
            boolean worn = equippable != null
                    && equippable.assetId().isPresent()
                    && HumanoidArmorLayer.shouldRender(stack, target.slot());
            if (frame == null || !worn) {
                continue;
            }
            EquipmentClientInfo.LayerType layer = target.slot() == EquipmentSlot.LEGS
                    ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
                    : EquipmentClientInfo.LayerType.HUMANOID;
            poseStack.pushPose();
            poseStack.mulPose(frame);
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            equipment.renderLayers(
                    layer,
                    equippable.assetId().get(),
                    model(target),
                    state,
                    stack,
                    poseStack,
                    collector,
                    light,
                    state.outlineColor
            );
            poseStack.popPose();
        }
    }

    private EntityModel<GeoEntityRenderState> model(GeoArmorBone target) {
        return this.models.computeIfAbsent(target, bone -> {
            if (this.modelSet == null) {
                this.modelSet = ArmorModelSet.bake(
                        ModelLayers.PLAYER_ARMOR,
                        this.context.getModelSet(),
                        HumanoidModel::new
                );
            }
            ModelPart part = bone.part().of(this.modelSet.get(bone.slot()));
            part.setInitialPose(PartPose.ZERO);
            part.resetPose();
            return new EntityModel<>(new ModelPart(List.of(), Map.of("part", part))) {};
        });
    }
}
