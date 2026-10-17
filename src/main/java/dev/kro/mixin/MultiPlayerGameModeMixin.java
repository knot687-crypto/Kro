package dev.kro.mixin;

import dev.kro.Kro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Your attacks, just before and just after they leave the client: the Target HUD, Hit Sparks,
 * kill credit, W Tap and Reach watch them, and Auto Mace and Breach Swap put the mace in hand for
 * the blow and take it away again in the same instant.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void kro$attack(Player player, Entity target, CallbackInfo ci) {
		Kro kro = Kro.get();
		if (kro != null) {
			Minecraft mc = Minecraft.getInstance();
			kro.targets.onAttack(target);
			kro.hitSparks.onAttack(target);
			kro.effects.onAttack(target);
			kro.wTap.onAttack(mc, target);
			kro.reach.onAttack();
			kro.maceSwap.beforeAttack(mc, player, target);
		}
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void kro$attacked(Player player, Entity target, CallbackInfo ci) {
		Kro kro = Kro.get();
		if (kro != null) {
			kro.maceSwap.afterAttack(Minecraft.getInstance());
		}
	}
}
