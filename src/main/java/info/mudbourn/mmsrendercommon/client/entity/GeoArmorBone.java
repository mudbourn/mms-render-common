package info.mudbourn.mmsrendercommon.client.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Where a geo entity draws the armor it wears in a slot: on a bone, as the vanilla humanoid armor
 * model's {@code part} drawn from the bone's pivot, as GeckoLib's armor layer does.
 */
public record GeoArmorBone(EquipmentSlot slot, Part part) {

    public static final Codec<GeoArmorBone> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EquipmentSlot.CODEC.fieldOf("slot").forGetter(GeoArmorBone::slot),
                    Part.CODEC.fieldOf("part").forGetter(GeoArmorBone::part)
            ).apply(instance, GeoArmorBone::new)
    );

    /** A part of the humanoid armor model. */
    public enum Part implements StringRepresentable {
        HEAD("head"),
        BODY("body"),
        RIGHT_ARM("right_arm"),
        LEFT_ARM("left_arm"),
        RIGHT_LEG("right_leg"),
        LEFT_LEG("left_leg");

        public static final Codec<Part> CODEC = StringRepresentable.fromEnum(Part::values);

        private final String id;

        Part(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }

        /** This part of a humanoid model. */
        public ModelPart of(HumanoidModel<?> model) {
            return switch (this) {
                case HEAD -> model.head;
                case BODY -> model.body;
                case RIGHT_ARM -> model.rightArm;
                case LEFT_ARM -> model.leftArm;
                case RIGHT_LEG -> model.rightLeg;
                case LEFT_LEG -> model.leftLeg;
            };
        }
    }
}
