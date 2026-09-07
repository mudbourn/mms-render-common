package info.mudbourn.mmsrendercommon.client.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Bedrock geo model baked for drawing over a vanilla humanoid.
 *
 * <p>The ported Origin Furs models are authored as whole-body Bedrock geometry whose
 * bones carry vanilla humanoid names ({@code bipedHead}, {@code bipedBody}, ...).
 * Each bone's geometry is baked here into the local space of the matching vanilla
 * {@link ModelPart}, so that at draw time the fur inherits the wearer's pose for free
 * by riding the same {@code translateAndRotate} the base model already ran.
 *
 * <p>Nothing here uses {@code CubeListBuilder}: it cannot express the per-face UVs,
 * planar cards and per-cube rotations these models are built from. The quads are
 * emitted straight into a {@link VertexConsumer} instead, which is also why they are
 * drawn double-sided (a mirrored axis flips winding) through a no-cull layer.
 */
public final class HumanoidGeoModel {

    /** A ready-to-draw quad in one part's local space: four positions, UVs and a normal. */
    private record Quad(Vector3f[] positions, float[] u, float[] v, Vector3f normal) {}

    /**
     * A bone whose quads swing on an animation track, kept out of the static list.
     *
     * <p>Its quads are baked into the owning part's local space like any others, but its
     * pivot (also in that space) and bone name are held so the swing can be applied about
     * the pivot at draw time.
     */
    private record AnimatedGroup(String bone, Vector3f pivot, List<Quad> quads) {}

    /** Vanilla humanoid part names, in draw order. */
    private static final String[] PARTS = {
            "head", "body", "right_arm", "left_arm", "right_leg", "left_leg"
    };

    /**
     * Each part's pivot in vanilla model space (the part's {@code PartPose} offset).
     *
     * <p>Bedrock geometry is authored in absolute model coordinates, so a bone's quads
     * are converted to absolute space and then rebased onto the owning part's pivot,
     * which is where {@code translateAndRotate} will place the part's origin.
     */
    private static final Map<String, float[]> PIVOTS = Map.of(
            "head", new float[] {0.0F, 0.0F, 0.0F},
            "body", new float[] {0.0F, 0.0F, 0.0F},
            "right_arm", new float[] {-5.0F, 2.0F, 0.0F},
            "left_arm", new float[] {5.0F, 2.0F, 0.0F},
            "right_leg", new float[] {-1.9F, 12.0F, 0.0F},
            "left_leg", new float[] {1.9F, 12.0F, 0.0F});

    private final Map<String, List<Quad>> quadsByPart;
    private final Map<String, List<AnimatedGroup>> animatedByPart;
    private final GeoAnimation animation;

    private HumanoidGeoModel(Map<String, List<Quad>> quadsByPart,
                            Map<String, List<AnimatedGroup>> animatedByPart,
                            GeoAnimation animation) {
        this.quadsByPart = quadsByPart;
        this.animatedByPart = animatedByPart;
        this.animation = animation;
    }

    /** Bakes a parsed model with no animation; every bone is static. */
    public static HumanoidGeoModel bake(GeoModelData data) {
        return bake(data, null);
    }

