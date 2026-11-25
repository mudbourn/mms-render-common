package info.mudbourn.mmsrendercommon.mixin.legacy;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Lets a legacy model point the game's model at the root part its subclass builds. */
@Mixin(Model.class)
public interface ModelAccessor {

    @Mutable
    @Accessor("root")
    void mms$setRoot(ModelPart root);

    @Mutable
    @Accessor("allParts")
    void mms$setAllParts(List<ModelPart> allParts);
}
