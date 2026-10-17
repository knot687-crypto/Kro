package dev.kro.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.kro.Kro;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Reach: the entity reach your crosshair picks targets with. Only this one use is lengthened, so
 * block reach, interactions and other players are untouched, and weapons that carry their own
 * attack range (spears) keep it.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@ModifyExpressionValue(method = "raycastHitResult", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;entityInteractionRange()D"))
	private double kro$reach(double range) {
		Kro kro = Kro.get();
		return kro == null ? range : kro.reach.range(range);
	}
}
