package info.mudbourn.mmsrendercommon.client.anim;

/**
 * Picks the base animation of a geo item or armor piece each frame, for state that a Molang
 * {@code states} rule cannot read, such as the stack's components.
 *
 * <p>Registered with {@link GeoAnimations#registerDriver} and named by a definition's
 * {@code driver} field.
 */
@FunctionalInterface
public interface GeoDriver {

    /** The animation name to play, or null to fall through to the {@code states} rules and {@code idle}. */
    String animation(GeoContext context);
}
