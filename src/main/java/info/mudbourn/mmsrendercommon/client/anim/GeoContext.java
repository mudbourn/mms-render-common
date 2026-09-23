package info.mudbourn.mmsrendercommon.client.anim;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * What a geo item or armor piece is being drawn for this frame.
 *
 * @param stack   the item stack
 * @param holder  the entity holding or wearing it, or null when drawn without one
 * @param slot    the slot it is held or worn in, or null when it is in no slot, such as in a GUI
 * @param display the item display context, or null for worn armor
 */
public record GeoContext(ItemStack stack, Entity holder, EquipmentSlot slot, ItemDisplayContext display) {}
