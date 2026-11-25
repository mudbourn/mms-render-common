package info.mudbourn.mmsrendercommon.trigger;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Optional;

/**
 * Server to client: play a one-off geo animation on the item an entity holds or wears in a slot,
 * or on the geo entity itself.
 *
 * @param slot      the slot of the geo item or armor, or empty for the entity's own geo
 * @param animation the animation name, or empty to stop whatever one-off is playing
 */
public record GeoTriggerPayload(int entityId, Optional<EquipmentSlot> slot, String animation) implements CustomPacketPayload {

    public static final Type<GeoTriggerPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("mms_render_common", "geo_trigger")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, GeoTriggerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            GeoTriggerPayload::entityId,
            ByteBufCodecs.optional(EquipmentSlot.STREAM_CODEC),
            GeoTriggerPayload::slot,
            ByteBufCodecs.STRING_UTF8,
            GeoTriggerPayload::animation,
            GeoTriggerPayload::new
    );

    @Override
    public Type<GeoTriggerPayload> type() {
        return TYPE;
    }
}
