package info.mudbourn.mmsrendercommon.trigger;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side API for playing one-off geo animations, the counterpart of GeckoLib's
 * {@code triggerAnim}.
 *
 * <p>A trigger names an animation in the animation file of the geo item or geo armor the holder
 * has in a slot: {@code MAINHAND} or {@code OFFHAND} for a held item, an armor slot for worn
 * armor. It reaches the holder, if a player, and every player tracking the holder, provided their
 * client runs this mod. Each client plays it over the item's base animation the next time that
 * item is drawn; a client that does not draw it within a second drops it. Calls on a client-side
 * entity do nothing; use {@code GeoAnimations.trigger} on the client instead.
 */
public final class GeoTriggers {

    private GeoTriggers() {
    }

    /** Plays a one-off animation, by name, on the geo item in the holder's slot. */
    public static void trigger(Entity holder, EquipmentSlot slot, String animation) {
        send(holder, new GeoTriggerPayload(holder.getId(), slot, animation));
    }

    /** Stops the one-off animation playing on the geo item in the holder's slot. */
    public static void stop(Entity holder, EquipmentSlot slot) {
        send(holder, new GeoTriggerPayload(holder.getId(), slot, ""));
    }

    private static void send(Entity holder, GeoTriggerPayload payload) {
        if (holder.level().isClientSide()) {
            return;
        }
        List<ServerPlayer> recipients = new ArrayList<>(PlayerLookup.tracking(holder));
        if (holder instanceof ServerPlayer self && !recipients.contains(self)) {
            recipients.add(self);
        }
        for (ServerPlayer player : recipients) {
            if (ServerPlayNetworking.canSend(player, GeoTriggerPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
