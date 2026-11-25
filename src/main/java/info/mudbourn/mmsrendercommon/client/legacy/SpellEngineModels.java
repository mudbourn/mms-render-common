package info.mudbourn.mmsrendercommon.client.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Spell Engine's custom model call as 1.21 renderers make it; Spell Engine is reached by name and is not a dependency. */
public final class SpellEngineModels {
    private static MethodHandle render;
    private static boolean missing;

    private SpellEngineModels() {
    }

    // CustomModels.render(layer, itemRenderer, model, pose, buffers, light, seed) of 1.21; a source that is not backed by a collector draws nothing.
    public static void render(RenderType layer, ItemRenderer items, Identifier model, PoseStack pose, MultiBufferSource buffers, int light, int seed) {
        SubmitNodeCollector collector = SubmitBufferSource.collectorOf(buffers);
        MethodHandle handle = handle();
        if (collector == null || handle == null) {
            return;
        }
        try {
            handle.invokeExact(layer, model, pose, collector, light, seed);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Spell Engine could not draw " + model, e);
        }
    }

    private static MethodHandle handle() {
        if (render == null && !missing) {
            try {
                Class<?> models = Class.forName("net.spell_engine.api.render.CustomModels");
                render = MethodHandles.publicLookup().findStatic(
                        models,
                        "render",
                        MethodType.methodType(void.class, RenderType.class, Identifier.class, PoseStack.class, SubmitNodeCollector.class, int.class, int.class)
                );
            } catch (ReflectiveOperationException e) {
                missing = true;
            }
        }
        return render;
    }
}
