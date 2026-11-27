package info.mudbourn.mmsrendercommon.client.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import info.mudbourn.mmsrendercommon.client.anim.GeoAnimationSpec;
import info.mudbourn.mmsrendercommon.client.geo.GeoTexture;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A geo entity definition, read from {@code assets/<ns>/geo_entity/<path>.json}.
 *
 * <pre>
 * {
 *   "geo": "fnc:geo/entity/wolf.geo.json",
 *   "texture": "fnc:textures/entity/wolf.png",
 *   "emissive_texture": "fnc:textures/entity/wolf_glowmask.png",
 *   "animations": "fnc:animations/entity/wolf.animation.json",
 *   "idle": "idle",
 *   "walk": "walk",
 *   "attack": "attack",
 *   "death": "death",
 *   "head_bone": "head",
 *   "held_items": {"right_hand": {"slot": "mainhand"}},
 *   "rest_rotations": {"left_arm": [-60, 0, 0]},
 *   "armor_bones": {"armorBipedHead": {"slot": "head", "part": "head"}},
 *   "scale": 1.2,
 *   "shadow_radius": 0.6
 * }
 * </pre>
 * The base animation each frame is the {@code driver}'s pick, else the first matching
 * {@code states} rule, else {@code death} while dying, {@code attack} while swinging,
 * {@code walk} while moving, else {@code idle}; the other animation fields are those of
 * {@link GeoAnimationSpec}. {@code head_bone} turns with the entity's look direction on top of
 * its animation. {@code emissive_texture} draws full bright over the rest, as glowing eyes do,
 * and {@code translucent} (default false) draws translucent rather than cutout.
 * {@code rest_rotations} turns bones by Euler degrees on top of their animation every frame,
 * for a pose the model does not have at rest. {@code held_items} draws the item in a slot on a bone,
 * {@code armor_bones} draws the armor worn in a slot as a part of the vanilla humanoid armor model
 * ({@code head}, {@code body}, {@code right_arm}, {@code left_arm}, {@code right_leg} or
 * {@code left_leg}) from a bone's pivot, and {@code attachments} places registered attachments on bones. {@code scale} (default 1) scales the model and its shadow,
 * whose radius is {@code shadow_radius} (default 0.5) blocks.
 */
public record GeoEntityDefinition(Identifier geo,
                                  GeoTexture texture,
                                  Optional<GeoTexture> emissiveTexture,
                                  boolean translucent,
                                  float scale,
                                  float shadowRadius,
                                  Optional<String> headBone,
                                  Optional<String> walk,
                                  Optional<String> attack,
                                  Optional<String> death,
                                  Map<String, GeoHeldItem> heldItems,
                                  Map<String, Identifier> attachments,
                                  Map<String, List<Float>> restRotations,
                                  Map<String, GeoArmorBone> armorBones,
                                  GeoAnimationSpec animation) {

    private static final Codec<List<Float>> VECTOR = Codec.FLOAT.listOf(3, 3);

    public static final Codec<GeoEntityDefinition> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Identifier.CODEC.fieldOf("geo").forGetter(GeoEntityDefinition::geo),
                    GeoTexture.CODEC.fieldOf("texture").forGetter(GeoEntityDefinition::texture),
                    GeoTexture.CODEC.optionalFieldOf("emissive_texture").forGetter(GeoEntityDefinition::emissiveTexture),
                    Codec.BOOL.optionalFieldOf("translucent", false).forGetter(GeoEntityDefinition::translucent),
                    Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(GeoEntityDefinition::scale),
                    Codec.FLOAT.optionalFieldOf("shadow_radius", 0.5F).forGetter(GeoEntityDefinition::shadowRadius),
                    Codec.STRING.optionalFieldOf("head_bone").forGetter(GeoEntityDefinition::headBone),
                    Codec.STRING.optionalFieldOf("walk").forGetter(GeoEntityDefinition::walk),
                    Codec.STRING.optionalFieldOf("attack").forGetter(GeoEntityDefinition::attack),
                    Codec.STRING.optionalFieldOf("death").forGetter(GeoEntityDefinition::death),
                    Codec.unboundedMap(Codec.STRING, GeoHeldItem.CODEC)
                            .optionalFieldOf("held_items", Map.of())
                            .forGetter(GeoEntityDefinition::heldItems),
                    Codec.unboundedMap(Codec.STRING, Identifier.CODEC)
                            .optionalFieldOf("attachments", Map.of())
                            .forGetter(GeoEntityDefinition::attachments),
                    Codec.unboundedMap(Codec.STRING, VECTOR)
                            .optionalFieldOf("rest_rotations", Map.of())
                            .forGetter(GeoEntityDefinition::restRotations),
                    Codec.unboundedMap(Codec.STRING, GeoArmorBone.CODEC)
                            .optionalFieldOf("armor_bones", Map.of())
                            .forGetter(GeoEntityDefinition::armorBones),
                    GeoAnimationSpec.MAP_CODEC.forGetter(GeoEntityDefinition::animation)
            ).apply(instance, GeoEntityDefinition::new)
    );
}
