package dev.kro.mixin;

import dev.kro.Kro;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** World Theme: sky and cloud tints. Optional hooks; the theme degrades gracefully without them. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "getSkyColor(Lnet/minecraft/world/phys/Vec3;F)I", at = @At("RETURN"), cancellable = true, require = 0)
	private void kro$skyAt(CallbackInfoReturnable<Integer> cir) {
		Kro kro = Kro.get();
		if (kro != null && kro.worldTheme.active()) {
			cir.setReturnValue(kro.worldTheme.sky(cir.getReturnValue()));
		}
	}

	@Inject(method = "getSkyColor(Lnet/minecraft/client/Camera;F)I", at = @At("RETURN"), cancellable = true, require = 0)
	private void kro$skyCamera(CallbackInfoReturnable<Integer> cir) {
		Kro kro = Kro.get();
		if (kro != null && kro.worldTheme.active()) {
			cir.setReturnValue(kro.worldTheme.sky(cir.getReturnValue()));
		}
	}

	@Inject(method = "getCloudColor(F)I", at = @At("RETURN"), cancellable = true, require = 0)
	private void kro$cloud(CallbackInfoReturnable<Integer> cir) {
		Kro kro = Kro.get();
		if (kro != null && kro.worldTheme.active()) {
			cir.setReturnValue(kro.worldTheme.cloud(cir.getReturnValue()));
		}
	}

	@Inject(method = "getCloudColor(FLnet/minecraft/client/Camera;)I", at = @At("RETURN"), cancellable = true, require = 0)
	private void kro$cloudCamera(CallbackInfoReturnable<Integer> cir) {
		Kro kro = Kro.get();
		if (kro != null && kro.worldTheme.active()) {
			cir.setReturnValue(kro.worldTheme.cloud(cir.getReturnValue()));
		}
	}
}
