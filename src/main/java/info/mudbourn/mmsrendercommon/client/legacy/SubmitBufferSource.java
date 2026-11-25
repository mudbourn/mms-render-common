package info.mudbourn.mmsrendercommon.client.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.LinkedHashMap;
import java.util.Map;

/** A buffer source over a submit collector: vertices are kept per render type and submitted as custom geometry on flush. */
public final class SubmitBufferSource implements MultiBufferSource {
    private final SubmitNodeCollector collector;
    private final Map<RenderType, RecordingVertexConsumer> batches = new LinkedHashMap<>();

    public SubmitBufferSource(SubmitNodeCollector collector) {
        this.collector = collector;
    }

    public SubmitNodeCollector collector() {
        return collector;
    }

    // The collector behind a buffer source, or null when the source is not one of these.
    public static SubmitNodeCollector collectorOf(MultiBufferSource source) {
        return source instanceof SubmitBufferSource submit ? submit.collector : null;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return batches.computeIfAbsent(type, key -> new RecordingVertexConsumer());
    }

    public void flush() {
        for (Map.Entry<RenderType, RecordingVertexConsumer> batch : batches.entrySet()) {
            RecordingVertexConsumer vertices = batch.getValue();
            if (!vertices.isEmpty()) {
                collector.submitCustomGeometry(new PoseStack(), batch.getKey(), (pose, consumer) -> vertices.replay(consumer));
            }
        }
        batches.clear();
    }

    // The two helpers the uproot rules call: the source for a collector, and its flush once the old render method returned.
    public static MultiBufferSource of(SubmitNodeCollector collector) {
        return new SubmitBufferSource(collector);
    }

    public static void flush(SubmitNodeCollector collector, MultiBufferSource source) {
        if (source instanceof SubmitBufferSource submit) {
            submit.flush();
        }
    }
}
