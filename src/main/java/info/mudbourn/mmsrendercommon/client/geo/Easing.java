package info.mudbourn.mmsrendercommon.client.geo;

import java.util.Locale;

/**
 * GeckoLib keyframe easings, applied to the 0..1 progress between two keyframes.
 *
 * <p>Each family is defined by its ease-in curve; the out and in-out forms are derived from
 * it the way GeckoLib derives them, so a ported animation eases the same as it did under
 * GeckoLib, including GeckoLib's own bounce and elastic shapes. {@link #STEP} and
 * {@link #CATMULLROM} are not curves on progress and are handled by the keyframe sampler.
 */
public enum Easing {
    LINEAR,
    STEP,
    CATMULLROM,
    SINE,
    QUAD,
    CUBIC,
    QUART,
    QUINT,
    EXPO,
    CIRC,
    BACK,
    ELASTIC,
    BOUNCE;

    /** Which side of the segment the curve shapes. */
    public enum Mode { IN, OUT, IN_OUT }

    /** A parsed {@code easing} value: family, mode and optional GeckoLib {@code easingArgs} value. */
    public record Spec(Easing easing, Mode mode, Double argument) {

        public static final Spec LINEAR = new Spec(Easing.LINEAR, Mode.IN, null);

        /** Eased progress for a raw 0..1 progress. */
        public double apply(double t) {
            return switch (this.mode) {
                case IN -> this.easing.in(t, this.argument);
                case OUT -> 1.0 - this.easing.in(1.0 - t, this.argument);
                case IN_OUT -> t < 0.5
                        ? this.easing.in(t * 2.0, this.argument) / 2.0
                        : 1.0 - this.easing.in((1.0 - t) * 2.0, this.argument) / 2.0;
            };
        }
    }

    /** Parses a GeckoLib easing name such as {@code easeInOutBack}; unknown names read as linear. */
    public static Spec parse(String name, Double argument) {
        String lower = name.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "linear" -> {
                return Spec.LINEAR;
            }
            case "step" -> {
                return new Spec(STEP, Mode.IN, argument);
            }
            case "catmullrom" -> {
                return new Spec(CATMULLROM, Mode.IN, argument);
            }
            default -> {
            }
        }
        Mode mode;
        String family;
        if (lower.startsWith("easeinout")) {
            mode = Mode.IN_OUT;
            family = lower.substring("easeinout".length());
        } else if (lower.startsWith("easein")) {
            mode = Mode.IN;
            family = lower.substring("easein".length());
        } else if (lower.startsWith("easeout")) {
            mode = Mode.OUT;
            family = lower.substring("easeout".length());
        } else {
            return Spec.LINEAR;
        }
        for (Easing easing : values()) {
            if (easing.name().toLowerCase(Locale.ROOT).equals(family)) {
                return new Spec(easing, mode, argument);
            }
        }
        return Spec.LINEAR;
    }

    private double in(double t, Double argument) {
        return switch (this) {
            case LINEAR, STEP, CATMULLROM -> t;
            case SINE -> 1.0 - Math.cos(t * Math.PI / 2.0);
            case QUAD -> t * t;
            case CUBIC -> t * t * t;
            case QUART -> t * t * t * t;
            case QUINT -> t * t * t * t * t;
            case EXPO -> t == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * t - 10.0);
            case CIRC -> 1.0 - Math.sqrt(1.0 - t * t);
            case BACK -> back(t, argument == null ? 1.70158 : argument);
            case ELASTIC -> elastic(t, argument == null ? 1.0 : argument);
            case BOUNCE -> bounce(t, argument == null ? 0.5 : argument);
        };
    }

    private static double back(double t, double n) {
        return t * t * ((n + 1.0) * t - n);
    }

    private static double elastic(double t, double n) {
        return 1.0 - Math.pow(Math.cos(t * Math.PI / 2.0), 3.0) * Math.cos(t * n * Math.PI);
    }

    private static double bounce(double t, double n) {
        double one = 121.0 / 16.0 * t * t;
        double two = 121.0 / 4.0 * n * Math.pow(t - 6.0 / 11.0, 2.0) + 1.0 - n;
        double three = 121.0 * n * n * Math.pow(t - 9.0 / 11.0, 2.0) + 1.0 - n * n;
        double four = 484.0 * n * n * n * Math.pow(t - 10.5 / 11.0, 2.0) + 1.0 - n * n * n;
        return Math.min(Math.min(one, two), Math.min(three, four));
    }
}
