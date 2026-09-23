package info.mudbourn.mmsrendercommon.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Draws something on a bone of a geo item or armor piece, such as the item a pair of tongs holds.
 *
 * <p>Registered with {@link GeoAnimations#registerAttachment} and named per bone in a
 * definition's {@code attachments} map, {@code {"bone_name": "ns:attachment"}}. It is called after
 * the geo is submitted, with the pose stack at the bone's pivot, turned and moved with the bone,
 * in block units.
 */
@FunctionalInterface
public interface GeoAttachment {

    /** Submits whatever sits on the bone. */
    void submit(GeoContext context, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay);
}
