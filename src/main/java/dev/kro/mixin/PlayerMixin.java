package dev.kro.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.kro.Kro;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Reach: your own entity reach, as the crosshair uses it. Other players are never touched. */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@ModifyReturnValue(method = "entityInteractionRange", at = @At("RETURN"))
	private double kro$reach(double range) {
		Kro kro = Kro.get();
		return kro != null && (Object) this instanceof LocalPlayer ? kro.reach.range(range) : range;
	}
}
