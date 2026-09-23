package info.mudbourn.mmsrendercommon.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import info.mudbourn.mmsrendercommon.client.geo.AnimationController;
import info.mudbourn.mmsrendercommon.client.geo.BonePose;
import info.mudbourn.mmsrendercommon.client.geo.GeoAnimation;
import info.mudbourn.mmsrendercommon.client.geo.GeoAssets;
import info.mudbourn.mmsrendercommon.client.geo.GeoModel;
import info.mudbourn.mmsrendercommon.client.geo.MolangScope;
import info.mudbourn.mmsrendercommon.trigger.GeoTriggerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Client-side animation state for geo items and geo armor, and the hooks that steer it.
 *
 * <p>Each holder keeps one {@link AnimationController} per slot and model, so the first and
 * third person views of one held item share a controller while two holders of the same item
 * each animate on their own. {@link #registerDriver} and {@link #registerAttachment} add the
 * {@link GeoDriver}s and {@link GeoAttachment}s definitions name. {@link #trigger} plays a
 * one-off animation from client code; the server does the same through {@code GeoTriggers},
 * whose packets land in {@link #receive}. A trigger starts the next time the item in that slot
 * is drawn and is dropped if it is not drawn within a second. Holders are held weakly.
 */
public final class GeoAnimations {

    private static final Logger LOGGER = LoggerFactory.getLogger("mms_render_common");

    private static final long TRIGGER_LIFETIME_TICKS = 20L;

    /** A queued one-off: the animation, or empty to stop, and when it arrived. */
    private record Trigger(long serial, String animation, long arrivedAt) {}

    /** One controller and the last trigger it has taken. */
    private static final class Instance {

        private final AnimationController controller;
        private long takenTrigger;

        private Instance(float transitionTicks) {
            this.controller = new AnimationController(transitionTicks);
        }
    }

    private static final Map<Identifier, GeoDriver> DRIVERS = new HashMap<>();
    private static final Map<Identifier, GeoAttachment> ATTACHMENTS = new HashMap<>();
    private static final Map<Object, Map<String, Instance>> INSTANCES = new WeakHashMap<>();
    private static final Map<Entity, Map<EquipmentSlot, Trigger>> TRIGGERS = new WeakHashMap<>();
    private static final Set<Identifier> MISSING_DRIVERS = new HashSet<>();
    private static long nextSerial = 1L;

    private GeoAnimations() {
    }

    /** Registers a driver under the id definitions name in their {@code driver} field. */
    public static void registerDriver(Identifier id, GeoDriver driver) {
        DRIVERS.put(id, driver);
    }

    /** Registers an attachment under the id definitions name in their {@code attachments} map. */
    public static void registerAttachment(Identifier id, GeoAttachment attachment) {
        ATTACHMENTS.put(id, attachment);
    }

    /** Plays a one-off animation, by name, on the geo item or armor in a holder's slot. */
    public static void trigger(Entity holder, EquipmentSlot slot, String animation) {
        ClientLevel level = Minecraft.getInstance().level;
        long now = level == null ? 0L : level.getGameTime();
        TRIGGERS.computeIfAbsent(holder, key -> new EnumMap<>(EquipmentSlot.class))
                .put(slot, new Trigger(nextSerial++, animation, now));
    }

    /** Stops the one-off animation playing on the geo item or armor in a holder's slot. */
    public static void stop(Entity holder, EquipmentSlot slot) {
        trigger(holder, slot, "");
    }

    /** Applies a trigger sent by the server. */
    public static void receive(GeoTriggerPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity holder = level == null ? null : level.getEntity(payload.entityId());
        if (holder != null) {
            trigger(holder, payload.slot(), payload.animation());
        }
    }

    /**
     * Advances the animation of one drawn geo and returns its bone poses for this frame.
     *
     * <p>Picks the base animation, starts any pending trigger for the context's holder and
     * slot, samples the pose and plays the sound keyframes crossed since the last frame at the
     * holder. {@code owner} keys the animation state when there is no holder; with one, the
     * holder does. Returns an empty map when the spec names no animations.
     *
     * @param model the geo the poses are for, which keys the state apart from other models
     */
    public static Map<String, BonePose> animate(GeoAnimationSpec spec, Identifier model, Object owner,
                                                GeoContext context, MolangScope scope) {
        Map<String, GeoAnimation> animations = spec.animations().map(GeoAssets::animations).orElse(Map.of());
        if (animations.isEmpty()) {
            return Map.of();
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        long gameTime = level == null ? 0L : level.getGameTime();
        float nowTicks = gameTime + partialTick;

        Entity holder = context.holder();
        Instance instance = INSTANCES.computeIfAbsent(holder == null ? owner : holder, key -> new HashMap<>())
                .computeIfAbsent(stateKey(context, model), key -> new Instance(spec.transitionTicks()));
        AnimationController controller = instance.controller;
        takeTrigger(instance, holder, context.slot(), animations, gameTime, nowTicks, scope);

        String baseName = baseAnimation(spec, context, scope);
        controller.play(baseName == null ? null : animations.get(baseName), nowTicks, scope);
        Map<String, BonePose> poses = controller.sample(nowTicks, scope);
        playCues(controller, holder, level);
        return poses;
    }

    /**
     * Submits a definition's attachments on their bones.
     *
     * @param poseStack at the space the model draws in
     */
    public static void submitAttachments(Map<String, Identifier> attachments, GeoModel model,
                                         Map<String, BonePose> poses, GeoContext context, PoseStack poseStack,
                                         SubmitNodeCollector collector, int light, int overlay) {
        for (Map.Entry<String, Identifier> entry : attachments.entrySet()) {
            GeoAttachment attachment = ATTACHMENTS.get(entry.getValue());
            Matrix4f frame = model.boneFrame(entry.getKey(), poses);
            if (attachment == null || frame == null) {
                continue;
            }
            poseStack.pushPose();
            poseStack.mulPose(frame);
            attachment.submit(context, poseStack, collector, light, overlay);
            poseStack.popPose();
        }
    }

    private static void takeTrigger(Instance instance, Entity holder, EquipmentSlot slot,
                                    Map<String, GeoAnimation> animations, long gameTime,
                                    float nowTicks, MolangScope scope) {
        if (holder == null || slot == null) {
            return;
        }
        Map<EquipmentSlot, Trigger> pending = TRIGGERS.get(holder);
        Trigger trigger = pending == null ? null : pending.get(slot);
        if (trigger == null || trigger.serial() <= instance.takenTrigger) {
            return;
        }
        instance.takenTrigger = trigger.serial();
        if (gameTime - trigger.arrivedAt() > TRIGGER_LIFETIME_TICKS) {
            return;
        }
        if (trigger.animation().isEmpty()) {
            instance.controller.stop(nowTicks, scope);
            return;
        }
        GeoAnimation animation = animations.get(trigger.animation());
        if (animation != null) {
            instance.controller.trigger(animation, nowTicks, scope);
        }
    }

    private static String baseAnimation(GeoAnimationSpec spec, GeoContext context, MolangScope scope) {
        if (spec.driver().isPresent()) {
            GeoDriver driver = DRIVERS.get(spec.driver().get());
            if (driver == null) {
                if (MISSING_DRIVERS.add(spec.driver().get())) {
                    LOGGER.warn("No geo driver registered as {}", spec.driver().get());
                }
            } else {
                String picked = driver.animation(context);
                if (picked != null) {
                    return picked;
                }
            }
        }
        for (GeoState state : spec.states()) {
            if (state.when().holds(scope)) {
                return state.animation();
            }
        }
        return spec.idle().orElse(null);
    }

    private static String stateKey(GeoContext context, Identifier model) {
        String where = context.slot() != null
                ? context.slot().name()
                : context.display() != null ? context.display().name() : "none";
        return where + "|" + model;
    }

    private static void playCues(AnimationController controller, Entity holder, ClientLevel level) {
        for (GeoAnimation.SoundCue cue : controller.drainSoundCues()) {
            if (holder == null || level == null) {
                continue;
            }
            level.playLocalSound(
                    holder.getX(),
                    holder.getY(),
                    holder.getZ(),
                    SoundEvent.createVariableRangeEvent(cue.sound()),
                    holder.getSoundSource(),
                    cue.volume(),
                    cue.pitch(),
                    false
            );
        }
    }
}
