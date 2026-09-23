package info.mudbourn.mmsrendercommon.client.geo;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * A geo texture: one image, or a flipbook of frames shown in turn.
 *
 * <p>Written as a bare id, {@code "ns:textures/item/x.png"}, or as an object with
 * {@code texture}, {@code frames} and {@code frame_ticks} (default 1). A flipbook of
 * {@code frames} images reads {@code x_1.png} to {@code x_<frames>.png} beside the named path
 * and shows each for {@code frame_ticks} ticks, looping.
 */
public record GeoTexture(Identifier texture, int frames, int frameTicks) {

    private static final Codec<Integer> POSITIVE = Codec.intRange(1, Integer.MAX_VALUE);

    private static final Codec<GeoTexture> FLIPBOOK_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Identifier.CODEC.fieldOf("texture").forGetter(GeoTexture::texture),
                    POSITIVE.optionalFieldOf("frames", 1).forGetter(GeoTexture::frames),
                    POSITIVE.optionalFieldOf("frame_ticks", 1).forGetter(GeoTexture::frameTicks)
            ).apply(instance, GeoTexture::new)
    );

    public static final Codec<GeoTexture> CODEC = Codec.either(Identifier.CODEC, FLIPBOOK_CODEC).xmap(
            either -> either.map(id -> new GeoTexture(id, 1, 1), texture -> texture),
            texture -> texture.frames() == 1 ? Either.left(texture.texture()) : Either.right(texture)
    );

    /** The image to show at a tick count, such as the holder's age. */
    public Identifier at(long ticks) {
        if (this.frames == 1) {
            return this.texture;
        }
        int frame = (int) Math.floorMod(ticks / this.frameTicks, (long) this.frames) + 1;
        String path = this.texture.getPath();
        int dot = path.lastIndexOf('.');
        String framed = dot < 0
                ? path + "_" + frame
                : path.substring(0, dot) + "_" + frame + path.substring(dot);
        return this.texture.withPath(framed);
    }
}
