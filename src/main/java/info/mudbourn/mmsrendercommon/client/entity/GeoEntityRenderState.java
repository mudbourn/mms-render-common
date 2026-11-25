package info.mudbourn.mmsrendercommon.client.entity;

import info.mudbourn.mmsrendercommon.client.anim.GeoContext;
import info.mudbourn.mmsrendercommon.client.geo.BonePose;
import info.mudbourn.mmsrendercommon.client.geo.GeoModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

import java.util.HashMap;
import java.util.Map;

/** A geo entity as one frame draws it: its definition, model and posed bones. */
public class GeoEntityRenderState extends LivingEntityRenderState {

    /** The definition, or null when its file is missing or invalid. */
    public GeoEntityDefinition definition;

    /** The baked geo, or null when it is missing or invalid. */
    public GeoModel model;

    /** This frame's bone poses, keyed by bone name. */
    public Map<String, BonePose> poses = Map.of();

    /** The entity as attachments see it. */
    public GeoContext context;

    /** Ticks the entity has lived, for flipbook textures. */
    public long ticks;

    /** The items drawn on bones, keyed by bone name. */
    public final Map<String, ItemStackRenderState> heldItems = new HashMap<>();
}
