package dev.kro.mc.input;

import dev.kro.Kro;
import dev.kro.core.module.Module;
import dev.kro.core.util.Keys;
import dev.kro.mc.screen.KroScreen;
import net.minecraft.client.Minecraft;

/** In-game key handling: the menu key and per-module toggle binds. */
public final class KeyBinds {
	private static final int PRESS = 1;

	private final Kro kro;
	private long lastAttackClick;

	public KeyBinds(Kro kro) {
		this.kro = kro;
	}

	/**
	 * Called for every keyboard event before vanilla handles it.
	 *
	 * @return true to swallow the event
	 */
	public boolean onKey(int key, int action) {
		Minecraft mc = Minecraft.getInstance();
		if (action != PRESS || mc.screen != null || mc.player == null) {
			return false;
		}
		if (key == kro.modules.ui.menuKey.key()) {
			mc.setScreen(new KroScreen());
			return true;
		}
		return toggleBound(key);
	}

	/** Mouse buttons: records attack clicks and handles side-button binds. */
	public void onMouse(int button, int action) {
		Minecraft mc = Minecraft.getInstance();
		if (action != PRESS || mc.screen != null || mc.player == null) {
			return;
		}
		if (button == 0) {
			lastAttackClick = System.currentTimeMillis();
		}
		if (button >= 2) {
			toggleBound(Keys.MOUSE_BASE + button);
		}
	}

	/** Milliseconds since the player last clicked to attack. */
	public long sinceAttackClick() {
		return System.currentTimeMillis() - lastAttackClick;
	}

	/** Toggles every module bound to {@code code}. Never swallows the key, vanilla may use it too. */
	private boolean toggleBound(int code) {
		for (Module m : kro.modules.all()) {
			if (!m.alwaysOn() && m.bind().key() == code) {
				m.toggle();
				if (kro.modules.ui.notifications.on()) {
					kro.toasts.push(m, m.enabled());
				}
			}
		}
		return false;
	}
}
