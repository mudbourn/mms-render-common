package info.mudbourn.mmsrendercommon.client;

import info.mudbourn.mmsrendercommon.mixin.cemrelay.ModelPartChildrenAccessor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

/**
 * Relays an EMF/CEM-animated base model's pose onto a feature layer's own model.
 *
 * <p>Why this exists: in CEM there is no parent/child relationship between an
 * entity's base model and the models its feature layers draw. Each is an
 * independent model with an independent part tree, and EMF runs the CEM
 * animation from inside {@code ModelPart#render} — i.e. strictly after
 * {@code setupAnim} has returned. So a layer model cannot inherit the animation
 * (it is not a child) and cannot copy it either (nothing has been animated yet
 * at the point vanilla layers normally copy from). Fresh Animations therefore
 * animates a spider's abdomen while the mod-added spikes bolted to it stay in
 * the vanilla rest pose.
 *
 * <p>EMF solves the one case it ships with — {@code HumanoidArmorLayer} — via
 * {@code EMFBipedPose}, a flat six-part snapshot taken after the main model
 * animates and re-applied to the armor model. That approach does not generalise:
 * it assumes both models are {@code HumanoidModel}, that part names line up, and
 * that pivots match. Mod layer models satisfy none of those reliably.
 *
 * <h2>What this does instead</h2>
 *
 * <p>For each (target part, source part) pair, it makes the target's geometry
 * render exactly where the source part's frame has moved to, whatever the two
 * pivot layouts are. Writing {@code Aacc} for the source part's accumulated
 * animated matrix (composed from the model root down), {@code Racc} for the same
 * chain in its rest pose, and {@code Sacc} for the target part's accumulated
 * rest matrix, the matrix the target needs is
 *
 * <pre>  M = Aacc · Racc⁻¹ · Sacc</pre>
 *
 * <p>{@code Racc⁻¹ · Sacc} re-expresses the target's authored geometry in the
 * source part's rest frame; {@code Aacc} then carries it wherever the animation
 * has taken that frame. Accumulating down the chain matters: a spider's
 * {@code head} is a child of {@code body0}, so its own local transform is
 * meaningless to a layer part that hangs off the root.
 *
 * <p>{@code M} is decomposed back into the target {@code ModelPart}'s
 * translate/rotate/scale fields, since that is the only channel a plain
 * {@code ModelPart} offers. That is exact for the rotation, translation and
 * uniform-scale animations CEM packs actually write. A non-uniform scale
 * composed with a rotation somewhere up the chain would shear, which no
 * {@code ModelPart} can represent; that decomposes to the nearest
 * rotation-and-scale rather than failing.
 *
 * <p>When EMF is absent, or the base model is not an animated CEM model, every
 * entry point here is a no-op and the layer keeps whatever pose the mod's own
 * {@code setupAnim} gave it. Vanilla behaviour is never altered.
 */
public final class CemLayerPoseRelay {

    private static final String IEMF_MODEL = "traben.entity_model_features.models.IEMFModel";
    private static final String EMF_ROOT = "traben.entity_model_features.models.parts.EMFModelPartRoot";

    /**
     * Cortinarius Cow mushrooms to vanilla cow parts. The shroom model mirrors
     * the vanilla layout and pivots; only the leg names differ, and those are
     * matched by pivot and by which phase of the vanilla swing the mod's own
     * {@code setupAnim} gives them — {@code leg0} (4, 12, 7) and {@code leg3}
     * (-4, 12, -6) take {@code cos(t + π)}, {@code leg1} (-4, 12, 7) and
     * {@code leg2} (4, 12, -6) take {@code cos(t)}.
     */
    public static final Map<String, String> CORTINARIUS_COW_SHROOMS = Map.of(
            "body", "body",
            "head", "head",
            "leg0", "left_hind_leg",
            "leg1", "right_hind_leg",
            "leg2", "left_front_leg",
            "leg3", "right_front_leg");

    /**
     * Hell / Shadow / Crystal Spider spikes to vanilla spider parts, matched by
     * geometry rather than by name — the spike parts both hang off the root at
     * pivot (0, 24, 0) with cubes in absolute coordinates, sharing no layout with
     * the model they decorate. {@code bodySpikes} sits at z 5..15 over the
     * abdomen, which EMF reaches as vanilla {@code body1} (the jem calls it
     * {@code body}); {@code headSpikes} at z -7..-10 over the head, which vanilla
     * parents to {@code body0} (the jem's {@code neck}).
     */
    public static final Map<String, String> HELL_SPIDER_SPIKES = Map.of(
            "bodySpikes", "body1",
            "headSpikes", "head");