    /**
     * Bakes a parsed model, resolving each bone to a humanoid part by name.
     *
     * <p>A bone the animation drives is baked into its own {@link AnimatedGroup} rather
     * than the part's static list, so its swing can be applied about its pivot at draw
     * time; every other bone is flattened into the static list as before.
     */
    public static HumanoidGeoModel bake(GeoModelData data, GeoAnimation animation) {
        Map<String, GeoModelData.Bone> byName = new HashMap<>();
        for (GeoModelData.Bone bone : data.bones()) {
            byName.put(bone.name(), bone);
        }

        Map<String, List<Quad>> quadsByPart = new HashMap<>();
        Map<String, List<AnimatedGroup>> animatedByPart = new HashMap<>();
        for (GeoModelData.Bone bone : data.bones()) {
            String part = resolvePart(bone, byName);
            if (part == null) {
                continue;
            }
            float[] pivot = PIVOTS.get(part);
            if (animation != null && animation.animates(bone.name())) {
                List<Quad> quads = new ArrayList<>();
                for (GeoModelData.Cube cube : bone.cubes()) {
                    bakeCube(cube, pivot, data.textureWidth(), data.textureHeight(), quads);
                }
                Vector3f bonePivot = pointToModelSpace(bone.pivot(), pivot);
                animatedByPart.computeIfAbsent(part, key -> new ArrayList<>())
                        .add(new AnimatedGroup(bone.name(), bonePivot, quads));
                continue;
            }
            List<Quad> quads = quadsByPart.computeIfAbsent(part, key -> new ArrayList<>());
            for (GeoModelData.Cube cube : bone.cubes()) {
                bakeCube(cube, pivot, data.textureWidth(), data.textureHeight(), quads);
            }
        }
        return new HumanoidGeoModel(quadsByPart, animatedByPart, animation);
    }

