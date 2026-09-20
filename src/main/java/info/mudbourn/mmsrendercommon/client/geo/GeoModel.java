package info.mudbourn.mmsrendercommon.client.geo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Bedrock {@code geo.json} model baked for drawing as a free-standing prop.
 *
 * <p>This is the non-humanoid companion to {@link HumanoidGeoModel}: where that one
 * folds a whole-body model onto vanilla biped {@link net.minecraft.client.model.geom.ModelPart}s
 * so fur inherits a player's pose, this one keeps the model in its own Bedrock space and
 * bakes every bone's geometry into one flat quad list drawn wherever the caller places the
 * {@link PoseStack}. It is meant for props and equipment ported from GeckoLib mods (crates,
 * guns, mythical weapons) that have no vanilla skeleton to ride.
 *
 * <p>Coordinates stay in Bedrock convention: pixels, Y up, model origin at the entity's
 * feet. Bones are folded through their pivots and no axis is mirrored, so a face drawn here
 * matches the source file's orientation directly; {@link #draw} divides positions by 16 into
 * block units at emit time. Quads are emitted double-sided through the caller's render type,
 * which is why a mirrored source cube still shows both faces.
 *
 * <p>Rotated bones and cubes are supported through the same pivot-composed math the humanoid
 * baker uses, but with no mirror negation. A source whose rotated parts render the wrong way
 * is the one case to verify in world, since the Bedrock-to-Minecraft handedness of a rotation
 * cannot be checked from the file alone.
 */
public final class GeoModel {

    /** A ready-to-draw quad in Bedrock pixel space: four positions, UVs and a normal. */
    private record Quad(Vector3f[] positions, float[] u, float[] v, Vector3f normal) {}

    private static final Map<Identifier, GeoModel> CACHE = new HashMap<>();

    private final List<Quad> quads;

    private GeoModel(List<Quad> quads) {
        this.quads = quads;
    }

    /**
     * Reads, parses and bakes a Bedrock geo, caching the result by id.
     *
     * <p>Runs against the client resource manager, so it cannot be called before the client
     * is up; call it lazily on first draw. Returns null if the resource is missing or cannot
     * be parsed, leaving the caller to log and skip rather than crash the render.
     */
    public static GeoModel load(Identifier geo) {
        GeoModel cached = CACHE.get(geo);
        if (cached != null) {
            return cached;
        }
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(geo);
        if (resource.isEmpty()) {
            return null;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            GeoModel model = bake(GeoModelData.parse(json));
            CACHE.put(geo, model);
            return model;
        } catch (Exception exception) {
            return null;
        }
    }

    /** Bakes a parsed model, folding every bone's chain rotation into a flat quad list. */
    public static GeoModel bake(GeoModelData data) {
        Map<String, GeoModelData.Bone> byName = new HashMap<>();
        for (GeoModelData.Bone bone : data.bones()) {
            byName.put(bone.name(), bone);
        }
        List<Quad> quads = new ArrayList<>();
        for (GeoModelData.Bone bone : data.bones()) {
            Matrix4f boneMatrix = boneChain(bone, byName);
            for (GeoModelData.Cube cube : bone.cubes()) {
                bakeCube(cube, boneMatrix, data.textureWidth(), data.textureHeight(), quads);
            }
        }
        return new GeoModel(quads);
    }

    /** Emits every quad into the consumer under the caller's already-posed matrix. */
    public void draw(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        for (Quad quad : this.quads) {
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
     * The composed rotation a bone inherits from its own chain, or null when nothing is rotated.
     *
     * <p>Each ancestor's rotation is applied about its pivot and the product is ordered so the
     * outermost ancestor rotates last, mirroring the humanoid baker; unlike it, no axis is
     * negated, since this model is not turned to face the opposite way.
     */
    private static Matrix4f boneChain(GeoModelData.Bone bone, Map<String, GeoModelData.Bone> byName) {
        Matrix4f result = null;
        GeoModelData.Bone current = bone;
        while (current != null) {
            float[] r = current.rotation();
            if (r != null) {
                float[] p = current.pivot();
                Matrix4f link = new Matrix4f()
                        .translate(p[0], p[1], p[2])
                        .rotateZYX((float) Math.toRadians(r[2]),
                                (float) Math.toRadians(r[1]),
                                (float) Math.toRadians(r[0]))
                        .translate(-p[0], -p[1], -p[2]);
                result = result == null ? link : new Matrix4f(link).mul(result);
            }
            current = current.parent() == null ? null : byName.get(current.parent());
        }
        return result;
    }

    private static void bakeCube(GeoModelData.Cube cube, Matrix4f boneMatrix,
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
            uvFor(face, textureWidth, textureHeight, u, v);

            Vector3f[] positions = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                positions[i] = toModelSpace(corners[i], rotation, boneMatrix, cube);
            }
            out.add(new Quad(positions, u, v, faceNormal(direction, rotation, boneMatrix)));
        }
    }

    /** Applies the cube's own rotation about its pivot, then the bone chain, in Bedrock space. */
    private static Vector3f toModelSpace(Vector3f corner, Matrix4f rotation,
                                         Matrix4f boneMatrix, GeoModelData.Cube cube) {
        Vector3f p = new Vector3f(corner);
        if (rotation != null) {
            float[] cp = cube.pivot();
            p.sub(cp[0], cp[1], cp[2]);
            rotation.transformPosition(p);
            p.add(cp[0], cp[1], cp[2]);
        }
        if (boneMatrix != null) {
            boneMatrix.transformPosition(p);
        }
        return p;
    }

    /** The cube's rotation about its pivot, or null when it is axis aligned. */
    private static Matrix4f cubeRotation(GeoModelData.Cube cube) {
        float[] r = cube.rotation();
        if (r == null) {
            return null;
        }
        return new Matrix4f().rotateZYX(
                (float) Math.toRadians(r[2]),
                (float) Math.toRadians(r[1]),
                (float) Math.toRadians(r[0]));
    }

    private static Vector3f faceNormal(Direction direction, Matrix4f rotation, Matrix4f boneMatrix) {
        Vector3f normal = new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        if (rotation != null) {
            rotation.transformDirection(normal);
        }
        if (boneMatrix != null) {
            boneMatrix.transformDirection(normal);
        }
        return normal;
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

    private static void uvFor(GeoModelData.Face face, int textureWidth, int textureHeight,
                              float[] u, float[] v) {
        float u0 = face.u() / textureWidth;
        float v0 = face.v() / textureHeight;
        float u1 = (face.u() + face.uWidth()) / textureWidth;
        float v1 = (face.v() + face.vHeight()) / textureHeight;
        u[0] = u0;
        v[0] = v0;
        u[1] = u1;
        v[1] = v0;
        u[2] = u1;
        v[2] = v1;
        u[3] = u0;
        v[3] = v1;
    }

    /** The four corners of a face, ordered top-left, top-right, bottom-right, bottom-left. */
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
