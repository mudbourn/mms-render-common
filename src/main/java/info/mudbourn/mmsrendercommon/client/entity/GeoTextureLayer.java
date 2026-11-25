package info.mudbourn.mmsrendercommon.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import info.mudbourn.mmsrendercommon.client.geo.BonePose;
import info.mudbourn.mmsrendercommon.client.geo.GeoModel;
import info.mudbourn.mmsrendercommon.client.geo.GeoTexture;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

import java.util.Map;

/** Draws a geo entity's model with its texture, or full bright with its emissive texture. */
public class GeoTextureLayer extends GeoEntityLayer {

    private static final int INVISIBLE_COLOR = 0x26FFFFFF;

    private final boolean emissive;

    public GeoTextureLayer(RenderLayerParent<GeoEntityRenderState, EntityModel<GeoEntityRenderState>> parent,
                           boolean emissive) {
        super(parent);
        this.emissive = emissive;
    }

    @Override
    protected void submitGeo(PoseStack poseStack, SubmitNodeCollector collector, int light,
                             GeoEntityRenderState state) {
        GeoEntityDefinition definition = state.definition;
        RenderType renderType;
        int color = -1;
        if (this.emissive) {
            if (state.isInvisible || definition.emissiveTexture().isEmpty()) {
                return;
            }
            renderType = RenderTypes.eyes(definition.emissiveTexture().get().at(state.ticks));
            light = LightCoordsUtil.FULL_BRIGHT;
        } else {
            Identifier texture = definition.texture().at(state.ticks);
            if (state.isInvisible) {
                if (state.isInvisibleToPlayer) {
                    return;
                }
                renderType = RenderTypes.entityTranslucent(texture);
                color = INVISIBLE_COLOR;
            } else {
                renderType = definition.translucent()
                        ? RenderTypes.entityTranslucent(texture)
                        : RenderTypes.entityCutout(texture);
            }
        }
        submit(poseStack, collector, renderType, state, light, color);
    }

    private static void submit(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType,
                               GeoEntityRenderState state, int light, int color) {
        GeoModel model = state.model;
        Map<String, BonePose> poses = state.poses;
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        collector.submitCustomGeometry(
                poseStack,
                renderType,
                (pose, consumer) -> model.draw(pose, consumer, light, overlay, color, poses)
        );
    }

    /** Draws the base texture. */
    public static GeoTextureLayer base(RenderLayerParent<GeoEntityRenderState, EntityModel<GeoEntityRenderState>> parent) {
        return new GeoTextureLayer(parent, false);
    }

    /** Draws the emissive texture, if the definition has one. */
    public static GeoTextureLayer glow(RenderLayerParent<GeoEntityRenderState, EntityModel<GeoEntityRenderState>> parent) {
        return new GeoTextureLayer(parent, true);
    }
}
