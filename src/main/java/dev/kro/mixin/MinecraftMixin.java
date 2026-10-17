package dev.kro.mixin;

import dev.kro.Kro;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No Click Delay clears the missed-swing lockout right before vanilla checks it. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"))
	private void kro$startAttack(CallbackInfoReturnable<Boolean> cir) {
		Kro kro = Kro.get();
		if (kro != null) {
			kro.noClickDelay.clear((Minecraft) (Object) this);
		}
	}
}
