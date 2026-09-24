package info.mudbourn.mmsrendercommon.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ItemLike;

import java.util.HashMap;
import java.util.Map;

/**
 * Client API for drawing armor items as animated Bedrock geo, the counterpart of GeckoLib's
 * {@code GeoArmorRenderer}.
 *
 * <p>A mod registers its armor items against a {@link GeoArmorDefinition} from its client
 * initializer:
 * <pre>
 * GeoArmor.register(Identifier.fromNamespaceAndPath("mms_vanity", "raijin"), RAIJIN_HELMET, RAIJIN_CHESTPLATE);
 * </pre>
 * which reads {@code assets/mms_vanity/geo_armor/raijin.json}. The geo follows GeckoLib's armor
 * layout: {@code armorHead}, {@code armorBody}, {@code armorRightArm}, {@code armorLeftArm},
 * {@code armorRightLeg}, {@code armorLeftLeg}, {@code armorRightBoot} and {@code armorLeftBoot},
 * each drawn only for its slot, with everything under it, and posed onto the wearer's matching
 * body part; so a GeckoLib armor model loads unchanged, and one geo can hold a whole set.
 * {@code armorLegsBody} follows the body but is drawn for the legs slot, for leggings that hang
 * a belt or skirt off the torso. As with any armor, an item is drawn only in the slot its
 * {@code equippable} component names, and that component needs an {@code asset_id}; the
 * equipment asset itself may be empty.
 *
 * <p>An item needs no registration when its {@code equippable.asset_id} is {@code <ns>:<path>}
 * and {@code assets/<ns>/geo_armor/<path>.json} exists: any stack wearing that asset draws as
 * that definition, so a plain vanilla item carrying the component draws as geo armor from a
 * resource pack alone.
 */
public final class GeoArmor {

    private static final Map<Identifier, GeoArmorRenderer> BY_ASSET = new HashMap<>();

    private GeoArmor() {
    }

    /**
     * Draws the items as geo armor from the definition at
     * {@code assets/<ns>/geo_armor/<path>.json}.
     */
    public static void register(Identifier definition, ItemLike... items) {
        ArmorRenderer.register(new GeoArmorRenderer(definition), items);
    }

    /**
     * Whether a stack worn in a slot draws as the geo armor definition named by its equipment
     * asset. Call from the render thread.
     */
    public static boolean drawsByAsset(ItemStack stack, EquipmentSlot slot) {
        GeoArmorRenderer renderer = byAsset(stack, slot);
        return renderer != null && renderer.definition() != null;
    }

    /**
     * Draws a worn stack as the geo armor definition named by its equipment asset; does nothing
     * unless {@link #drawsByAsset} holds.
     */
    public static void renderByAsset(PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack,
                                     HumanoidRenderState state, EquipmentSlot slot, int light,
                                     HumanoidModel<HumanoidRenderState> contextModel) {
        GeoArmorRenderer renderer = byAsset(stack, slot);
        GeoArmorDefinition definition = renderer == null ? null : renderer.definition();
        if (definition != null) {
            renderer.draw(definition, poseStack, collector, stack, state, slot, light, contextModel);
        }
    }

    private static GeoArmorRenderer byAsset(ItemStack stack, EquipmentSlot slot) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot || equippable.assetId().isEmpty()) {
            return null;
        }
        Identifier asset = equippable.assetId().get().identifier();
        return BY_ASSET.computeIfAbsent(asset, GeoArmorRenderer::new);
    }
}
