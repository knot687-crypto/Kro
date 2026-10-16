package dev.kro.mixin;

import dev.kro.Kro;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes in-game key presses to Kro's binds before vanilla sees them. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void kro$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		Kro kro = Kro.get();
		if (kro != null && kro.keys.onKey(event.key(), action)) {
			ci.cancel();
		}
	}
}
