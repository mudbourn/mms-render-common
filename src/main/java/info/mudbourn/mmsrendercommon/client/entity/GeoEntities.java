package info.mudbourn.mmsrendercommon.client.entity;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** Registers entity types to draw as geo entities. */
public final class GeoEntities {

    private GeoEntities() {
    }

    /**
     * Draws an entity type as the geo entity definition at
     * {@code assets/<ns>/geo_entity/<path>.json}. Call from a client initializer.
     */
    public static <T extends LivingEntity> void register(EntityType<? extends T> type, Identifier definition) {
        EntityRenderers.register(type, context -> new GeoEntityRenderer<T>(context, definition));
    }
}
