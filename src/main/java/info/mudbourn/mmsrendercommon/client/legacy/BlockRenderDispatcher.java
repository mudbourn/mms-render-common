package info.mudbourn.mmsrendercommon.client.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** The 1.21 block render dispatcher, answering the calls that draw one block model into a buffer source. */
public final class BlockRenderDispatcher {
    private static final BlockRenderDispatcher INSTANCE = new BlockRenderDispatcher();

    private BlockRenderDispatcher() {
    }

    public static BlockRenderDispatcher of(EntityRendererProvider.Context context) {
        return INSTANCE;
    }

    public static BlockRenderDispatcher of(Minecraft minecraft) {
        return INSTANCE;
    }

    // Draws the state's model untinted, as a moving block is. A source that is not backed by a collector draws nothing.
    public void renderSingleBlock(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        SubmitNodeCollector collector = SubmitBufferSource.collectorOf(buffers);
        if (collector == null) {
            return;
        }
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        collector.submitBlockModel(pose, RenderTypes.cutoutMovingBlock(), parts, new int[] {ARGB.colorFromFloat(1.0F, 1.0F, 1.0F, 1.0F)}, light, overlay, 0);
    }
}