    /**
     * Frostiful's ice skates to vanilla humanoid parts. Unlike the other two maps
     * this one is the identity: {@code IceSkateModel extends HumanoidModel}, so it
     * is built from the same layer definition with the same names and the same
     * pivots. Only the legs carry geometry, but the whole biped is mapped so a
     * future skate model with a strap or a cuff on another part inherits the pose
     * without a code change.
     */
    public static final Map<String, String> ICE_SKATES = Map.of(
            "head", "head",
            "hat", "hat",
            "body", "body",
            "right_arm", "right_arm",
            "left_arm", "left_arm",
            "right_leg", "right_leg",
            "left_leg", "left_leg");

    /**
     * Vanilla humanoid parts to themselves, for armour models that are built from
     * the same layer definition as the body they clothe.
     *
     * <p>Identical in content to {@link #ICE_SKATES} and deliberately not shared
     * with it: the skates are one mod's model that happens to agree with vanilla,
     * while this is the general biped-to-biped case, and the two have no reason to
     * change together.
     *
     * <p>{@code hat} is mapped even though DetailedAnimations' armour jems call
     * that part {@code headwear} — EMF rewrites jem aliases onto vanilla names as
     * it builds the model, and a part the relay cannot find is skipped rather than
     * mispositioned.
     */
    public static final Map<String, String> HUMANOID_ARMOR = Map.of(
            "head", "head",
            "hat", "hat",
            "body", "body",
            "right_arm", "right_arm",
            "left_arm", "left_arm",
            "right_leg", "right_leg",
            "left_leg", "left_leg");

    private static volatile boolean unavailable;
    private static volatile MethodHandle isEmfModel;
    private static volatile MethodHandle getRootModel;
    private static volatile MethodHandle hasAnimation;

    private CemLayerPoseRelay() {
    }

    /**
     * Copies the animated pose of {@code base} onto {@code layer}.
     *
     * @param base           the entity's main model, already animated by EMF
     * @param layer          the feature layer's own model, already through its
     *                       own {@code setupAnim} (whose work this overwrites
     *                       for the mapped parts only)
     * @param targetToSource layer part name to base part name. Base names are
     *                       the <em>vanilla</em> part names, not the CEM/jem
     *                       aliases — EMF maps jem names onto vanilla ones when
     *                       it builds the model, so a jem's {@code "body"} is
     *                       reached here as the spider's {@code "body1"}.
     */
    public static void relay(Model base, Model layer, Map<String, String> targetToSource) {
        relay(base, targetToSource, layer);
    }

    /**
     * As {@link #relay(Model, Model, Map)}, but preserving whatever pose the layer
     * is already in rather than replacing it.
     *
     * <p>The plain relay writes {@code M = Aacc · Racc⁻¹ · Sacc} with {@code Sacc}
     * taken from the layer's <em>rest</em> pose, so it overwrites the mapped parts
     * outright. That is correct for a layer whose own {@code setupAnim} contributes
     * nothing the relay should keep — a spider's bolted-on spikes, a cow's
     * mushrooms — but it is destructive for a layer that has already been posed and
     * wants the animation applied <em>on top</em>: when the base model happens to be
     * sitting at rest, {@code Aacc == Racc} and {@code M} collapses to {@code Sacc},
     * snapping the layer back to its authored rest pose and discarding that posing.
     *
     * <p>This variant substitutes the layer's <em>current</em> accumulation for
     * {@code Sacc}, leaving {@code Aacc · Racc⁻¹} — the base model's animation
     * expressed as a delta from its own rest — as the only thing applied. An
     * unanimated base therefore contributes an identity delta and the layer keeps
     * its existing pose exactly, which is the property the destructive form only
     * appeared to have.
     */
    public static void relayOver(Model base, Model layer, Map<String, String> targetToSource) {
        relay(base, targetToSource, true, layer);
    }

