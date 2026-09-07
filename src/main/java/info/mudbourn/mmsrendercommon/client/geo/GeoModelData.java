package info.mudbourn.mmsrendercommon.client.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed Bedrock {@code geo.json} model, as authored by Blockbench.
 *
 * <p>This is the raw geometry, not a renderable: bones still carry Bedrock-space
 * pivots and per-cube rotations, and cubes still carry per-face UVs and planar
 * (zero-thickness) sizes. Vanilla's {@code CubeListBuilder} can express none of
 * those, which is why the ported Origin Furs models are read through this and drawn
 * by {@code GeoRenderer} rather than transcribed into {@code MeshDefinition}s.
 *
 * <p>Coordinates are left in Bedrock space here (Y up, model origin at the entity's
 * feet, degrees for rotation). The conversion to render space is the baker's job, so
 * that this stays a faithful mirror of the source file and the one place the
 * convention is applied is the one place it can be checked.
 */
public record GeoModelData(int textureWidth, int textureHeight, List<Bone> bones) {

    /**
     * One Bedrock bone.
     *
     * @param parent the parent bone name, or null for a root bone
     * @param pivot  the bone's pivot in Bedrock space; rotations happen about it
     * @param rotation the bone's own rotation in degrees, or null for none
     */
    public record Bone(String name,
                       String parent,
                       float[] pivot,
                       float[] rotation,
                       List<Cube> cubes) {}

    /**
     * One cube. A cube with a zero component in {@link #size} is a single-quad card,
     * which the source uses heavily for fins, ears and flat faces.
     *
     * @param pivot    the cube's own rotation pivot, or null to rotate about the bone
     * @param rotation the cube's rotation in degrees, or null for an axis-aligned box
     * @param inflate  uniform outward grow applied to every face, in pixels
     */
    public record Cube(float[] origin,
                       float[] size,
                       float[] pivot,
                       float[] rotation,
                       float inflate,
                       boolean mirror,
                       Uv uv) {}

    /**
     * A cube's texture mapping. Bedrock allows either a single box origin that lays
     * the six faces out by vanilla's own rule, or an explicit per-face map that names
     * only the faces the cube actually draws.
     */
    public sealed interface Uv permits BoxUv, PerFaceUv {}

    /** Box UV: the face layout is derived from this origin and the cube size. */
    public record BoxUv(float u, float v) implements Uv {}

    /** Per-face UV: only the named faces are drawn. */
    public record PerFaceUv(Map<Direction, Face> faces) implements Uv {}

    /** One face's UV rectangle. A negative extent mirrors that axis, as in Bedrock. */
    public record Face(float u, float v, float uWidth, float vHeight) {}

    /** Parses a Blockbench Bedrock geometry document, taking its first geometry. */
    public static GeoModelData parse(JsonObject root) {
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");

        int textureWidth = description.get("texture_width").getAsInt();
        int textureHeight = description.get("texture_height").getAsInt();

        List<Bone> bones = new ArrayList<>();
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            bones.add(parseBone(element.getAsJsonObject()));
        }
        return new GeoModelData(textureWidth, textureHeight, bones);
    }

    private static Bone parseBone(JsonObject json) {
        String name = json.get("name").getAsString();
        String parent = json.has("parent") ? json.get("parent").getAsString() : null;
        float[] pivot = readVec(json, "pivot", new float[] {0, 0, 0});
        float[] rotation = json.has("rotation") ? readVec(json, "rotation", null) : null;

        List<Cube> cubes = new ArrayList<>();
        if (json.has("cubes")) {
            for (JsonElement element : json.getAsJsonArray("cubes")) {
                cubes.add(parseCube(element.getAsJsonObject()));
            }
        }
        return new Bone(name, parent, pivot, rotation, cubes);
    }

    private static Cube parseCube(JsonObject json) {
        float[] origin = readVec(json, "origin", new float[] {0, 0, 0});
        float[] size = readVec(json, "size", new float[] {0, 0, 0});
        float[] pivot = json.has("pivot") ? readVec(json, "pivot", null) : null;
        float[] rotation = json.has("rotation") ? readVec(json, "rotation", null) : null;
        float inflate = json.has("inflate") ? json.get("inflate").getAsFloat() : 0.0F;
        boolean mirror = json.has("mirror") && json.get("mirror").getAsBoolean();
        return new Cube(origin, size, pivot, rotation, inflate, mirror, parseUv(json.get("uv")));
    }

    private static Uv parseUv(JsonElement uv) {
        if (uv.isJsonArray()) {
            JsonArray array = uv.getAsJsonArray();
            return new BoxUv(array.get(0).getAsFloat(), array.get(1).getAsFloat());
        }
        Map<Direction, Face> faces = new EnumMap<>(Direction.class);
        for (Map.Entry<String, JsonElement> entry : uv.getAsJsonObject().entrySet()) {
            Direction direction = directionOf(entry.getKey());
            JsonObject face = entry.getValue().getAsJsonObject();
            float[] corner = readVec2(face.getAsJsonArray("uv"));
            float[] extent = readVec2(face.getAsJsonArray("uv_size"));
            faces.put(direction, new Face(corner[0], corner[1], extent[0], extent[1]));
        }
        return new PerFaceUv(faces);
    }

    /** Bedrock face names to vanilla directions. */
    private static Direction directionOf(String name) {
        return switch (name) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            default -> throw new IllegalArgumentException("Unknown Bedrock face: " + name);
        };
    }

    private static float[] readVec(JsonObject json, String key, float[] fallback) {
        if (!json.has(key)) {
            return fallback;
        }
        JsonArray array = json.getAsJsonArray(key);
        return new float[] {
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat()
        };
    }

    private static float[] readVec2(JsonArray array) {
        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat()};
    }
}
