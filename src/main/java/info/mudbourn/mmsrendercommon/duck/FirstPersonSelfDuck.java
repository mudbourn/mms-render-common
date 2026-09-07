package info.mudbourn.mmsrendercommon.duck;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Marks a render state as "this is the camera entity, drawn in first person".
 *
 * <p>Render states carry no back-reference to the entity they were extracted
 * from, so anything downstream of extraction — a feature renderer, a layer —
 * has no way to ask "is this me?" on its own. The flag is stamped once during
 * extraction, while the entity is still in hand, and read at draw time.
 *
 * <p>Deliberately not sourced from First-person Model's own
 * {@code LivingEntityRenderStateAccess}: reading that would make this a
 * compile-time dependency on a client mod we do not control the version of,
 * for a question — camera entity, first-person camera — that vanilla can
 * answer by itself.
 *
 * @see info.mudbourn.mmsrendercommon.mixin.headhide.LivingEntityRendererMixin
 */
public interface FirstPersonSelfDuck {

    boolean mmsCompat$isFirstPersonSelf();

    void mmsCompat$setFirstPersonSelf(boolean firstPersonSelf);

    /**
     * Whether this draw is a head piece on the first-person camera entity, and so
     * must not be drawn at all.
     *
     * <p>Asked by every mixin that can draw head equipment, not just the one that
     * hides it. Two of them share the {@code renderArmorPiece} HEAD seam and both
     * cancel, so whichever is applied last silences the other — and the ported-set
     * mixin was winning, which is why Lowlands and Weaver's helmets stayed on
     * screen while every ordinary helmet vanished. Each asks for itself instead of
     * relying on an ordering neither of them can see.
     */
    static boolean hidesHeadPiece(LivingEntityRenderState state, EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD
            && ((FirstPersonSelfDuck) state).mmsCompat$isFirstPersonSelf();
    }
}
