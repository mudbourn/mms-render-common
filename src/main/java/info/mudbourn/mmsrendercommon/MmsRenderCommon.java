package info.mudbourn.mmsrendercommon;

import info.mudbourn.mmsrendercommon.trigger.GeoTriggerPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Common entrypoint: registers the geo trigger payload on both sides. */
public final class MmsRenderCommon implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(GeoTriggerPayload.TYPE, GeoTriggerPayload.CODEC);
    }
}
