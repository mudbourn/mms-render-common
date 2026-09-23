package info.mudbourn.mmsrendercommon.client.geo;

import java.util.HashMap;
import java.util.Map;

/**
 * The values a {@link Molang} expression reads while it is evaluated.
 *
 * <p>{@code query.anim_time} is the playing animation's own clock and is supplied by the
 * sampler, not stored here. {@code query.life_time} and any other query or variable are
 * supplied by the caller through {@link #set}. Names are stored without their
 * {@code query.}/{@code q.} or {@code variable.}/{@code v.} prefix, lower case. A name that
 * was never set reads as zero.
 */
public final class MolangScope {

    private final Map<String, Double> queries = new HashMap<>();
    private final Map<String, Double> variables = new HashMap<>();

    /** Sets a query, named without its prefix, such as {@code life_time} or {@code ground_speed}. */
    public MolangScope set(String query, double value) {
        this.queries.put(query, value);
        return this;
    }

    /** Sets a variable, named without its prefix. */
    public MolangScope variable(String name, double value) {
        this.variables.put(name, value);
        return this;
    }

    double query(String name) {
        return this.queries.getOrDefault(name, 0.0);
    }

    double variable(String name) {
        return this.variables.getOrDefault(name, 0.0);
    }
}