    /**
     * Copies the base model's animated <em>root</em> transform onto the layer's root.
     *
     * <p>Deliberately not part of {@link #relay}, which walks the root's children and
     * never folds the root in — a jem-backed layer animates its own root, and doing
     * both would compound. This is for a layer that has no jem at all, where the root
     * transform is otherwise supplied by nobody.
     *
     * <p>It matters because DetailedAnimations puts the swim and crawl body transform
     * in the root: {@code player.jem} drives {@code root.rx/ry/tx/ty/tz} from its
     * {@code var.r*} terms, and the four armour jems mirror it through
     * {@code var.pr*}. A layer that copies only limb poses therefore swims with the
     * limbs while the body stays upright.
     *
     * <p>The armour jems offset the mirrored root slightly — {@code var.prty}
     * subtracts 2 while sneaking and a further 0.1 — which this does not reproduce;
     * it takes the wearer's root as-is. Worth revisiting if sneaking sits visibly
     * high, but the swim case does not involve either term.
     */
    public static void relayRoot(Model base, Model layer) {
        if (base == null || layer == null || !isAnimatedCemModel(base)) {
            return;
        }
        ModelPart from = base.root();
        ModelPart to = layer.root();

        to.x = from.x;
        to.y = from.y;
        to.z = from.z;
        to.xRot = from.xRot;
        to.yRot = from.yRot;
        to.zRot = from.zRot;
        to.xScale = from.xScale;
        to.yScale = from.yScale;
        to.zScale = from.zScale;
    }

    /**
     * Copies the animated pose of {@code base} onto several layer models at once,
     * walking the base's part tree a single time.
     *
     * <p>Layers that are {@code null} are skipped, so a caller holding a slot-keyed
     * set of models does not have to filter it first.
     */
    public static void relay(Model base, Map<String, String> targetToSource, Model... layers) {
        relay(base, targetToSource, false, layers);
    }

    private static void relay(Model base, Map<String, String> targetToSource,
                              boolean over, Model... layers) {
        if (base == null || layers.length == 0 || !isAnimatedCemModel(base)) {
            return;
        }

        Map<String, Matrix4f> animated = new HashMap<>();
        Map<String, Matrix4f> rest = new HashMap<>();
        accumulate(base.root(), new Matrix4f(), new Matrix4f(), animated, rest);

        for (Model layer : layers) {
            if (layer != null) {
                relayOnto(layer, targetToSource, animated, rest, over);
            }
        }
    }

    private static void relayOnto(Model layer, Map<String, String> targetToSource,
                                  Map<String, Matrix4f> animated, Map<String, Matrix4f> rest,
                                  boolean over) {
        // Both accumulations are computed either way; which one is used as the
        // target's basis is the whole difference between overwriting the layer's
        // existing pose and carrying it along. See relayOver.
        Map<String, Matrix4f> layerCurrent = new HashMap<>();
        Map<String, Matrix4f> layerRest = new HashMap<>();
        accumulate(layer.root(), new Matrix4f(), new Matrix4f(), layerCurrent, layerRest);
        Map<String, Matrix4f> layerBasis = over ? layerCurrent : layerRest;

        for (Map.Entry<String, String> entry : targetToSource.entrySet()) {
            ModelPart target = child(layer.root(), entry.getKey());
            Matrix4f sourceAnimated = animated.get(entry.getValue());
            Matrix4f sourceRest = rest.get(entry.getValue());
            Matrix4f targetRest = layerBasis.get(entry.getKey());
            if (target == null || sourceAnimated == null || sourceRest == null || targetRest == null) {
                // A mod update reshaped one of the models. Leave the layer stock
                // rather than dragging it somewhere arbitrary.
                continue;
            }
            apply(target, new Matrix4f(sourceAnimated)
                    .mul(new Matrix4f(sourceRest).invert())
                    .mul(targetRest));
        }
    }

    /**
     * Walks a part tree, recording each part's accumulated animated and rest
     * matrices by name.
     *
     * <p>Duplicate names across different branches would collide; vanilla and
     * mod part trees do not use them, and first-write-wins keeps the shallower
     * part, which is the one a layer is realistically bolted to.
     */
    private static void accumulate(ModelPart part, Matrix4f parentAnimated, Matrix4f parentRest,
                                   Map<String, Matrix4f> animated, Map<String, Matrix4f> rest) {
        for (Map.Entry<String, ModelPart> entry : children(part).entrySet()) {
            ModelPart child = entry.getValue();
            Matrix4f childAnimated = new Matrix4f(parentAnimated).mul(localOf(child));
            Matrix4f childRest = new Matrix4f(parentRest).mul(restOf(child));
            animated.putIfAbsent(entry.getKey(), childAnimated);
            rest.putIfAbsent(entry.getKey(), childRest);
            accumulate(child, childAnimated, childRest, animated, rest);
        }
    }

