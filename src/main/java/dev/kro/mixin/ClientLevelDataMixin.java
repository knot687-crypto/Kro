package dev.kro.mixin;

import dev.kro.Kro;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** World Theme: a client-side clock override. */
@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientLevelDataMixin {
	@Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
	private void kro$themeTime(CallbackInfoReturnable<Long> cir) {
		Kro kro = Kro.get();
		if (kro != null && kro.worldTheme.active()) {
			cir.setReturnValue(kro.worldTheme.dayTime(cir.getReturnValue()));
		}
	}
}
