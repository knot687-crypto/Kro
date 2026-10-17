package dev.kro.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kro.Kro;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smaller Totem: a totem held in first person is drawn at a reduced size, anchored in the hand. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Unique
	private boolean kro$scaled;

	@Inject(method = "renderItem", at = @At("HEAD"))
	private void kro$smallTotem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		kro$scaled = false;
		Kro kro = Kro.get();
		if (kro == null || !kro.modules.smallerTotem.enabled() || !context.firstPerson() || stack.isEmpty() || !stack.is(Items.TOTEM_OF_UNDYING)) {
			return;
		}
		float s = kro.modules.smallerTotem.heldSize.floatValue() / 100f;
		if (s < 0.999f) {
			poseStack.pushPose();
			poseStack.scale(s, s, s);
			kro$scaled = true;
		}
	}

	@Inject(method = "renderItem", at = @At("RETURN"))
	private void kro$smallTotemDone(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (kro$scaled) {
			poseStack.popPose();
			kro$scaled = false;
		}
	}
}