    /**
     * Submits the fur over a posed base model, one part at a time.
     *
     * <p>Each part is submitted as its own custom-geometry node so the collector
     * snapshots that part's world transform: the callback fires at draw time with a
     * copy of the pose taken here, which is why the {@code poseStack} can be popped
     * back immediately after each submit.
     */
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType,
                       ModelPart root, int light, int overlay, int color, float timeSeconds) {
        poseStack.pushPose();
        root.translateAndRotate(poseStack);
        for (String part : PARTS) {
            ModelPart node = root.getChild(part);
            poseStack.pushPose();
            node.translateAndRotate(poseStack);
            List<Quad> quads = this.quadsByPart.get(part);
            if (quads != null) {
                collector.submitCustomGeometry(poseStack, renderType,
                        (pose, consumer) -> drawPart(pose, consumer, quads, light, overlay, color));
            }
            submitAnimated(part, poseStack, collector, renderType, light, overlay, color, timeSeconds);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private void submitAnimated(String part, PoseStack poseStack, SubmitNodeCollector collector,
                                RenderType renderType, int light, int overlay, int color,
                                float timeSeconds) {
        List<AnimatedGroup> groups = this.animatedByPart.get(part);
        if (groups == null) {
            return;
        }
        for (AnimatedGroup group : groups) {
            List<Quad> quads = group.quads();
            poseStack.pushPose();
            Vector3f pivot = group.pivot();
            poseStack.translate(pivot.x() / 16.0F, pivot.y() / 16.0F, pivot.z() / 16.0F);
            poseStack.mulPose(swing(group.bone(), timeSeconds));
            poseStack.translate(-pivot.x() / 16.0F, -pivot.y() / 16.0F, -pivot.z() / 16.0F);
            collector.submitCustomGeometry(poseStack, renderType,
                    (pose, consumer) -> drawPart(pose, consumer, quads, light, overlay, color));
            poseStack.popPose();
        }
    }

    /**
     * The bone's animated rotation as a part-local matrix.
     *
     * <p>The track gives Bedrock Euler degrees; the model is drawn mirrored on X and Z,
     * so a Bedrock rotation appears here with its X and Z angles negated.
     */
    private Matrix4f swing(String bone, float timeSeconds) {
        if (this.animation == null) {
            return new Matrix4f();
        }
        float[] euler = this.animation.rotationOf(bone, timeSeconds);
        if (euler == null) {
            return new Matrix4f();
        }
        return new Matrix4f().rotateZYX(
                (float) Math.toRadians(-euler[2]),
                (float) Math.toRadians(euler[1]),
                (float) Math.toRadians(-euler[0]));
    }

    private void drawPart(PoseStack.Pose pose, VertexConsumer consumer, List<Quad> quads,
                          int light, int overlay, int color) {
        for (Quad quad : quads) {
            for (int i = 0; i < 4; i++) {
                Vector3f p = quad.positions()[i];
                consumer.addVertex(pose, p.x() / 16.0F, p.y() / 16.0F, p.z() / 16.0F)
                        .setColor(color)
                        .setUv(quad.u()[i], quad.v()[i])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, quad.normal().x(), quad.normal().y(), quad.normal().z());
            }
        }
    }

    /**
     * The humanoid part a bone draws onto, following parent links up to a named root.
     *
     * <p>A bone whose chain never reaches a known biped bone is dropped rather than
     * guessed onto the body, so a stray bone in a source model fails visibly (nothing
     * drawn) instead of appearing in the wrong place.
     */
    private static String resolvePart(GeoModelData.Bone bone, Map<String, GeoModelData.Bone> byName) {
        GeoModelData.Bone current = bone;
        while (current != null) {
            String part = PART_BY_BONE.get(current.name());
            if (part != null) {
                return part;
            }
            current = current.parent() == null ? null : byName.get(current.parent());
        }
        return null;
    }

    /** Source bone names to vanilla humanoid parts. */
    private static final Map<String, String> PART_BY_BONE = Map.of(
            "bipedHead", "head",
            "bipedBody", "body",
            "bipedRightArm", "right_arm",
            "bipedLeftArm", "left_arm",
            "bipedRightLeg", "right_leg",
            "bipedLeftLeg", "left_leg");

    private static void bakeCube(GeoModelData.Cube cube, float[] pivot,
                                 int textureWidth, int textureHeight, List<Quad> out) {
        float inflate = cube.inflate();
        float x0 = cube.origin()[0] - inflate;
        float y0 = cube.origin()[1] - inflate;
        float z0 = cube.origin()[2] - inflate;
        float x1 = cube.origin()[0] + cube.size()[0] + inflate;
        float y1 = cube.origin()[1] + cube.size()[1] + inflate;
        float z1 = cube.origin()[2] + cube.size()[2] + inflate;

        Matrix4f rotation = cubeRotation(cube);
        for (Direction direction : Direction.values()) {
            GeoModelData.Face face = faceUv(cube.uv(), direction, x0, y0, z0, x1, y1, z1);
            if (face == null) {
                continue;
            }
            Vector3f[] corners = faceCorners(direction, x0, y0, z0, x1, y1, z1);
            float[] u = new float[4];
            float[] v = new float[4];
            uvFor(face, direction, textureWidth, textureHeight, u, v);

            Vector3f[] positions = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                positions[i] = toModelSpace(corners[i], rotation, cube, pivot);
            }
            out.add(new Quad(positions, u, v, faceNormal(direction, rotation)));
        }
    }

    /**
     * Converts a Bedrock corner to the owning part's local space.
     *
     * <p>Bedrock is Y-up with the model origin at the feet; vanilla parts are Y-down
     * with geometry authored relative to the part pivot. Blockbench authors a mob
     * facing the opposite way to a Minecraft entity, so the conversion is a 180-degree
     * turn about Y: both X and Z are negated. That lands the source's right arm on the
     * vanilla part at {@code -x} and puts back-of-body geometry behind the player. The
     * per-cube rotation is applied in Bedrock space first, about the cube's own pivot.
     */
    private static Vector3f toModelSpace(Vector3f corner, Matrix4f rotation,
                                         GeoModelData.Cube cube, float[] pivot) {
        Vector3f p = new Vector3f(corner);
        if (rotation != null) {
            float[] cp = cube.pivot();
            p.sub(cp[0], cp[1], cp[2]);
            rotation.transformPosition(p);
            p.add(cp[0], cp[1], cp[2]);
        }
        return new Vector3f(-p.x() - pivot[0], (24.0F - p.y()) - pivot[1], -p.z() - pivot[2]);
    }

    /** A bare Bedrock point rebased onto the owning part's pivot, with no cube rotation. */
    private static Vector3f pointToModelSpace(float[] point, float[] pivot) {
        return new Vector3f(
                -point[0] - pivot[0],
                (24.0F - point[1]) - pivot[1],
                -point[2] - pivot[2]);
    }

    /** The cube's rotation about its pivot, or null when it is axis aligned. */
    private static Matrix4f cubeRotation(GeoModelData.Cube cube) {
        float[] r = cube.rotation();
        if (r == null) {
            return null;
        }
        // The model is mirrored on X and Z, so both those angles negate; Y is kept.
        return new Matrix4f().rotateZYX(
                (float) Math.toRadians(-r[2]),
                (float) Math.toRadians(r[1]),
                (float) Math.toRadians(-r[0]));
    }

    private static Vector3f faceNormal(Direction direction, Matrix4f rotation) {
        Vector3f normal = new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        if (rotation != null) {
            rotation.transformDirection(normal);
        }
        // toModelSpace negates all three axes, so the normal follows.
        return new Vector3f(-normal.x(), -normal.y(), -normal.z());
    }

    private static GeoModelData.Face faceUv(GeoModelData.Uv uv, Direction direction,
                                            float x0, float y0, float z0,
                                            float x1, float y1, float z1) {
        if (uv instanceof GeoModelData.PerFaceUv perFace) {
            return perFace.faces().get(direction);
        }
        GeoModelData.BoxUv box = (GeoModelData.BoxUv) uv;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        return switch (direction) {
            case DOWN -> new GeoModelData.Face(box.u() + dz, box.v(), dx, dz);
            case UP -> new GeoModelData.Face(box.u() + dz + dx, box.v(), dx, dz);
            case NORTH -> new GeoModelData.Face(box.u() + dz + dx, box.v() + dz, dx, dy);
            case SOUTH -> new GeoModelData.Face(box.u() + dz + dx + dz, box.v() + dz, dx, dy);
            case WEST -> new GeoModelData.Face(box.u(), box.v() + dz, dz, dy);
            case EAST -> new GeoModelData.Face(box.u() + dz + dx, box.v() + dz, dz, dy);
        };
    }

    private static void uvFor(GeoModelData.Face face, Direction direction,
                              int textureWidth, int textureHeight,
                              float[] u, float[] v) {
        float u0 = face.u() / textureWidth;
        float v0 = face.v() / textureHeight;
        float u1 = (face.u() + face.uWidth()) / textureWidth;
        float v1 = (face.v() + face.vHeight()) / textureHeight;
        // toModelSpace mirrors X; a face whose normal is not X has its U along X, so flip U.
        if (direction.getAxis() != Direction.Axis.X) {
            float swap = u0;
            u0 = u1;
            u1 = swap;
        }
        u[0] = u0;
        v[0] = v0;
        u[1] = u1;
        v[1] = v0;
        u[2] = u1;
        v[2] = v1;
        u[3] = u0;
        v[3] = v1;
    }

    /** The four corners of a face, ordered to pair with the UV rectangle above. */
    private static Vector3f[] faceCorners(Direction direction,
                                          float x0, float y0, float z0,
                                          float x1, float y1, float z1) {
        return switch (direction) {
            case NORTH -> new Vector3f[] {
                    new Vector3f(x1, y1, z0), new Vector3f(x0, y1, z0),
                    new Vector3f(x0, y0, z0), new Vector3f(x1, y0, z0)};
            case SOUTH -> new Vector3f[] {
                    new Vector3f(x0, y1, z1), new Vector3f(x1, y1, z1),
                    new Vector3f(x1, y0, z1), new Vector3f(x0, y0, z1)};
            case EAST -> new Vector3f[] {
                    new Vector3f(x1, y1, z1), new Vector3f(x1, y1, z0),
                    new Vector3f(x1, y0, z0), new Vector3f(x1, y0, z1)};
            case WEST -> new Vector3f[] {
                    new Vector3f(x0, y1, z0), new Vector3f(x0, y1, z1),
                    new Vector3f(x0, y0, z1), new Vector3f(x0, y0, z0)};
            case UP -> new Vector3f[] {
                    new Vector3f(x0, y1, z0), new Vector3f(x1, y1, z0),
                    new Vector3f(x1, y1, z1), new Vector3f(x0, y1, z1)};
            case DOWN -> new Vector3f[] {
                    new Vector3f(x0, y0, z1), new Vector3f(x1, y0, z1),
                    new Vector3f(x1, y0, z0), new Vector3f(x0, y0, z0)};
        };
    }
}
