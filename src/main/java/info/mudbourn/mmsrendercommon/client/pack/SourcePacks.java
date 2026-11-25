package info.mudbourn.mmsrendercommon.client.pack;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlagSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Client resource packs read from mod jars the player installs but Fabric does not load, such as
 * a NeoForge mod whose art a rewrite draws without shipping it.
 *
 * <p>A consumer names the asset namespaces it reads this way with {@link #accept}. Each resource
 * reload scans {@code config/mms_render_common/sources/} for jars and zips, and {@code mods/} for
 * jars that are not Fabric or Quilt mods, and adds every one holding an accepted namespace under
 * {@code assets/} as a required pack at the bottom of the pack list. A pack exposes only the
 * accepted namespaces, so a jar never replaces vanilla or other mods' assets, and it needs no
 * {@code pack.mcmeta}.
 */
public final class SourcePacks implements RepositorySource {

    private static final Logger LOGGER = LoggerFactory.getLogger("mms_render_common");

    /** The source added to the client pack repository. */
    public static final SourcePacks INSTANCE = new SourcePacks();

    private static final Set<String> NAMESPACES = ConcurrentHashMap.newKeySet();

    private SourcePacks() {
    }

    /** Reads an asset namespace from installed jars that hold it. Call from a client initializer. */
    public static void accept(String namespace) {
        NAMESPACES.add(namespace);
    }

    /** The folder scanned for source jars besides {@code mods/}. */
    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("mms_render_common").resolve("sources");
    }

    @Override
    public void loadPacks(Consumer<Pack> result) {
        if (NAMESPACES.isEmpty()) {
            return;
        }
        List<Path> candidates = new ArrayList<>();
        try {
            Files.createDirectories(folder());
            candidates.addAll(archives(folder(), false));
            candidates.addAll(archives(FabricLoader.getInstance().getGameDir().resolve("mods"), true));
        } catch (IOException exception) {
            LOGGER.warn("Could not scan for source jars", exception);
        }
        for (Path jar : candidates) {
            Set<String> namespaces = acceptedNamespaces(jar);
            if (!namespaces.isEmpty()) {
                result.accept(pack(jar, namespaces));
                LOGGER.info("Reading {} from {}", namespaces, jar.getFileName());
            }
        }
    }

    private static List<Path> archives(Path folder, boolean skipFabricMods) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> {
                        String name = file.getFileName().toString();
                        return name.endsWith(".jar") || !skipFabricMods && name.endsWith(".zip");
                    })
                    .filter(file -> !skipFabricMods || !isFabricMod(file))
                    .sorted()
                    .toList();
        }
    }

    private static boolean isFabricMod(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry("fabric.mod.json") != null || zip.getEntry("quilt.mod.json") != null;
        } catch (IOException exception) {
            return true;
        }
    }

    private static Set<String> acceptedNamespaces(Path jar) {
        Set<String> found = new HashSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String[] parts = entries.nextElement().getName().split("/", 3);
                if (parts.length == 3 && parts[0].equals("assets") && NAMESPACES.contains(parts[1])) {
                    found.add(parts[1]);
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not read source jar {}", jar, exception);
        }
        return found;
    }

    private static Pack pack(Path jar, Set<String> namespaces) {
        String name = jar.getFileName().toString();
        PackLocationInfo location = new PackLocationInfo(
                "mms_render_common/source/" + name,
                Component.literal(name),
                PackSource.BUILT_IN,
                Optional.empty()
        );
        Pack.Metadata metadata = new Pack.Metadata(
                Component.literal("Assets read from an installed jar"),
                PackCompatibility.COMPATIBLE,
                FeatureFlagSet.of(),
                List.of()
        );
        return new Pack(
                location,
                new Supplier(jar, Set.copyOf(namespaces)),
                metadata,
                new PackSelectionConfig(true, Pack.Position.BOTTOM, false)
        );
    }

    /** Opens a jar as a pack limited to some namespaces. */
    private record Supplier(Path jar, Set<String> namespaces) implements Pack.ResourcesSupplier {

        @Override
        public PackMetadataResources openMetadata(PackLocationInfo location) {
            return new FilePackResources.FileResourcesSupplier(this.jar).openMetadata(location);
        }

        @Override
        public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata metadata) {
            return new FilePackResources.FileResourcesSupplier(this.jar)
                    .openResources(location, metadata)
                    .map(resources -> new NamespaceFilteredPackResources(resources, this.namespaces));
        }
    }
}
