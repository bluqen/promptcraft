package dev.promptcraft.mixin;

import dev.promptcraft.GeneratedPack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/** Adds the generated pack to every pack repository (client resources and server data). */
@Mixin(PackRepository.class)
public class PackRepositoryMixin {
	@Shadow @Final @Mutable private Set<RepositorySource> sources;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void promptcraft$addGeneratedPack(RepositorySource[] repositorySources, CallbackInfo ci) {
		Set<RepositorySource> withOurs = new LinkedHashSet<>(this.sources);
		withOurs.add(GeneratedPack.SOURCE);
		this.sources = withOurs;
	}
}