    /**
     * The part's current local transform, matching {@code ModelPart} render
     * order: translate, then {@code rotationZYX}, then scale.
     *
     * <p>Translations stay in part units (the {@code /16} that
     * {@code translateAndRotate} applies is a uniform factor, and every
     * translation here is both read and written in those units, so it cancels).
     */
    private static Matrix4f localOf(ModelPart part) {
        return new Matrix4f()
                .translate(part.x, part.y, part.z)
                .rotateZYX(part.zRot, part.yRot, part.xRot)
                .scale(part.xScale, part.yScale, part.zScale);
    }

    /** The same transform as authored, from the part's baked initial pose. */
    private static Matrix4f restOf(ModelPart part) {
        PartPose pose = part.getInitialPose();
        return new Matrix4f()
                .translate(pose.x(), pose.y(), pose.z())
                .rotateZYX(pose.zRot(), pose.yRot(), pose.xRot())
                .scale(pose.xScale(), pose.yScale(), pose.zScale());
    }

    /** Decomposes {@code m} back into the fields a ModelPart can express. */
    private static void apply(ModelPart part, Matrix4f m) {
        Vector3f translation = m.getTranslation(new Vector3f());
        part.x = translation.x;
        part.y = translation.y;
        part.z = translation.z;

        Vector3f scale = m.getScale(new Vector3f());
        part.xScale = scale.x;
        part.yScale = scale.y;
        part.zScale = scale.z;

        // getUnnormalizedRotation — not getNormalizedRotation — is the one that
        // copes with a scaled basis: it divides the columns out by their lengths
        // before reading the quaternion, where getNormalizedRotation assumes
        // that has already been done and silently skews the angles if it hasn't.
        // The ZYX order below matches how rotateZYX above, and ModelPart's own
        // translateAndRotate, compose these fields.
        Vector3f angles = m.getUnnormalizedRotation(new Quaternionf()).getEulerAnglesZYX(new Vector3f());
        part.xRot = angles.x;
        part.yRot = angles.y;
        part.zRot = angles.z;
    }

    /**
     * The first part named {@code name} at any depth under {@code root}, or null.
     *
     * <p>Depth-first and shallowest-wins along each branch, matching how
     * {@code Model#copyTransforms} builds its name map.
     */
    public static ModelPart findChild(ModelPart root, String name) {
        return child(root, name);
    }

    private static ModelPart child(ModelPart root, String name) {
        for (Map.Entry<String, ModelPart> entry : children(root).entrySet()) {
            if (entry.getKey().equals(name)) {
                return entry.getValue();
            }
            ModelPart found = child(entry.getValue(), name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> children(ModelPart part) {
        return ((ModelPartChildrenAccessor) (Object) part).mms$children();
    }

    /**
     * True only when EMF has replaced this model with a CEM model that carries
     * an animation. A CEM model without animations poses identically to vanilla,
     * so relaying from it would be work with no visible effect.
     */
    public static boolean isAnimatedCemModel(Model model) {
        if (!resolve()) {
            return false;
        }
        try {
            if (!(boolean) isEmfModel.invoke(model)) {
                return false;
            }
            Object root = getRootModel.invoke(model);
            return root != null && (boolean) hasAnimation.invoke(root);
        } catch (Throwable t) {
            unavailable = true;
            return false;
        }
    }

    /**
     * Resolves EMF's model interface reflectively, so this takes no compile-time
     * dependency on the mod and any upstream reshape degrades to leaving layers
     * stock rather than crashing every entity render.
     */
    private static boolean resolve() {
        if (unavailable) {
            return false;
        }
        if (isEmfModel != null) {
            return true;
        }
        synchronized (CemLayerPoseRelay.class) {
            if (unavailable) {
                return false;
            }
            if (isEmfModel != null) {
                return true;
            }
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                ClassLoader loader = CemLayerPoseRelay.class.getClassLoader();

                Class<?> modelClass = Class.forName(IEMF_MODEL, false, loader);
                Class<?> rootClass = Class.forName(EMF_ROOT, false, loader);

                MethodHandle is = lookup.unreflect(modelClass.getMethod("emf$isEMFModel"));
                MethodHandle get = lookup.unreflect(modelClass.getMethod("emf$getEMFRootModel"));
                MethodHandle has = lookup.unreflect(rootClass.getMethod("hasAnimation"));

                // Erase the mod-owned types so callers only ever handle Object.
                isEmfModel = is.asType(is.type().changeParameterType(0, Object.class));
                getRootModel = get.asType(get.type()
                        .changeParameterType(0, Object.class)
                        .changeReturnType(Object.class));
                hasAnimation = has.asType(has.type().changeParameterType(0, Object.class));
                return true;
            } catch (Throwable t) {
                unavailable = true;
                return false;
            }
        }
    }
}
