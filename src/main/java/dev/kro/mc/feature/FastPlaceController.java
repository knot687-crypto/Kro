package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.core.module.impl.FastPlace;
import dev.kro.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Fast Place: while right click is held, vanilla waits four ticks between uses. Each time a use
 * goes out a shorter wait is picked, and the countdown is cut to it at the start of a tick, before
 * vanilla counts it down; every use is still vanilla's own, so placement rules are untouched.
 */
public final class FastPlaceController {
	private final Kro kro;
	private int target = 4;
	private int last;

	public FastPlaceController(Kro kro) {
		this.kro = kro;
	}

	public void tick(Minecraft mc) {
		FastPlace m = kro.modules.fastPlace;
		LocalPlayer player = mc.player;
		if (!m.enabled() || player == null || mc.screen != null) {
			return;
		}
		MinecraftAccessor timing = (MinecraftAccessor) mc;
		int delay = timing.kro$rightClickDelay();
		if (delay > last) {
			// a use just went out: pick the wait before the next one
			target = (int) Math.round(m.delay.random());
		}
		if (delay > target && (qualifies(m, player.getMainHandItem()) || qualifies(m, player.getOffhandItem()))) {
			timing.kro$setRightClickDelay(target);
			delay = target;
		}
		last = delay;
	}

	private static boolean qualifies(FastPlace m, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(Items.END_CRYSTAL)) {
			return m.crystals.on();
		}
		return stack.getItem() instanceof BlockItem ? m.blocks.on() : m.other.on();
	}
}
