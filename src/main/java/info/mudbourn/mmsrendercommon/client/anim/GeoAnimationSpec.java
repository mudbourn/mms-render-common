package info.mudbourn.mmsrendercommon.client.anim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * The animation fields shared by geo item and geo armor definitions.
 *
 * <p>{@code animations} names a Bedrock animation file. Each frame the base animation is the
 * {@code driver}'s pick, else the first matching {@code states} rule, else {@code idle}; with
 * none of them the model rests. One-off animations from triggers play over the base. Every
 * switch blends over {@code transition_ticks} (default 2).
 */
public record GeoAnimationSpec(Optional<Identifier> animations,
                               Optional<String> idle,
                               List<GeoState> states,
                               Optional<Identifier> driver,
                               float transitionTicks) {

    public static final MapCodec<GeoAnimationSpec> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Identifier.CODEC.optionalFieldOf("animations").forGetter(GeoAnimationSpec::animations),
                    Codec.STRING.optionalFieldOf("idle").forGetter(GeoAnimationSpec::idle),
                    GeoState.CODEC.listOf().optionalFieldOf("states", List.of()).forGetter(GeoAnimationSpec::states),
                    Identifier.CODEC.optionalFieldOf("driver").forGetter(GeoAnimationSpec::driver),
                    Codec.FLOAT.optionalFieldOf("transition_ticks", 2.0F).forGetter(GeoAnimationSpec::transitionTicks)
            ).apply(instance, GeoAnimationSpec::new)
    );
}
