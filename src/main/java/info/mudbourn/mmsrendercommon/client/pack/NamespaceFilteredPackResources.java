package info.mudbourn.mmsrendercommon.client.pack;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** A pack that shows only some client asset namespaces of another pack. */
final class NamespaceFilteredPackResources implements PackResources {

    private final PackResources delegate;
    private final Set<String> namespaces;

    NamespaceFilteredPackResources(PackResources delegate, Set<String> namespaces) {
        this.delegate = delegate;
        this.namespaces = namespaces;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        return shows(type, location.getNamespace()) ? this.delegate.getResource(type, location) : null;
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        if (shows(type, namespace)) {
            this.delegate.listResources(type, namespace, directory, output);
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) {
            return Set.of();
        }
        Set<String> shown = new HashSet<>(this.delegate.getNamespaces(type));
        shown.retainAll(this.namespaces);
        return shown;
    }

    @Override
    public PackLocationInfo location() {
        return this.delegate.location();
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return this.delegate.getRootResource(path);
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
        return this.delegate.getMetadataSection(type);
    }

    @Override
    public void close() {
        this.delegate.close();
    }

    private boolean shows(PackType type, String namespace) {
        return type == PackType.CLIENT_RESOURCES && this.namespaces.contains(namespace);
    }
}
