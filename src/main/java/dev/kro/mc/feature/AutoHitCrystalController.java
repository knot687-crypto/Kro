package dev.kro.mc.feature;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kro.Kro;
import dev.kro.core.module.impl.AutoHitCrystal;
import dev.kro.core.module.impl.ShortWindCharge;
import dev.kro.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The hit-crystal: from your weapon, obsidian goes down where you aim, a crystal goes on it, the
 * crystal is popped and your weapon comes back, one human-sized step per tick. A right click
 * starts it and is taken by it; while it runs, further right clicks and the held button are kept
 * from vanilla so nothing extra is placed, and every action is an ordinary hotbar swap or click.
 */
public final class AutoHitCrystalController {
	private static final int GIVE_UP = 20;

	private enum Step { IDLE, OBSIDIAN, CRYSTAL, BREAK, DONE }

	private final Kro kro;
	private Step step = Step.IDLE;
	private long tick;
	private long readyAt;
	private long stepSince;
	private int originalSlot = -1;

	public AutoHitCrystalController(Kro kro) {
		this.kro = kro;
	}

	/** True while a hit-crystal runs: the physical right button is hidden from vanilla. */
	public boolean holdsUse() {
		return step != Step.IDLE;
	}

	public void tick(Minecraft mc) {
		tick++;
		AutoHitCrystal m = kro.modules.autoHitCrystal;
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (!m.enabled() || player == null || level == null || mc.gameMode == null || mc.screen != null || player.isSpectator()) {
			finish(player);
			return;
		}
		Inventory inv = player.getInventory();
		if (step == Step.IDLE) {
			// a right click queued for this tick
			if (((KeyMappingAccessor) mc.options.keyUse).kro$clickCount() == 0 || player.isUsingItem()) {
				return;
			}
			ItemStack held = player.getMainHandItem();
			if (m.weaponOnly.on() && !held.is(ItemTags.SWORDS) && !held.is(ItemTags.AXES) && !held.is(Items.MACE)) {
				return;
			}
			ShortWindCharge wind = kro.modules.shortWindCharge;
			if (held.is(Items.MACE) && wind.enabled() && wind.activation.is("Use with mace")) {
				// that right click is Short Wind Charge's
				return;
			}
			BlockHitResult bh = blockAt(mc);
			if (bh == null) {
				return;
			}
			boolean base = isCrystalBase(level.getBlockState(bh.getBlockPos()));
			if (!base && find(inv, Items.OBSIDIAN) < 0 || find(inv, Items.END_CRYSTAL) < 0) {
				return;
			}
			originalSlot = inv.getSelectedSlot();
			drainClicks(mc);
			go(base ? Step.CRYSTAL : Step.OBSIDIAN, 0);
			return;
		}
		// the sequence places everything itself; more right clicks would only add stray blocks
		drainClicks(mc);
		if (tick < readyAt) {
			return;
		}
		if (tick - stepSince > GIVE_UP) {
			go(Step.DONE, 0);
		}
		switch (step) {
			case OBSIDIAN -> {
				int obsidian = find(inv, Items.OBSIDIAN);
				if (obsidian < 0 || blockAt(mc) == null) {
					go(Step.DONE, 0);
				} else if (inv.getSelectedSlot() != obsidian) {
					inv.setSelectedSlot(obsidian);
					pause(m);
				} else {
					click(mc.options.keyUse);
					go(Step.CRYSTAL, delay(m));
				}
			}
			case CRYSTAL -> {
				int crystal = find(inv, Items.END_CRYSTAL);
				BlockHitResult bh = blockAt(mc);
				if (crystal < 0) {
					go(Step.DONE, 0);
				} else if (inv.getSelectedSlot() != crystal) {
					inv.setSelectedSlot(crystal);
					pause(m);
				} else if (bh != null && AutoCrystalController.canPlaceOn(level, bh.getBlockPos())) {
					click(mc.options.keyUse);
					go(m.hit.on() ? Step.BREAK : Step.DONE, delay(m));
				}
			}
			case BREAK -> {
				HitResult hit = mc.hitResult;
				if (hit instanceof EntityHitResult eh && hit.getType() == HitResult.Type.ENTITY && eh.getEntity() instanceof EndCrystal) {
					click(mc.options.keyAttack);
					go(Step.DONE, delay(m));
				}
			}
			case DONE -> finish(player);
			default -> {
			}
		}
	}

	private void finish(LocalPlayer player) {
		if (step != Step.IDLE && player != null && originalSlot >= 0 && kro.modules.autoHitCrystal.swapBack.on()) {
			player.getInventory().setSelectedSlot(originalSlot);
		}
		originalSlot = -1;
		step = Step.IDLE;
	}

	private void go(Step next, long wait) {
		step = next;
		stepSince = tick;
		readyAt = tick + wait;
	}

	private void pause(AutoHitCrystal m) {
		readyAt = tick + delay(m);
	}

	private static long delay(AutoHitCrystal m) {
		return 1 + Math.round(m.delay.random());
	}

	private static void drainClicks(Minecraft mc) {
		while (mc.options.keyUse.consumeClick()) {
			// dropped: the macro makes its own clicks
		}
	}

	private static BlockHitResult blockAt(Minecraft mc) {
		HitResult hit = mc.hitResult;
		return hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bh ? bh : null;
	}

	private static boolean isCrystalBase(BlockState state) {
		return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
	}

	private static int find(Inventory inv, Item item) {
		if (inv.getItem(inv.getSelectedSlot()).is(item)) {
			return inv.getSelectedSlot();
		}
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			if (inv.getItem(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	private static void click(KeyMapping key) {
		KeyMapping.click(InputConstants.getKey(key.saveString()));
	}
}
