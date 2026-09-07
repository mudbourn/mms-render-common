package info.mudbourn.mmsrendercommon.client.geo;

import java.util.Set;

/**
 * Supplies a swinging bone's rotation to {@link HumanoidGeoModel} at draw time.
 *
 * <p>An animator names the bones it drives, so the baker can split those bones into
 * their own groups, and returns each one's rotation per frame in Bedrock Euler degrees
 * for the baker's convention to mirror. It sees the wearer's render clock and walk
 * cycle, so a driver may loop on age alone or react to movement like vanilla mob
 * models do. A keyframe track ({@link GeoAnimation}) is one animator; a hand-written
 * procedural curve is another.
 */
public interface BoneAnimator {

    /**
     * A bone name reduced to its identity, with Origin Furs armor-slot tags stripped.
     *
     * <p>Source models tag a bone with {@code _<slot>_hides} suffixes to mark that it
     * hides under that armor slot, so the same feather reads as {@code back_feather_0}
     * in the animation but {@code back_feather_0_elytra_hides_chestplate_hides} in the
     * geometry. Matching an animation track to a geo bone canonicalizes both through
     * here so the tags never break the join.
     */
    static String canonicalBone(String name) {
        return name.replaceAll("_[a-z0-9]+_hides", "");
    }

    /** The bones this animator drives; every other bone is baked static. */
    Set<String> animatedBones();

    /**
     * The bone's rotation this frame in Bedrock Euler degrees, or null to leave it at rest.
     *
     * @param ageInTicks the wearer's render age, the same clock vanilla idle swings use
     * @param limbSwingPos the walk-cycle phase
     * @param limbSwingSpeed the walk-cycle amplitude, zero when standing still
     */
    float[] rotation(String bone, float ageInTicks, float limbSwingPos, float limbSwingSpeed);
}
