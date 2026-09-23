package info.mudbourn.mmsrendercommon.client.anim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import info.mudbourn.mmsrendercommon.client.geo.Molang;
import info.mudbourn.mmsrendercommon.client.geo.MolangScope;

/**
 * One {@code states} rule: play an animation while a Molang condition is non-zero.
 *
 * <pre>
 * "states": [
 *   {"when": "q.is_flying", "animation": "flying"},
 *   {"when": "q.ground_speed > 0.5", "animation": "walk"}
 * ]
 * </pre>
 * The first rule whose condition holds wins; when none holds, the definition's {@code idle}
 * plays. Conditions read the queries {@link GeoQueries} supplies.
 */
public record GeoState(Condition when, String animation) {

    /** A compiled Molang condition, kept with its source so it encodes back unchanged. */
    public record Condition(String source, Molang.Expr expr) {

        public static final Codec<Condition> CODEC = Codec.STRING.comapFlatMap(
                Condition::compile,
                Condition::source
        );

        private static DataResult<Condition> compile(String source) {
            try {
                return DataResult.success(new Condition(source, Molang.compile(source)));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(exception::getMessage);
            }
        }

        public boolean holds(MolangScope scope) {
            return this.expr.eval(scope, 0.0) != 0.0;
        }
    }

    public static final Codec<GeoState> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Condition.CODEC.fieldOf("when").forGetter(GeoState::when),
                    Codec.STRING.fieldOf("animation").forGetter(GeoState::animation)
            ).apply(instance, GeoState::new)
    );
}
