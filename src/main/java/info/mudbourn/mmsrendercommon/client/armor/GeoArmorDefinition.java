package info.mudbourn.mmsrendercommon.client.armor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import info.mudbourn.mmsrendercommon.client.anim.GeoAnimationSpec;
import info.mudbourn.mmsrendercommon.client.geo.GeoTexture;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * A geo armor definition, read from {@code assets/<ns>/geo_armor/<path>.json}.
 *
 * <pre>
 * {
 *   "geo": "armoryofdestiny:geo/armor/winged_vengeance.geo.json",
 *   "texture": "armoryofdestiny:textures/item/armor/winged_vengeance.png",
 *   "translucent": true,
 *   "animations": "armoryofdestiny:animations/armor/winged_vengeance.animation.json",
 *   "idle": "idle",
 *   "states": [{"when": "q.is_flying", "animation": "flying"}],
 *   "transition_ticks": 20
 * }
 * </pre>
 * {@code slim_geo} and {@code slim_texture} replace the geo and texture on slim-armed players.
 * The textures are {@link GeoTexture}s, so a flipbook of frames is written as
 * {@code {"texture": ..., "frames": 4, "frame_ticks": 5}}. {@code emissive_texture} draws full
 * bright over the rest, {@code translucent} (default false) draws translucent rather than cutout,
 * and {@code attachments} places registered attachments on bones. The animation fields are those
 * of {@link GeoAnimationSpec}.
 */
public record GeoArmorDefinition(Identifier geo,
                                 Optional<Identifier> slimGeo,
                                 GeoTexture texture,
                                 Optional<GeoTexture> slimTexture,
                                 Optional<GeoTexture> emissiveTexture,
                                 boolean translucent,
                                 Map<String, Identifier> attachments,
                                 GeoAnimationSpec animation) {

    public static final Codec<GeoArmorDefinition> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Identifier.CODEC.fieldOf("geo").forGetter(GeoArmorDefinition::geo),
                    Identifier.CODEC.optionalFieldOf("slim_geo").forGetter(GeoArmorDefinition::slimGeo),
                    GeoTexture.CODEC.fieldOf("texture").forGetter(GeoArmorDefinition::texture),
                    GeoTexture.CODEC.optionalFieldOf("slim_texture").forGetter(GeoArmorDefinition::slimTexture),
                    GeoTexture.CODEC.optionalFieldOf("emissive_texture").forGetter(GeoArmorDefinition::emissiveTexture),
                    Codec.BOOL.optionalFieldOf("translucent", false).forGetter(GeoArmorDefinition::translucent),
                    Codec.unboundedMap(Codec.STRING, Identifier.CODEC)
                            .optionalFieldOf("attachments", Map.of())
                            .forGetter(GeoArmorDefinition::attachments),
                    GeoAnimationSpec.MAP_CODEC.forGetter(GeoArmorDefinition::animation)
            ).apply(instance, GeoArmorDefinition::new)
    );

    /** The geo for a wearer with slim arms or not. */
    public Identifier geo(boolean slim) {
        return slim ? this.slimGeo.orElse(this.geo) : this.geo;
    }

    /** The texture for a wearer with slim arms or not. */
    public GeoTexture texture(boolean slim) {
        return slim ? this.slimTexture.orElse(this.texture) : this.texture;
    }
}
