package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.core.module.impl.AutoTotem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

/**
 * Keeps a totem in the offhand. A refill is a single vanilla inventory click (the same "swap to
 * offhand" a player triggers with F over a slot), sent after a randomised human delay and only
 * while no other container is open, so the server sees an ordinary inventory action.
 */
public final class AutoTotemController {
	/** Inventory's offhand index, the target of a swap-to-offhand click. */
	private static final int OFFHAND_BUTTON = Inventory.SLOT_OFFHAND;
	/** Player inventory menu layout: main inventory rows, then the hotbar row. */
	private static final int MENU_HOTBAR_START = 36;

	private final Kro kro;
	private long swapAt;

	public AutoTotemController(Kro kro) {
		this.kro = kro;
	}

	public void tick(Minecraft mc) {
		AutoTotem m = kro.modules.autoTotem;
		LocalPlayer player = mc.player;
		if (!m.enabled() || player == null || mc.gameMode == null || player.isSpectator() || player.isCreative()) {
			swapAt = 0L;
			return;
		}
		boolean inventoryOpen = mc.screen instanceof InventoryScreen;
		boolean allowed = m.mode.is("Inventory") ? inventoryOpen : player.containerMenu == player.inventoryMenu;
		if (!allowed || player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) {
			swapAt = 0L;
			return;
		}
		Inventory inv = player.getInventory();
		int keep = m.hotbarSlot();
		int from = -1;
		int button = -1;
		boolean offhandReady = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
		if (!offhandReady && (player.getOffhandItem().isEmpty() || m.replace.on())) {
			from = findTotem(inv, keep);
			button = OFFHAND_BUTTON;
		} else if (offhandReady && keep >= 0 && inventoryOpen && !inv.getItem(keep).is(Items.TOTEM_OF_UNDYING)) {
			from = findTotemInMain(inv);
			button = keep;
		}
		if (from < 0) {
			swapAt = 0L;
			return;
		}
		long now = System.currentTimeMillis();
		if (swapAt == 0L) {
			swapAt = now + Math.round(m.delay.random());
		}
		if (now < swapAt) {
			return;
		}
		int menuSlot = from < Inventory.getSelectionSize() ? MENU_HOTBAR_START + from : from;
		mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, menuSlot, button, ClickType.SWAP, player);
		swapAt = 0L;
	}

	/** A totem for the offhand: from the main inventory first, then the hotbar, sparing the kept slot. */
	private static int findTotem(Inventory inv, int keep) {
		int main = findTotemInMain(inv);
		if (main >= 0) {
			return main;
		}
		int spare = -1;
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
				if (i != keep) {
					return i;
				}
				spare = i;
			}
		}
		return spare;
	}

	private static int findTotemInMain(Inventory inv) {
		for (int i = Inventory.getSelectionSize(); i < Inventory.INVENTORY_SIZE; i++) {
			if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
				return i;
			}
		}
		return -1;
	}
}
