package info.mudbourn.mmsrendercommon.mixin.geoholder;

import info.mudbourn.mmsrendercommon.duck.GeoHolderDuck;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Storage for {@link GeoHolderDuck} on every living render state. */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements GeoHolderDuck {

    @Unique
    private int mmsRenderCommon$geoHolderId = -1;

    @Override
    public int mmsRenderCommon$geoHolderId() {
        return this.mmsRenderCommon$geoHolderId;
    }

    @Override
    public void mmsRenderCommon$setGeoHolderId(int id) {
        this.mmsRenderCommon$geoHolderId = id;
    }
}
