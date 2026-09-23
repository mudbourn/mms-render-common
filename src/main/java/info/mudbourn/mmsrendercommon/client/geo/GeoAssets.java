package info.mudbourn.mmsrendercommon.client.geo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client resource cache for geo models, animation files and JSON definitions, shared by every
 * geo item and armor that names the same file.
 *
 * <p>Each file is read on first use and kept until the next resource reload, so any number of
 * item model variants over one geo parse it once. A file that is missing or fails to parse is
 * logged once and then reads as absent until the next reload. Call from the render thread.
 */
public final class GeoAssets {

    private static final Logger LOGGER = LoggerFactory.getLogger("mms_render_common");

    private static final Map<Identifier, Optional<GeoModel>> MODELS = new HashMap<>();
    private static final Map<Identifier, Map<String, GeoAnimation>> ANIMATIONS = new HashMap<>();
    private static final Map<Codec<?>, Map<Identifier, Optional<?>>> DEFINITIONS = new HashMap<>();

    private GeoAssets() {
    }

    /** The baked geo at a resource path such as {@code ns:geo/item/x.geo.json}, or null. */
    public static GeoModel model(Identifier geo) {
        return MODELS.computeIfAbsent(geo, id -> {
            try {
                return Optional.of(GeoModel.read(resources(), id));
            } catch (Exception exception) {
                LOGGER.warn("Could not load geo {}", id, exception);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** Every animation in an animation file, keyed by name; empty when the file cannot be read. */
    public static Map<String, GeoAnimation> animations(Identifier file) {
        return ANIMATIONS.computeIfAbsent(file, id -> {
            try {
                return GeoAnimation.parseAll(readJson(resources(), id).getAsJsonObject());
            } catch (Exception exception) {
                LOGGER.warn("Could not load animations {}", id, exception);
                return Map.of();
            }
        });
    }

    /** A JSON resource decoded with a codec, or null when it is missing or invalid. */
    @SuppressWarnings("unchecked")
    public static <T> T definition(Identifier file, Codec<T> codec) {
        Map<Identifier, Optional<?>> byFile = DEFINITIONS.computeIfAbsent(codec, key -> new HashMap<>());
        return (T) byFile.computeIfAbsent(file, id -> {
            try {
                return codec.parse(JsonOps.INSTANCE, readJson(resources(), id))
                        .resultOrPartial(error -> LOGGER.warn("Invalid definition {}: {}", id, error));
            } catch (Exception exception) {
                LOGGER.warn("Could not load definition {}", id, exception);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** Drops everything cached; called on every client resource reload. */
    public static void clear() {
        MODELS.clear();
        ANIMATIONS.clear();
        DEFINITIONS.clear();
    }

    static JsonObject readObject(ResourceManager resources, Identifier id) throws IOException {
        return readJson(resources, id).getAsJsonObject();
    }

    private static JsonElement readJson(ResourceManager resources, Identifier id) throws IOException {
        Optional<Resource> resource = resources.getResource(id);
        if (resource.isEmpty()) {
            throw new FileNotFoundException(id.toString());
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            return JsonParser.parseReader(reader);
        }
    }

    private static ResourceManager resources() {
        return Minecraft.getInstance().getResourceManager();
    }
}
