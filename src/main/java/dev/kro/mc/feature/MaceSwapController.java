package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.core.module.impl.AutoMace;
import dev.kro.core.module.impl.BreachSwap;
import dev.kro.mixin.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Auto Mace and Breach Swap. Both swap the hotbar to a mace right before an attack leaves the
 * client, so the server sees the mace in hand for that one blow, and (when set to) swap back in
 * the same tick: the swap, the hit and the swap back reach the server together.
 */
public final class MaceSwapController {
	private final Kro kro;
	private int restoreSlot = -1;
	private boolean restoreAfterAttack;
	private boolean restoreOnLanding;

	public MaceSwapController(Kro kro) {
		this.kro = kro;
	}

	/** Start of an attack, before its packet: pick the mace if this blow calls for one. */
	public void beforeAttack(Minecraft mc, Player attacker, Entity target) {
		LocalPlayer player = mc.player;
		if (attacker != player || player == null || !(target instanceof LivingEntity) || restoreAfterAttack) {
			return;
		}
		Inventory inv = player.getInventory();
		int current = inv.getSelectedSlot();
		ItemStack held = inv.getItem(current);
		if (held.is(Items.MACE)) {
			return;
		}
		AutoMace am = kro.modules.autoMace;
		// the mace smashes once you have fallen more than 1.5 blocks, never while gliding
		if (am.enabled() && !player.onGround() && !player.isFallFlying() && player.fallDistance > am.minFall.value()
				&& (!am.playersOnly.on() || target instanceof Player)) {
			int mace = findMace(inv, Enchantments.DENSITY, false);
			if (mace >= 0) {
				inv.setSelectedSlot(mace);
				switch (am.swapBack.get()) {
					case "Instantly" -> holdForThisHit(current);
					case "On landing" -> {
						restoreSlot = current;
						restoreOnLanding = true;
					}
					default -> {
					}
				}
				return;
			}
		}
		BreachSwap bs = kro.modules.breachSwap;
		if (bs.enabled() && Targets.isWeapon(held) && (!bs.playersOnly.on() || target instanceof Player)
				&& ThreadLocalRandom.current().nextDouble(100.0) < bs.chance.value()) {
			int mace = findMace(inv, Enchantments.BREACH, bs.breachOnly.on());
			if (mace >= 0) {
				inv.setSelectedSlot(mace);
				holdForThisHit(current);
			}
		}
	}

	private void holdForThisHit(int back) {
		restoreSlot = back;
		restoreAfterAttack = true;
	}

	/** End of the attack: put the weapon back and tell the server right away. */
	public void afterAttack(Minecraft mc) {
		if (!restoreAfterAttack) {
			return;
		}
		restoreAfterAttack = false;
		if (mc.player != null && restoreSlot >= 0) {
			mc.player.getInventory().setSelectedSlot(restoreSlot);
			if (mc.gameMode != null) {
				((MultiPlayerGameModeAccessor) mc.gameMode).kro$syncCarriedItem();
			}
		}
		restoreSlot = -1;
	}

	/** Each tick: Auto Mace's "On landing" swap back. */
	public void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (!restoreOnLanding) {
			return;
		}
		if (player == null) {
			restoreOnLanding = false;
			restoreSlot = -1;
			return;
		}
		if (player.onGround() || !kro.modules.autoMace.enabled()) {
			if (restoreSlot >= 0) {
				player.getInventory().setSelectedSlot(restoreSlot);
			}
			restoreOnLanding = false;
			restoreSlot = -1;
		}
	}

	/** A hotbar mace, preferring one with {@code wanted}; with {@code required}, only such a mace. */
	private static int findMace(Inventory inv, ResourceKey<Enchantment> wanted, boolean required) {
		int any = -1;
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.is(Items.MACE)) {
				continue;
			}
			if (has(stack, wanted)) {
				return i;
			}
			if (any < 0) {
				any = i;
			}
		}
		return required ? -1 : any;
	}

	private static boolean has(ItemStack stack, ResourceKey<Enchantment> key) {
		for (Holder<Enchantment> h : stack.getEnchantments().keySet()) {
			if (h.is(key)) {
				return true;
			}
		}
		return false;
	}
}
