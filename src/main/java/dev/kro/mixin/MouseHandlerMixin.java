package dev.kro.mixin;

import dev.kro.Kro;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	/** Attack clicks feed Aim Assist's "only while clicking"; side buttons can be module binds. */
	@Inject(method = "onButton", at = @At("HEAD"))
	private void kro$button(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		Kro kro = Kro.get();
		if (kro != null) {
			kro.keys.onMouse(info.button(), action);
		}
	}

	/** Aim Assist runs right after vanilla applies mouse movement, once per frame. */
	@Inject(method = "turnPlayer", at = @At("TAIL"))
	private void kro$turn(double movementTime, CallbackInfo ci) {
		Kro kro = Kro.get();
		if (kro != null) {
			kro.aimAssist.onFrame(movementTime);
		}
	}
}
