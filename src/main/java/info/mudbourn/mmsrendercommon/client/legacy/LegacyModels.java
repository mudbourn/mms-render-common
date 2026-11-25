package info.mudbourn.mmsrendercommon.client.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

import java.util.stream.Stream;

/** Small adapters for model calls whose shape changed between 1.21 and 26.3; the uproot rules point the old calls here. */
public final class LegacyModels {

    private LegacyModels() {
    }

    // ModelPart.getAllParts answered a stream in 1.21.
    public static Stream<ModelPart> allParts(ModelPart part) {
        return part.getAllParts().stream();
    }

    // Model.renderToBuffer without a colour drew untinted.
    public static void renderToBuffer(Model<?> model, PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        model.renderToBuffer(pose, buffer, light, overlay, -1);
    }

    // The arguments of 26.3's Keyframe constructor for a 1.21 one, which took a mutable vector.
    public static Object[] keyframeArgs(float timestamp, Vector3f target, AnimationChannel.Interpolation interpolation) {
        return new Object[] {timestamp, target, interpolation};
    }
}
