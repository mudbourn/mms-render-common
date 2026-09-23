package info.mudbourn.mmsrendercommon.client.geo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plays {@link GeoAnimation}s on one model instance and produces its bone poses per frame.
 *
 * <p>One controller belongs to one thing being drawn (a held item, a prop, an entity). All
 * times are the caller's render clock in ticks, partial ticks included. {@link #play} sets
 * the base animation and {@link #trigger} plays another over it. A triggered
 * {@link GeoAnimation.Loop#PLAY_ONCE} animation returns to the base when it ends; a looping or
 * holding one stays until the next trigger, a new base, or {@link #stop}. Every switch blends
 * from the pose on screen at the moment of the switch to the new animation over
 * {@code transitionTicks}.
 *
 * <p>Sound keyframes the playing animation crosses while it is sampled queue up for
 * {@link #drainSoundCues}, once per crossing, including each pass of a loop.
 */
public final class AnimationController {

    private final float transitionTicks;

    private GeoAnimation base;
    private float baseStart;
    private GeoAnimation oneShot;
    private float oneShotStart;
    private Map<String, BonePose> blendFrom = Map.of();
    private float blendStart;
    private GeoAnimation cueAnimation;
    private float cueStart;
    private float cueTime;
    private final List<GeoAnimation.SoundCue> pendingCues = new ArrayList<>();

    public AnimationController(float transitionTicks) {
        this.transitionTicks = transitionTicks;
    }

    /**
     * Sets the base animation, or null for none, which rests. Setting the one already playing
     * does nothing, so this is safe to call every frame. A new base set under a running
     * play-once {@link #trigger} starts when that ends; any other triggered animation is
     * replaced at once.
     */
    public void play(GeoAnimation animation, float nowTicks, MolangScope scope) {
        if (animation == this.base) {
            return;
        }
        boolean underOneShot = this.oneShot != null
                && current(nowTicks) == this.oneShot
                && this.oneShot.loop() == GeoAnimation.Loop.PLAY_ONCE;
        if (!underOneShot) {
            beginBlend(nowTicks, scope);
            this.oneShot = null;
        }
        this.base = animation;
        this.baseStart = nowTicks;
    }

    /** Plays an animation once over the base, restarting it if it is already playing. */
    public void trigger(GeoAnimation animation, float nowTicks, MolangScope scope) {
        beginBlend(nowTicks, scope);
        this.oneShot = animation;
        this.oneShotStart = nowTicks;
    }

    /** Stops everything and returns to the rest pose, blending out; safe to call every frame. */
    public void stop(float nowTicks, MolangScope scope) {
        if (this.base == null && this.oneShot == null) {
            return;
        }
        beginBlend(nowTicks, scope);
        this.base = null;
        this.oneShot = null;
    }

    /** The animation currently driving the pose, or null at rest. */
    public GeoAnimation current(float nowTicks) {
        if (this.oneShot != null && !this.oneShot.finished((nowTicks - this.oneShotStart) / 20.0F)) {
            return this.oneShot;
        }
        return this.base;
    }

    /** Every animated bone's pose this frame, keyed by canonical bone name. */
    public Map<String, BonePose> sample(float nowTicks, MolangScope scope) {
        expireOneShot(nowTicks, scope);
        Map<String, BonePose> target = sampleTarget(nowTicks, scope);
        float progress = this.transitionTicks <= 0.0F
                ? 1.0F
                : (nowTicks - this.blendStart) / this.transitionTicks;
        if (progress >= 1.0F) {
            this.blendFrom = Map.of();
        }
        if (this.blendFrom.isEmpty()) {
            return target;
        }

        Set<String> bones = new HashSet<>(target.keySet());
        bones.addAll(this.blendFrom.keySet());
        Map<String, BonePose> blended = new HashMap<>();
        for (String bone : bones) {
            BonePose from = this.blendFrom.getOrDefault(bone, BonePose.REST);
            BonePose to = target.getOrDefault(bone, BonePose.REST);
            blended.put(bone, BonePose.lerp(from, to, Math.max(0.0F, progress)));
        }
        return blended;
    }

    /** The sound keyframes crossed since the last call, in the order they were crossed. */
    public List<GeoAnimation.SoundCue> drainSoundCues() {
        if (this.pendingCues.isEmpty()) {
            return List.of();
        }
        List<GeoAnimation.SoundCue> cues = List.copyOf(this.pendingCues);
        this.pendingCues.clear();
        return cues;
    }

    private Map<String, BonePose> sampleTarget(float nowTicks, MolangScope scope) {
        GeoAnimation playing = current(nowTicks);
        if (playing == null) {
            this.cueAnimation = null;
            return Map.of();
        }
        float start = playing == this.oneShot ? this.oneShotStart : this.baseStart;
        float seconds = (nowTicks - start) / 20.0F;
        trackCues(playing, start, seconds);
        Map<String, BonePose> poses = new HashMap<>();
        for (String bone : playing.animatedBones()) {
            poses.put(bone, playing.sample(bone, seconds, scope));
        }
        return poses;
    }

    private void trackCues(GeoAnimation playing, float start, float seconds) {
        float now = playing.localTime(seconds);
        boolean restarted = playing != this.cueAnimation || start != this.cueStart;
        float from = restarted ? -1.0F : this.cueTime;
        boolean wrapped = !restarted && playing.loop() == GeoAnimation.Loop.LOOP && now < from;
        for (GeoAnimation.SoundCue cue : playing.sounds()) {
            float time = cue.time();
            boolean crossed = wrapped ? time > from || time <= now : time > from && time <= now;
            if (crossed) {
                this.pendingCues.add(cue);
            }
        }
        this.cueAnimation = playing;
        this.cueStart = start;
        this.cueTime = now;
    }

    private void expireOneShot(float nowTicks, MolangScope scope) {
        if (this.oneShot == null || current(nowTicks) == this.oneShot) {
            return;
        }
        float endTicks = this.oneShotStart + this.oneShot.length() * 20.0F;
        trackCues(this.oneShot, this.oneShotStart, this.oneShot.length());
        this.blendFrom = poseAtEnd(this.oneShot, scope);
        this.blendStart = endTicks;
        this.oneShot = null;
        this.baseStart = endTicks;
    }

    private void beginBlend(float nowTicks, MolangScope scope) {
        this.blendFrom = sample(nowTicks, scope);
        this.blendStart = nowTicks;
    }

    private static Map<String, BonePose> poseAtEnd(GeoAnimation animation, MolangScope scope) {
        float end = Math.max(0.0F, animation.length() - 0.0001F);
        Map<String, BonePose> poses = new HashMap<>();
        for (String bone : animation.animatedBones()) {
            poses.put(bone, animation.sample(bone, end, scope));
        }
        return poses;
    }
}
