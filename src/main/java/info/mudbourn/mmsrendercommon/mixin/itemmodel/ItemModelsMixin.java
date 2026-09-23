package info.mudbourn.mmsrendercommon.mixin.itemmodel;

import com.mojang.serialization.MapCodec;
import info.mudbourn.mmsrendercommon.client.item.GeoItemModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers the {@code mms_render_common:geo} item model type alongside vanilla's. */
@Mixin(ItemModels.class)
public abstract class ItemModelsMixin {

    @Shadow
    @Final
    private static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>> ID_MAPPER;

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void mms$registerGeo(CallbackInfo ci) {
        ID_MAPPER.put(GeoItemModel.TYPE, GeoItemModel.Unbaked.MAP_CODEC);
    }
}
