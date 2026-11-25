package info.mudbourn.mmsrendercommon.client.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.List;

/**
 * The item a geo entity has in a slot, drawn on a bone.
 *
 * <p>The item is moved from the bone's pivot by {@code offset} pixels (default none), turned by
 * {@code rotation} Euler degrees, X then Y then Z (default {@code [-90, 0, 0]}), and scaled by
 * {@code scale} (default 1).
 */
public record GeoHeldItem(EquipmentSlot slot, List<Float> rotation, List<Float> offset, float scale) {

    private static final Codec<List<Float>> VECTOR = Codec.FLOAT.listOf(3, 3);

    public static final Codec<GeoHeldItem> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EquipmentSlot.CODEC.fieldOf("slot").forGetter(GeoHeldItem::slot),
                    VECTOR.optionalFieldOf("rotation", List.of(-90.0F, 0.0F, 0.0F)).forGetter(GeoHeldItem::rotation),
                    VECTOR.optionalFieldOf("offset", List.of(0.0F, 0.0F, 0.0F)).forGetter(GeoHeldItem::offset),
                    Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(GeoHeldItem::scale)
            ).apply(instance, GeoHeldItem::new)
    );
}
