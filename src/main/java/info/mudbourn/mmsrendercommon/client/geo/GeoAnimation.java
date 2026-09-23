package info.mudbourn.mmsrendercommon.client.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One parsed Bedrock animation: per-bone rotation, position and scale keyframes.
 *
 * <p>Keyframe values may be numbers or {@link Molang} expressions, may carry separate
 * {@code pre}/{@code post} values, and may carry a GeckoLib {@code easing} or a Bedrock
 * {@code lerp_mode} of {@code catmullrom}. The easing on a keyframe shapes the segment that
 * arrives at it. {@link #sample} returns a bone's {@link BonePose} at a time in seconds,
 * with the time wrapped or clamped by the animation's {@link Loop} mode.
 *
 * <p>{@code sound_effects} keyframes are read into {@link SoundCue}s, in GeckoLib's
 * {@code "namespace:sound|volume|pitch"} form; an {@link AnimationController} reports each cue
 * as playback crosses it.
 *
 * <p>As a {@link BoneAnimator} it drives rotation only, on the wearer's age.
 */
public final class GeoAnimation implements BoneAnimator {

    /** What happens after the last keyframe. */
    public enum Loop { PLAY_ONCE, HOLD_ON_LAST_FRAME, LOOP }

    /** A sound keyframe: the sound's id, played at a time in seconds with a volume and pitch. */
    public record SoundCue(float time, Identifier sound, float volume, float pitch) {}

    private record Keyframe(float time, Molang.Expr[] pre, Molang.Expr[] post, Easing.Spec easing) {}

    private record Track(List<Keyframe> rotation, List<Keyframe> position, List<Keyframe> scale) {}

    private final String name;
    private final float length;
    private final Loop loop;
    private final Map<String, Track> tracks;
    private final List<SoundCue> sounds;

    private GeoAnimation(String name, float length, Loop loop, Map<String, Track> tracks,
                         List<SoundCue> sounds) {
        this.name = name;
        this.length = length;
        this.loop = loop;
        this.tracks = tracks;
        this.sounds = sounds;
    }

    /** Parses the first animation in a Bedrock {@code animation.json} document. */
    public static GeoAnimation parse(JsonObject root) {
        return parseAll(root).values().iterator().next();
    }

    /** Parses every animation in a Bedrock {@code animation.json} document, keyed by name in file order. */
    public static Map<String, GeoAnimation> parseAll(JsonObject root) {
        Map<String, GeoAnimation> animations = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("animations").entrySet()) {
            JsonObject animation = entry.getValue().getAsJsonObject();
            animations.put(entry.getKey(), parseAnimation(entry.getKey(), animation));
        }
        return animations;
    }

    public String name() {
        return this.name;
    }

    /** Length in seconds. */
    public float length() {
        return this.length;
    }

    public Loop loop() {
        return this.loop;
    }

    /** Sound keyframes in time order. */
    public List<SoundCue> sounds() {
        return this.sounds;
    }

    /** Whether a {@link Loop#PLAY_ONCE} animation has run past its end at this many seconds. */
    public boolean finished(float seconds) {
        return this.loop == Loop.PLAY_ONCE && seconds >= this.length;
    }

    /**
     * The bone's pose at a time in seconds since the animation started, or null when this
     * animation does not touch the bone. A finished {@link Loop#PLAY_ONCE} animation returns
     * the rest pose.
     */
    public BonePose sample(String bone, float seconds, MolangScope scope) {
        Track track = this.tracks.get(bone);
        if (track == null) {
            return null;
        }
        if (finished(seconds)) {
            return BonePose.REST;
        }
        float time = localTime(seconds);
        return new BonePose(
                sampleChannel(track.rotation(), time, scope, 0.0F),
                sampleChannel(track.position(), time, scope, 0.0F),
                sampleChannel(track.scale(), time, scope, 1.0F)
        );
    }

    @Override
    public Set<String> animatedBones() {
        return this.tracks.keySet();
    }

    @Override
    public float[] rotation(String bone, float ageInTicks, float limbSwingPos, float limbSwingSpeed) {
        Track track = this.tracks.get(bone);
        if (track == null || track.rotation().isEmpty()) {
            return null;
        }
        float seconds = ageInTicks / 20.0F;
        MolangScope scope = new MolangScope().set("life_time", seconds);
        return sampleChannel(track.rotation(), localTime(seconds), scope, 0.0F);
    }

    /** Seconds since start mapped onto the timeline: wrapped when looping, clamped otherwise. */
    float localTime(float seconds) {
        if (this.length <= 0.0F) {
            return 0.0F;
        }
        if (this.loop == Loop.LOOP) {
            return seconds % this.length;
        }
        return Math.min(seconds, this.length);
    }

    private static float[] sampleChannel(List<Keyframe> keyframes, float time,
                                         MolangScope scope, float rest) {
        if (keyframes.isEmpty()) {
            return new float[] {rest, rest, rest};
        }
        Keyframe first = keyframes.get(0);
        if (time <= first.time()) {
            return eval(first.pre(), scope, time);
        }
        Keyframe last = keyframes.get(keyframes.size() - 1);
        if (time >= last.time()) {
            return eval(last.post(), scope, time);
        }

        int next = 1;
        while (keyframes.get(next).time() < time) {
            next++;
        }
        Keyframe a = keyframes.get(next - 1);
        Keyframe b = keyframes.get(next);
        float span = b.time() - a.time();
        double progress = span <= 0.0F ? 1.0 : (time - a.time()) / span;

        float[] from = eval(a.post(), scope, time);
        float[] to = eval(b.pre(), scope, time);
        if (b.easing().easing() == Easing.STEP) {
            return from;
        }
        if (a.easing().easing() == Easing.CATMULLROM || b.easing().easing() == Easing.CATMULLROM) {
            float[] before = eval(keyframes.get(Math.max(0, next - 2)).post(), scope, time);
            float[] after = eval(keyframes.get(Math.min(keyframes.size() - 1, next + 1)).pre(), scope, time);
            return catmullRom(before, from, to, after, (float) progress);
        }
        float t = (float) b.easing().apply(progress);
        return new float[] {
                from[0] + (to[0] - from[0]) * t,
                from[1] + (to[1] - from[1]) * t,
                from[2] + (to[2] - from[2]) * t
        };
    }

    private static float[] catmullRom(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            out[i] = 0.5F * (2.0F * p1[i]
                    + (p2[i] - p0[i]) * t
                    + (2.0F * p0[i] - 5.0F * p1[i] + 4.0F * p2[i] - p3[i]) * t * t
                    + (3.0F * p1[i] - p0[i] - 3.0F * p2[i] + p3[i]) * t * t * t);
        }
        return out;
    }

    private static float[] eval(Molang.Expr[] vector, MolangScope scope, float time) {
        return new float[] {
                (float) vector[0].eval(scope, time),
                (float) vector[1].eval(scope, time),
                (float) vector[2].eval(scope, time)
        };
    }

    private static GeoAnimation parseAnimation(String name, JsonObject animation) {
        Map<String, Track> tracks = new HashMap<>();
        float lastKeyframe = 0.0F;
        if (animation.has("bones")) {
            for (Map.Entry<String, JsonElement> entry : animation.getAsJsonObject("bones").entrySet()) {
                JsonObject bone = entry.getValue().getAsJsonObject();
                Track track = new Track(
                        parseChannel(bone.get("rotation")),
                        parseChannel(bone.get("position")),
                        parseChannel(bone.get("scale"))
                );
                tracks.put(BoneAnimator.canonicalBone(entry.getKey()), track);
                lastKeyframe = Math.max(lastKeyframe, lastTime(track.rotation()));
                lastKeyframe = Math.max(lastKeyframe, lastTime(track.position()));
                lastKeyframe = Math.max(lastKeyframe, lastTime(track.scale()));
            }
        }
        float length = animation.has("animation_length")
                ? animation.get("animation_length").getAsFloat()
                : lastKeyframe;
        List<SoundCue> sounds = parseSounds(animation.getAsJsonObject("sound_effects"));
        return new GeoAnimation(name, length, parseLoop(animation.get("loop")), tracks, sounds);
    }

    private static List<SoundCue> parseSounds(JsonObject effects) {
        List<SoundCue> sounds = new ArrayList<>();
        if (effects == null) {
            return sounds;
        }
        for (Map.Entry<String, JsonElement> entry : effects.entrySet()) {
            String effect = entry.getValue().getAsJsonObject().get("effect").getAsString();
            String[] segments = effect.split("\\|");
            Identifier sound = Identifier.tryParse(segments[0]);
            if (sound == null) {
                continue;
            }
            float volume = segments.length > 1 ? Float.parseFloat(segments[1]) : 1.0F;
            float pitch = segments.length > 2 ? Float.parseFloat(segments[2]) : 1.0F;
            sounds.add(new SoundCue(Float.parseFloat(entry.getKey()), sound, volume, pitch));
        }
        sounds.sort((a, b) -> Float.compare(a.time(), b.time()));
        return sounds;
    }

    private static Loop parseLoop(JsonElement loop) {
        if (loop == null || !loop.isJsonPrimitive()) {
            return Loop.PLAY_ONCE;
        }
        JsonPrimitive primitive = loop.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean() ? Loop.LOOP : Loop.PLAY_ONCE;
        }
        boolean hold = primitive.getAsString().equals("hold_on_last_frame");
        return hold ? Loop.HOLD_ON_LAST_FRAME : Loop.PLAY_ONCE;
    }

    private static float lastTime(List<Keyframe> keyframes) {
        return keyframes.isEmpty() ? 0.0F : keyframes.get(keyframes.size() - 1).time();
    }

    private static List<Keyframe> parseChannel(JsonElement channel) {
        List<Keyframe> keyframes = new ArrayList<>();
        if (channel == null) {
            return keyframes;
        }
        if (!channel.isJsonObject() || isKeyframeObject(channel.getAsJsonObject())) {
            keyframes.add(parseKeyframe(0.0F, channel));
            return keyframes;
        }
        for (Map.Entry<String, JsonElement> entry : channel.getAsJsonObject().entrySet()) {
            keyframes.add(parseKeyframe(Float.parseFloat(entry.getKey()), entry.getValue()));
        }
        keyframes.sort((a, b) -> Float.compare(a.time(), b.time()));
        return keyframes;
    }

    private static boolean isKeyframeObject(JsonObject object) {
        return object.has("vector") || object.has("pre") || object.has("post");
    }

    private static Keyframe parseKeyframe(float time, JsonElement element) {
        if (!element.isJsonObject()) {
            Molang.Expr[] vector = parseVector(element);
            return new Keyframe(time, vector, vector, Easing.Spec.LINEAR);
        }
        JsonObject frame = element.getAsJsonObject();
        Molang.Expr[] vector = frame.has("vector") ? parseVector(frame.get("vector")) : null;
        Molang.Expr[] pre = frame.has("pre") ? parseVector(frame.get("pre")) : vector;
        Molang.Expr[] post = frame.has("post") ? parseVector(frame.get("post")) : vector;
        pre = pre == null ? post : pre;
        post = post == null ? pre : post;
        return new Keyframe(time, pre, post, parseEasing(frame));
    }

    private static Easing.Spec parseEasing(JsonObject frame) {
        if (frame.has("lerp_mode") && frame.get("lerp_mode").getAsString().equals("catmullrom")) {
            return Easing.parse("catmullrom", null);
        }
        if (!frame.has("easing")) {
            return Easing.Spec.LINEAR;
        }
        Double argument = null;
        if (frame.has("easingArgs")) {
            JsonArray args = frame.getAsJsonArray("easingArgs");
            argument = args.isEmpty() ? null : args.get(0).getAsDouble();
        }
        return Easing.parse(frame.get("easing").getAsString(), argument);
    }

    private static Molang.Expr[] parseVector(JsonElement element) {
        if (element.isJsonObject()) {
            return parseVector(element.getAsJsonObject().get("vector"));
        }
        if (element.isJsonArray() && element.getAsJsonArray().size() >= 3) {
            JsonArray array = element.getAsJsonArray();
            return new Molang.Expr[] {
                    parseValue(array.get(0)),
                    parseValue(array.get(1)),
                    parseValue(array.get(2))
            };
        }
        JsonElement single = element.isJsonArray() ? element.getAsJsonArray().get(0) : element;
        Molang.Expr value = parseValue(single);
        return new Molang.Expr[] {value, value, value};
    }

    private static Molang.Expr parseValue(JsonElement element) {
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return new Molang.Constant(primitive.getAsDouble());
        }
        return Molang.compile(primitive.getAsString());
    }
}
