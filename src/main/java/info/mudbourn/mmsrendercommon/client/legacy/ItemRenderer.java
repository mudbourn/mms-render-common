package info.mudbourn.mmsrendercommon.client.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The 1.21 item renderer, kept as the value renderers pass to model calls; 26.3 draws items through the submit collector. */
public final class ItemRenderer {
    private static final ItemRenderer INSTANCE = new ItemRenderer();

    private ItemRenderer() {
    }

    public static ItemRenderer of(EntityRendererProvider.Context context) {
        return INSTANCE;
    }

    public static ItemRenderer of(Minecraft minecraft) {
        return INSTANCE;
    }
}
