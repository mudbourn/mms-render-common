package info.mudbourn.mmsrendercommon.trigger;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Server to client: play a one-off geo animation on the item an entity holds or wears in a slot.
 *
 * @param animation the animation name, or empty to stop whatever one-off is playing
 */
public record GeoTriggerPayload(int entityId, EquipmentSlot slot, String animation) implements CustomPacketPayload {

    public static final Type<GeoTriggerPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("mms_render_common", "geo_trigger")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, GeoTriggerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            GeoTriggerPayload::entityId,
            EquipmentSlot.STREAM_CODEC,
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
