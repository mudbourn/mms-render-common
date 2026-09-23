package info.mudbourn.mmsrendercommon.client;

import info.mudbourn.mmsrendercommon.client.anim.GeoAnimations;
import info.mudbourn.mmsrendercommon.client.geo.GeoAssets;
import info.mudbourn.mmsrendercommon.trigger.GeoTriggerPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Client entrypoint: receives geo triggers and clears cached geo assets on resource reload. */
public final class MmsRenderCommonClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                GeoTriggerPayload.TYPE,
                (payload, context) -> GeoAnimations.receive(payload)
        );
        ResourceManagerReloadListener clearAssets = resources -> GeoAssets.clear();
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(
                Identifier.fromNamespaceAndPath("mms_render_common", "geo_assets"),
                clearAssets
        );
    }
}
