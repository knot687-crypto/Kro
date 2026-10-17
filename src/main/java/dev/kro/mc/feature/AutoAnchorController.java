package dev.kro.mc.feature;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kro.Kro;
import dev.kro.core.module.impl.AutoAnchor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Respawn anchor play, one human-sized step per tick: switch to glowstone, charge, switch to the
 * detonating item, set it off, then switch back. Every swap is an ordinary hotbar selection and
 * every press goes through vanilla's use key, so the anchor's own rules decide what happens.
 */
public final class AutoAnchorController {
	/** Ticks a detonated anchor is left alone while the server's explosion arrives. */
	private static final int AFTERMATH = 10;

	private final Kro kro;
	private long tick;
	private long readyAt;
	private int originalSlot = -1;
	private BlockPos blasted;
	private long blastedUntil;

	public AutoAnchorController(Kro kro) {
		this.kro = kro;
	}

	public void tick(Minecraft mc) {
		tick++;
		AutoAnchor m = kro.modules.autoAnchor;
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (!m.enabled() || player == null || level == null || mc.gameMode == null || player.isSpectator()) {
			originalSlot = -1;
			return;
		}
		if (tick < readyAt || mc.screen != null) {
			return;
		}
		Inventory inv = player.getInventory();
		BlockPos anchor = anchorAtCrosshair(mc, level);
		boolean triggered = m.activation.is("Always") || mc.options.keyUse.isDown();
		boolean allowed = !(m.onlyExplosive.on() && Level.NETHER.equals(level.dimension()));
		if (anchor != null && anchor.equals(blasted) && tick < blastedUntil) {
			anchor = null;
		}
		if (anchor == null || !triggered || !allowed || player.isUsingItem() || player.isShiftKeyDown()) {
			swapBack(m, inv);
			return;
		}
		if (originalSlot < 0) {
			originalSlot = inv.getSelectedSlot();
		}
		int charge = level.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE);
		if (charge < Math.min(RespawnAnchorBlock.MAX_CHARGES, m.charges.intValue())) {
			int glowstone = findHotbar(inv, Items.GLOWSTONE);
			if (glowstone < 0) {
				return;
			}
			if (inv.getSelectedSlot() != glowstone) {
				select(inv, glowstone, m);
				return;
			}
			press(mc.options.keyUse, m);
			return;
		}
		int detonator = detonatorSlot(inv, m);
		if (detonator < 0) {
			return;
		}
		if (inv.getSelectedSlot() != detonator) {
			select(inv, detonator, m);
			return;
		}
		press(mc.options.keyUse, m);
		blasted = anchor;
		blastedUntil = tick + AFTERMATH;
	}

	private static BlockPos anchorAtCrosshair(Minecraft mc, ClientLevel level) {
		HitResult hit = mc.hitResult;
		if (hit == null || hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult bh)) {
			return null;
		}
		BlockState state = level.getBlockState(bh.getBlockPos());
		return state.is(Blocks.RESPAWN_ANCHOR) ? bh.getBlockPos() : null;
	}

	/** The hotbar slot to hold while setting the anchor off; never glowstone, which would charge it. */
	private int detonatorSlot(Inventory inv, AutoAnchor m) {
		if (m.detonate.is("Totem")) {
			int totem = findHotbar(inv, Items.TOTEM_OF_UNDYING);
			if (totem >= 0) {
				return totem;
			}
		}
		if (originalSlot >= 0 && !inv.getItem(originalSlot).is(Items.GLOWSTONE)) {
			return originalSlot;
		}
		int fallback = -1;
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) {
				return i;
			}
			if (fallback < 0 && !stack.is(Items.GLOWSTONE)) {
				fallback = i;
			}
		}
		return fallback;
	}

	private void swapBack(AutoAnchor m, Inventory inv) {
		if (originalSlot >= 0) {
			if (m.swapBack.on() && inv.getSelectedSlot() != originalSlot) {
				select(inv, originalSlot, m);
			}
			originalSlot = -1;
		}
	}

	private static int findHotbar(Inventory inv, net.minecraft.world.item.Item item) {
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			if (inv.getItem(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	private void select(Inventory inv, int slot, AutoAnchor m) {
		inv.setSelectedSlot(slot);
		readyAt = tick + 1 + Math.round(m.delay.random());
	}

	private void press(KeyMapping key, AutoAnchor m) {
		KeyMapping.click(InputConstants.getKey(key.saveString()));
		readyAt = tick + 1 + Math.round(m.delay.random());
	}
}
