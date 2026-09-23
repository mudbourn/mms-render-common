package info.mudbourn.mmsrendercommon.client.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Bedrock {@code geo.json} model baked for drawing as a free-standing prop, such as an item
 * or block model ported from a GeckoLib mod.
 *
 * <p>The model is baked into the space GeckoLib bakes it into: pixels, Y up, origin at the
 * model's feet, X negated on every position and pivot, and Bedrock rotations read as
 * {@code (-x, -y, z)}. Face corners and UVs follow GeckoLib's own tables, so a model draws
 * here exactly as it did under GeckoLib. {@link #draw} divides by 16 into block units under
 * the caller's {@link PoseStack}. Each bone keeps its own quads and its place in the bone
 * hierarchy, so a {@link BonePose} map from an {@link AnimationController} can move, rotate
 * and scale a bone and everything under it. Quads are emitted double-sided through the
 * caller's render type. {@link #drawSubtrees} draws only some bones and their children, as
 * armor draws only the bones of the slot it is worn in, and {@link #boneFrame} places other
 * things on a bone.
 */
public final class GeoModel {

    /** A ready-to-draw quad: four positions, UVs and a normal, in the owning bone's space. */
    private record Quad(Vector3f[] positions, float[] u, float[] v, Vector3f normal) {}

    /** One bone: canonical name, parent index or -1, pivot, rest rotation in degrees, and its own quads. */
    private record Bone(String name, int parent, Vector3f pivot, float[] rest, List<Quad> quads) {}

    /** The eight corners of a cube, named as GeckoLib names them. */
    private record Corners(Vector3f bottomLeftBack, Vector3f bottomRightBack,
                           Vector3f topLeftBack, Vector3f topRightBack,
                           Vector3f topLeftFront, Vector3f topRightFront,
                           Vector3f bottomLeftFront, Vector3f bottomRightFront) {}

    private static final float[] NO_ROTATION = {0.0F, 0.0F, 0.0F};

    private static final Direction[] FACE_ORDER = {
            Direction.WEST,
            Direction.EAST,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.UP,
            Direction.DOWN
    };

    private final List<Bone> bones;
    private final Map<String, Integer> indexByName;
    private final Matrix4f[] restMatrices;

    private GeoModel(List<Bone> bones) {
        this.bones = bones;
        this.indexByName = new HashMap<>();
        for (int i = 0; i < bones.size(); i++) {
            this.indexByName.putIfAbsent(bones.get(i).name(), i);
        }
        this.restMatrices = boneMatrices(bones, Map.of());
    }

    /**
     * Reads, parses and bakes a Bedrock geo through {@link GeoAssets}, cached until the next
     * resource reload.
     *
     * <p>Reads from the client resource manager, so call it lazily on first draw. Returns null
     * if the resource is missing or cannot be parsed.
     */
    public static GeoModel load(Identifier geo) {
        return GeoAssets.model(geo);
    }

    /** Reads, parses and bakes a Bedrock geo without caching. */
    public static GeoModel read(ResourceManager resources, Identifier geo) throws IOException {
        return bake(GeoModelData.parse(GeoAssets.readObject(resources, geo)));
    }

    /** Bakes a parsed model, ordering bones so every parent precedes its children. */
    public static GeoModel bake(GeoModelData data) {
        Map<String, GeoModelData.Bone> byName = new HashMap<>();
        for (GeoModelData.Bone bone : data.bones()) {
            byName.put(bone.name(), bone);
        }
        Map<String, Integer> indexByName = new HashMap<>();
        List<Bone> bones = new ArrayList<>();
        for (GeoModelData.Bone bone : data.bones()) {
            addBone(bone, byName, indexByName, bones, data.textureWidth(), data.textureHeight());
        }
        return new GeoModel(bones);
    }

    /** Draws the model at rest. */
    public void draw(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        drawBones(pose, consumer, light, overlay, color, this.restMatrices);
    }

    /** Draws the model with the given bone poses, keyed by canonical bone name; absent bones stay at rest. */
    public void draw(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color,
                     Map<String, BonePose> poses) {
        drawBones(pose, consumer, light, overlay, color, matrices(poses));
    }

    /** Draws only the named bones and everything under them, posed as {@link #draw} poses them. */
    public void drawSubtrees(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color,
                             Map<String, BonePose> poses, Collection<String> roots) {
        boolean[] shown = new boolean[this.bones.size()];
        for (int i = 0; i < this.bones.size(); i++) {
            Bone bone = this.bones.get(i);
            shown[i] = roots.contains(bone.name()) || bone.parent() >= 0 && shown[bone.parent()];
        }
        drawBones(pose, consumer, light, overlay, color, matrices(poses), shown);
    }

    /** Whether a bone is one of the named bones or sits under one. */
    public boolean inSubtrees(String bone, Collection<String> roots) {
        Integer index = this.indexByName.get(BoneAnimator.canonicalBone(bone));
        while (index != null && index >= 0) {
            Bone current = this.bones.get(index);
            if (roots.contains(current.name())) {
                return true;
            }
            index = current.parent();
        }
        return false;
    }

    /** Whether the model has a bone of this name. */
    public boolean hasBone(String bone) {
        return this.indexByName.containsKey(BoneAnimator.canonicalBone(bone));
    }

    /** A bone's rest rotation in Bedrock Euler degrees, or null when there is no such bone. */
    public float[] restRotation(String bone) {
        Integer index = this.indexByName.get(BoneAnimator.canonicalBone(bone));
        return index == null ? null : this.bones.get(index).rest().clone();
    }

    /**
     * Where a bone sits under the given poses: a matrix from a frame at the bone's pivot, turned
     * with the bone, in block units, to the space {@link #draw} draws in. Null when there is no
     * such bone.
     */
    public Matrix4f boneFrame(String bone, Map<String, BonePose> poses) {
        Integer index = this.indexByName.get(BoneAnimator.canonicalBone(bone));
        if (index == null) {
            return null;
        }
        Vector3f pivot = this.bones.get(index).pivot();
        return new Matrix4f()
                .scale(1.0F / 16.0F)
                .mul(matrices(poses)[index])
                .translate(pivot)
                .scale(16.0F);
    }

    private Matrix4f[] matrices(Map<String, BonePose> poses) {
        return poses.isEmpty() ? this.restMatrices : boneMatrices(this.bones, poses);
    }

    private void drawBones(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color,
                           Matrix4f[] matrices) {
        drawBones(pose, consumer, light, overlay, color, matrices, null);
    }

    private void drawBones(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color,
                           Matrix4f[] matrices, boolean[] shown) {
        for (int i = 0; i < this.bones.size(); i++) {
            List<Quad> quads = this.bones.get(i).quads();
            if (quads.isEmpty() || shown != null && !shown[i]) {
                continue;
            }
            PoseStack.Pose bonePose = pose.copy();
            bonePose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
            bonePose.mulPose(matrices[i]);
            for (Quad quad : quads) {
                for (int corner = 0; corner < 4; corner++) {
                    Vector3f p = quad.positions()[corner];
                    consumer.addVertex(bonePose, p.x(), p.y(), p.z())
                            .setColor(color)
                            .setUv(quad.u()[corner], quad.v()[corner])
                            .setOverlay(overlay)
                            .setLight(light)
                            .setNormal(bonePose, quad.normal().x(), quad.normal().y(), quad.normal().z());
                }
            }
        }
    }

    /** Each bone's matrix: its parent's, then its animated offset and its pose about its pivot. */
    private static Matrix4f[] boneMatrices(List<Bone> bones, Map<String, BonePose> poses) {
        Matrix4f[] matrices = new Matrix4f[bones.size()];
        for (int i = 0; i < bones.size(); i++) {
            Bone bone = bones.get(i);
            BonePose animated = poses.getOrDefault(bone.name(), BonePose.REST);
            float[] rest = bone.rest();
            float[] r = animated.rotation();
            float[] t = animated.position();
            float[] s = animated.scale();
            Vector3f p = bone.pivot();
            Matrix4f local = new Matrix4f()
                    .translate(-t[0], t[1], t[2])
                    .translate(p.x(), p.y(), p.z())
                    .rotateZYX((float) Math.toRadians(rest[2] + r[2]),
                            (float) Math.toRadians(-(rest[1] + r[1])),
                            (float) Math.toRadians(-(rest[0] + r[0])))
                    .scale(s[0], s[1], s[2])
                    .translate(-p.x(), -p.y(), -p.z());
            matrices[i] = bone.parent() < 0 ? local : new Matrix4f(matrices[bone.parent()]).mul(local);
        }
        return matrices;
    }

    private static int addBone(GeoModelData.Bone bone, Map<String, GeoModelData.Bone> byName,
                               Map<String, Integer> indexByName, List<Bone> out,
                               int textureWidth, int textureHeight) {
        Integer existing = indexByName.get(bone.name());
        if (existing != null) {
            return existing;
        }
        GeoModelData.Bone parentBone = bone.parent() == null ? null : byName.get(bone.parent());
        int parent = parentBone == null
                ? -1
                : addBone(parentBone, byName, indexByName, out, textureWidth, textureHeight);

        List<Quad> quads = new ArrayList<>();
        for (GeoModelData.Cube cube : bone.cubes()) {
            bakeCube(cube, textureWidth, textureHeight, quads);
        }
        float[] p = bone.pivot();
        float[] rest = bone.rotation() == null ? NO_ROTATION : bone.rotation();
        out.add(new Bone(
                BoneAnimator.canonicalBone(bone.name()),
                parent,
                new Vector3f(-p[0], p[1], p[2]),
                rest,
                quads
        ));
        indexByName.put(bone.name(), out.size() - 1);
        return out.size() - 1;
    }

    private static void bakeCube(GeoModelData.Cube cube, int textureWidth, int textureHeight,
                                 List<Quad> out) {
        float[] origin = cube.origin();
        float[] size = cube.size();
        float inflate = cube.inflate();
        float x0 = -(origin[0] + size[0]) - inflate;
        float y0 = origin[1] - inflate;
        float z0 = origin[2] - inflate;
        float x1 = -origin[0] + inflate;
        float y1 = origin[1] + size[1] + inflate;
        float z1 = origin[2] + size[2] + inflate;
        Corners corners = new Corners(
                new Vector3f(x0, y0, z0),
                new Vector3f(x0, y0, z1),
                new Vector3f(x0, y1, z0),
                new Vector3f(x0, y1, z1),
                new Vector3f(x1, y1, z0),
                new Vector3f(x1, y1, z1),
                new Vector3f(x1, y0, z0),
                new Vector3f(x1, y0, z1)
        );

        boolean boxUv = cube.uv() instanceof GeoModelData.BoxUv;
        boolean mirror = cube.mirror();
        Matrix4f rotation = cubeRotation(cube);
        for (Direction direction : FACE_ORDER) {
            GeoModelData.Face face = faceUv(cube.uv(), direction, size);
            if (face == null) {
                continue;
            }
            Vector3f[] quadCorners = quadCorners(corners, direction, boxUv, mirror);
            float[] u = new float[4];
            float[] v = new float[4];
            uvFor(face, textureWidth, textureHeight, mirror, u, v);

            Vector3f[] positions = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                Vector3f corner = new Vector3f(quadCorners[i]);
                positions[i] = rotation == null ? corner : rotation.transformPosition(corner);
            }
            out.add(new Quad(positions, u, v, faceNormal(direction, mirror, rotation, size)));
        }
    }

    /** The cube's rotation about its pivot, or null when it is axis aligned. */
    private static Matrix4f cubeRotation(GeoModelData.Cube cube) {
        float[] r = cube.rotation();
        if (r == null) {
            return null;
        }
        float[] p = cube.pivot() == null ? NO_ROTATION : cube.pivot();
        return new Matrix4f()
                .translate(-p[0], p[1], p[2])
                .rotateZYX((float) Math.toRadians(r[2]),
                        (float) Math.toRadians(-r[1]),
                        (float) Math.toRadians(-r[0]))
                .translate(p[0], -p[1], -p[2]);
    }

    /** The face normal, flipped as GeckoLib flips it for mirrored cubes and inverted flat cubes. */
    private static Vector3f faceNormal(Direction direction, boolean mirror, Matrix4f rotation, float[] size) {
        Vector3f normal = new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        if (mirror) {
            normal.mul(-1.0F, 1.0F, 1.0F);
        }
        if (rotation != null) {
            rotation.transformDirection(normal);
        }
        if (normal.x() < 0.0F && (size[1] == 0.0F || size[2] == 0.0F)) {
            normal.mul(-1.0F, 1.0F, 1.0F);
        }
        if (normal.y() < 0.0F && (size[0] == 0.0F || size[2] == 0.0F)) {
            normal.mul(1.0F, -1.0F, 1.0F);
        }
        if (normal.z() < 0.0F && (size[0] == 0.0F || size[1] == 0.0F)) {
            normal.mul(1.0F, 1.0F, -1.0F);
        }
        return normal;
    }

    /** A face's UV rectangle: the per-face entry, or GeckoLib's box layout from floored sizes. */
    private static GeoModelData.Face faceUv(GeoModelData.Uv uv, Direction direction, float[] size) {
        if (uv instanceof GeoModelData.PerFaceUv perFace) {
            return perFace.faces().get(direction);
        }
        GeoModelData.BoxUv box = (GeoModelData.BoxUv) uv;
        float x = (float) Math.floor(size[0]);
        float y = (float) Math.floor(size[1]);
        float z = (float) Math.floor(size[2]);
        return switch (direction) {
            case WEST -> new GeoModelData.Face(box.u() + z + x, box.v() + z, z, y);
            case EAST -> new GeoModelData.Face(box.u(), box.v() + z, z, y);
            case NORTH -> new GeoModelData.Face(box.u() + z, box.v() + z, x, y);
            case SOUTH -> new GeoModelData.Face(box.u() + z + x + z, box.v() + z, x, y);
            case UP -> new GeoModelData.Face(box.u() + z, box.v(), x, z);
            case DOWN -> new GeoModelData.Face(box.u() + z + x, box.v() + z, x, -z);
        };
    }

    /** UVs for a face's four corners, with U reversed unless the cube is mirrored. */
    private static void uvFor(GeoModelData.Face face, int textureWidth, int textureHeight, boolean mirror,
                              float[] u, float[] v) {
        float u0 = face.u() / textureWidth;
        float v0 = face.v() / textureHeight;
        float u1 = (face.u() + face.uWidth()) / textureWidth;
        float v1 = (face.v() + face.vHeight()) / textureHeight;
        if (!mirror) {
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

    /** A face's four corners in GeckoLib's order, with mirrored cubes swapping opposite faces. */
    private static Vector3f[] quadCorners(Corners c, Direction direction, boolean boxUv, boolean mirror) {
        Direction side = switch (direction) {
            case WEST -> mirror ? Direction.EAST : Direction.WEST;
            case EAST -> mirror ? Direction.WEST : Direction.EAST;
            case UP -> mirror && !boxUv ? Direction.DOWN : Direction.UP;
            case DOWN -> mirror && !boxUv ? Direction.UP : Direction.DOWN;
            default -> direction;
        };
        return switch (side) {
            case WEST -> new Vector3f[] {
                    c.topRightBack(),
                    c.topLeftBack(),
                    c.bottomLeftBack(),
                    c.bottomRightBack()
            };
            case EAST -> new Vector3f[] {
                    c.topLeftFront(),
                    c.topRightFront(),
                    c.bottomRightFront(),
                    c.bottomLeftFront()
            };
            case NORTH -> new Vector3f[] {
                    c.topLeftBack(),
                    c.topLeftFront(),
                    c.bottomLeftFront(),
                    c.bottomLeftBack()
            };
            case SOUTH -> new Vector3f[] {
                    c.topRightFront(),
                    c.topRightBack(),
                    c.bottomRightBack(),
                    c.bottomRightFront()
            };
            case UP -> new Vector3f[] {
                    c.topRightBack(),
                    c.topRightFront(),
                    c.topLeftFront(),
                    c.topLeftBack()
            };
            case DOWN -> new Vector3f[] {
                    c.bottomLeftBack(),
                    c.bottomLeftFront(),
                    c.bottomRightFront(),
                    c.bottomRightBack()
            };
        };
    }
}
