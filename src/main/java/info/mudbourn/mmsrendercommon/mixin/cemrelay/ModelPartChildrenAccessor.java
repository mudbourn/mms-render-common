package info.mudbourn.mmsrendercommon.mixin.cemrelay;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Exposes {@link ModelPart}'s child map so a part tree can be walked by name.
 *
 * <p>{@code ModelPart} offers {@code getChild(String)} and {@code getAllParts()},
 * but neither is enough here: {@code getChild} only reaches direct children, and
 * {@code getAllParts} loses the names. {@code addAllChildren(BiConsumer)} would
 * be exactly right but is private. The backing {@code children} map is the only
 * way to recover the name-to-part tree, which
 * {@link info.mudbourn.mmsrendercommon.client.CemLayerPoseRelay} needs in order to
 * accumulate a part's transform down from the model root.
 */
@Mixin(ModelPart.class)
public interface ModelPartChildrenAccessor {

    @Accessor("children")
    Map<String, ModelPart> mms$children();
}
