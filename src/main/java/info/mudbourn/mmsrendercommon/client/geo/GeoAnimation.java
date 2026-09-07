package info.mudbourn.mmsrendercommon.client.geo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A parsed Bedrock animation, reduced to the one thing the ported furs use: per-bone
 * rotation over a looping timeline.
 *
 * <p>Only bone rotation tracks are read; Bedrock position and scale channels, and the
 * Molang expression forms of a keyframe, are ignored because no ported fur needs them.
 * A track is a list of keyframes sorted by time, sampled by {@link #rotationOf}, which
 * returns Euler degrees in Bedrock space for the baker's convention to mirror. It loops
 * on age alone and ignores the walk cycle, so a moving wearer swings no differently.
 */
public final class GeoAnimation implements BoneAnimator {

    /** One rotation keyframe: a time in seconds and the Euler degrees reached there. */
    private record Keyframe(float time, float[] vector, boolean easeInOut) {}

    private final float length;
    private final Map<String, List<Keyframe>> tracks;

    private GeoAnimation(float length, Map<String, List<Keyframe>> tracks) {
        this.length = length;
        this.tracks = tracks;
    }

    @Override
    public Set<String> animatedBones() {
        return this.tracks.keySet();
    }

    @Override
    public float[] rotation(String bone, float ageInTicks, float limbSwingPos, float limbSwingSpeed) {
        return rotationOf(bone, ageInTicks / 20.0F);
    }

    /**
     * The bone's rotation in Bedrock Euler degrees at a time, looped over the animation
     * length, or null when the bone is not animated.
     */
    public float[] rotationOf(String bone, float timeSeconds) {
        List<Keyframe> track = this.tracks.get(bone);
        if (track == null || track.isEmpty()) {
            return null;
        }
        float time = this.length <= 0.0F ? 0.0F : timeSeconds % this.length;
        Keyframe previous = track.get(0);
        if (time <= previous.time()) {
            return previous.vector();
        }
        for (int i = 1; i < track.size(); i++) {
            Keyframe next = track.get(i);
            if (time <= next.time()) {
                float span = next.time() - previous.time();
                float t = span <= 0.0F ? 0.0F : (time - previous.time()) / span;
                return lerp(previous.vector(), next.vector(), ease(t, next.easeInOut()));
            }
            previous = next;
        }
        return track.get(track.size() - 1).vector();
    }

    /** Parses the first animation in a Bedrock {@code animation.json} document. */
    public static GeoAnimation parse(JsonObject root) {
        JsonObject animations = root.getAsJsonObject("animations");
        Map.Entry<String, JsonElement> first = animations.entrySet().iterator().next();
        JsonObject animation = first.getValue().getAsJsonObject();
        float length = animation.has("animation_length")
                ? animation.get("animation_length").getAsFloat()
                : 0.0F;

        Map<String, List<Keyframe>> tracks = new HashMap<>();
        if (animation.has("bones")) {
            for (Map.Entry<String, JsonElement> entry : animation.getAsJsonObject("bones").entrySet()) {
                JsonObject bone = entry.getValue().getAsJsonObject();
                if (bone.has("rotation")) {
                    tracks.put(BoneAnimator.canonicalBone(entry.getKey()),
                            parseTrack(bone.getAsJsonObject("rotation")));
                }
            }
        }
        return new GeoAnimation(length, tracks);
    }

    private static List<Keyframe> parseTrack(JsonObject rotation) {
        List<Keyframe> keyframes = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : rotation.entrySet()) {
            float time = Float.parseFloat(entry.getKey());
            JsonObject frame = entry.getValue().getAsJsonObject();
            float[] vector = readVec(frame.getAsJsonArray("vector"));
            boolean easeInOut = frame.has("easing")
                    && frame.get("easing").getAsString().startsWith("easeInOut");
            keyframes.add(new Keyframe(time, vector, easeInOut));
        }
        keyframes.sort((a, b) -> Float.compare(a.time(), b.time()));
        return keyframes;
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[] {
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t,
                a[2] + (b[2] - a[2]) * t
        };
    }

    /** Quadratic ease-in-out when the keyframe asks for it, otherwise linear. */
    private static float ease(float t, boolean easeInOut) {
        if (!easeInOut) {
            return t;
        }
        return t < 0.5F ? 2.0F * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 2.0) / 2.0F;
    }

    private static float[] readVec(com.google.gson.JsonArray array) {
        return new float[] {
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat()
        };
    }
}
