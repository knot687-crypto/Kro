package dev.kro.mixin;

import dev.kro.Kro;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes your attacks for the Target HUD, Hit Sparks and kill credit for Kill Effects. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void kro$attack(Player player, Entity target, CallbackInfo ci) {
		Kro kro = Kro.get();
		if (kro != null) {
			kro.targets.onAttack(target);
			kro.hitSparks.onAttack(target);
			kro.effects.onAttack(target);
		}
	}
}
