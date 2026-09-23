package info.mudbourn.mmsrendercommon.client.armor;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

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
 * body part; so a GeckoLib armor model loads unchanged, and one geo can hold a whole set. As with
 * any armor, an item is drawn only in the slot its {@code equippable} component names, and that
 * component needs an {@code asset_id}; the equipment asset itself may be empty.
 */
public final class GeoArmor {

    private GeoArmor() {
    }

    /**
     * Draws the items as geo armor from the definition at
     * {@code assets/<ns>/geo_armor/<path>.json}.
     */
    public static void register(Identifier definition, ItemLike... items) {
        ArmorRenderer.register(new GeoArmorRenderer(definition), items);
    }
}
