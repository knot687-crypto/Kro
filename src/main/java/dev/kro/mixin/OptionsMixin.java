package dev.kro.mixin;

import dev.kro.Kro;
import dev.kro.mc.screen.KroScreen;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses Kro's own blur strength behind the menu. */
@Mixin(Options.class)
public abstract class OptionsMixin {
	@Inject(method = "getMenuBackgroundBlurriness", at = @At("HEAD"), cancellable = true, require = 0)
	private void kro$blur(CallbackInfoReturnable<Integer> cir) {
		Kro kro = Kro.get();
		if (kro != null && KroScreen.isOpen()) {
			cir.setReturnValue(kro.modules.ui.blur.intValue());
		}
	}
}
