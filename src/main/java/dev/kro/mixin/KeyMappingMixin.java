package dev.kro.mixin;

import dev.kro.Kro;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sprint, Jump Reset, W Tap and Short Wind Charge act by holding or releasing the real keys, so
 * every vanilla rule (hunger, blindness, sneaking, using items, ground checks) still decides what
 * actually happens. Auto Hit Crystal hides the held right button while it places.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
	@Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
	private void kro$isDown(CallbackInfoReturnable<Boolean> cir) {
		Kro kro = Kro.get();
		Minecraft mc = Minecraft.getInstance();
		if (kro == null || mc == null || mc.options == null || mc.player == null) {
			return;
		}
		Object self = this;
		if (self == mc.options.keySprint && (kro.modules.sprint.enabled() || kro.wTap.resprinting()) && mc.screen == null) {
			cir.setReturnValue(true);
		} else if (self == mc.options.keyJump && (kro.jumpReset.forceJump() || kro.windCharge.forceJump())) {
			cir.setReturnValue(true);
		} else if (self == mc.options.keyUp && kro.wTap.releasing()) {
			cir.setReturnValue(false);
		} else if (self == mc.options.keyUse && kro.hitCrystal.holdsUse()) {
			cir.setReturnValue(false);
		}
	}
}
