package info.mudbourn.mmsrendercommon.duck;

/**
 * The id of the entity a living render state was extracted from, so geo armor can find its
 * wearer. Reads -1 until a state is first extracted.
 */
public interface GeoHolderDuck {

    int mmsRenderCommon$geoHolderId();

    void mmsRenderCommon$setGeoHolderId(int id);
}
