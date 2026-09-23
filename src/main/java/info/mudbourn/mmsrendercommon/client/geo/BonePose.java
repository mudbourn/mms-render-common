package info.mudbourn.mmsrendercommon.client.geo;

/**
 * One bone's animated offset from its rest pose, in Bedrock space.
 *
 * @param rotation Euler degrees added to the bone's rest rotation
 * @param position pixels the bone is moved by, in its parent's space
 * @param scale    per-axis scale about the bone's pivot
 */
public record BonePose(float[] rotation, float[] position, float[] scale) {

    /** The rest pose: no rotation, no movement, unit scale. */
    public static final BonePose REST = new BonePose(
            new float[] {0.0F, 0.0F, 0.0F},
            new float[] {0.0F, 0.0F, 0.0F},
            new float[] {1.0F, 1.0F, 1.0F}
    );

    /** Linear blend from one pose to another by {@code t} in 0..1. */
    public static BonePose lerp(BonePose from, BonePose to, float t) {
        return new BonePose(
                lerp(from.rotation, to.rotation, t),
                lerp(from.position, to.position, t),
                lerp(from.scale, to.scale, t)
        );
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[] {
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t,
                a[2] + (b[2] - a[2]) * t
        };
    }
}
