package info.mudbourn.mmsrendercommon.mixin.sourcepack;

import com.google.common.collect.ImmutableSet;
import info.mudbourn.mmsrendercommon.client.pack.SourcePacks;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** Adds the installed-jar source packs to the client pack repository. */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mms$addSourcePacks(RepositorySource[] sources, CallbackInfo ci) {
        if (this.sources.stream().anyMatch(ClientPackSource.class::isInstance)) {
            this.sources = ImmutableSet.<RepositorySource>builder()
                    .addAll(this.sources)
                    .add(SourcePacks.INSTANCE)
                    .build();
        }
    }
}
