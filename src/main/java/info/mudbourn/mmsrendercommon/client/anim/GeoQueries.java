package info.mudbourn.mmsrendercommon.client.anim;

import info.mudbourn.mmsrendercommon.client.geo.MolangScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Molang queries geo animations and {@code states} rules can read about their holder.
 *
 * <p>From any holder: {@code life_time} (seconds), {@code ground_speed} and
 * {@code vertical_speed} (blocks per second), {@code head_x_rotation} (pitch, degrees),
 * {@code is_on_ground}, {@code is_in_water}, {@code is_sneaking}, {@code is_sprinting},
 * {@code is_riding}, {@code is_on_fire} and {@code is_first_person}. From a living holder also:
 * {@code limb_swing}, {@code limb_swing_amount}, {@code head_y_rotation} (head yaw relative to
 * the body, degrees), {@code swing_progress} (0 to 1), {@code is_fall_flying},
 * {@code is_swimming}, {@code is_using_item}, {@code health} and {@code max_health}; from a
 * player, {@code is_flying}. Worn armor also gets the wearer's posed body parts as
 * {@code <part>_x_rotation}, {@code _y_rotation} and {@code _z_rotation} in degrees, for the parts
 * {@code head}, {@code body}, {@code right_arm}, {@code left_arm}, {@code right_leg} and
 * {@code left_leg}. {@code anim_time} is the playing animation's own clock. Flags read 1 or 0,
 * and anything a holder cannot supply reads 0.
 */
public final class GeoQueries {

    private GeoQueries() {
    }

    /** Sets the holder queries. */
    public static void fill(MolangScope scope, Entity holder, float partialTick) {
        Vec3 motion = holder.getDeltaMovement();
        Minecraft minecraft = Minecraft.getInstance();
        boolean firstPerson = minecraft.getCameraEntity() == holder
                && minecraft.options.getCameraType().isFirstPerson();
        scope.set("life_time", (holder.tickCount + partialTick) / 20.0)
                .set("ground_speed", Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 20.0)
                .set("vertical_speed", motion.y * 20.0)
                .set("head_x_rotation", holder.getViewXRot(partialTick))
                .set("is_on_ground", flag(holder.onGround()))
                .set("is_in_water", flag(holder.isInWater()))
                .set("is_sneaking", flag(holder.isCrouching()))
                .set("is_sprinting", flag(holder.isSprinting()))
                .set("is_riding", flag(holder.isPassenger()))
                .set("is_on_fire", flag(holder.isOnFire()))
                .set("is_first_person", flag(firstPerson));
        if (holder instanceof LivingEntity living) {
            float headYaw = Mth.rotLerp(partialTick, living.yHeadRotO, living.yHeadRot);
            float bodyYaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
            scope.set("limb_swing", living.walkAnimation.position(partialTick))
                    .set("limb_swing_amount", living.walkAnimation.speed(partialTick))
                    .set("head_y_rotation", Mth.wrapDegrees(headYaw - bodyYaw))
                    .set("swing_progress", living.getAttackAnim(partialTick))
                    .set("is_fall_flying", flag(living.isFallFlying()))
                    .set("is_swimming", flag(living.isVisuallySwimming()))
                    .set("is_using_item", flag(living.isUsingItem()))
                    .set("health", living.getHealth())
                    .set("max_health", living.getMaxHealth());
        }
        if (holder instanceof Player player) {
            scope.set("is_flying", flag(player.getAbilities().flying));
        }
    }

    /** Sets the body part rotation queries from a posed humanoid model. */
    public static void fillParts(MolangScope scope, HumanoidModel<?> model) {
        part(scope, "head", model.head);
        part(scope, "body", model.body);
        part(scope, "right_arm", model.rightArm);
        part(scope, "left_arm", model.leftArm);
        part(scope, "right_leg", model.rightLeg);
        part(scope, "left_leg", model.leftLeg);
    }

    private static void part(MolangScope scope, String name, ModelPart part) {
        scope.set(name + "_x_rotation", Math.toDegrees(part.xRot))
                .set(name + "_y_rotation", Math.toDegrees(part.yRot))
                .set(name + "_z_rotation", Math.toDegrees(part.zRot));
    }

    private static double flag(boolean value) {
        return value ? 1.0 : 0.0;
    }
}
