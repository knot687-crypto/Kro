package dev.kro.mc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kro.Kro;
import dev.kro.ui.UiHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** The game side of {@link UiHost}: soft click sounds, closing and saving. */
public final class McHost implements UiHost {
	private final Runnable close;
	private long lastTick;

	public McHost(Runnable close) {
		this.close = close;
	}

	@Override
	public void sound(String kind) {
		Kro kro = Kro.get();
		if (kro == null || !kro.modules.ui.sounds.on()) {
			return;
		}
		float pitch;
		float volume;
		switch (kind) {
			case "on" -> {
				pitch = 1.85f;
				volume = 0.32f;
			}
			case "off" -> {
				pitch = 1.35f;
				volume = 0.28f;
			}
			case "open" -> {
				pitch = 1.65f;
				volume = 0.22f;
			}
			case "close" -> {
				pitch = 1.4f;
				volume = 0.2f;
			}
			case "tick" -> {
				long now = System.currentTimeMillis();
				if (now - lastTick < 45L) {
					return;
				}
				lastTick = now;
				pitch = 2.0f;
				volume = 0.07f;
			}
			default -> {
				pitch = 1.6f;
				volume = 0.24f;
			}
		}
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
	}

	@Override
	public void closeScreen() {
		close.run();
	}

	@Override
	public void saveConfig() {
		Kro.get().save();
	}

	@Override
	public boolean isKeyDown(int key) {
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
	}
}
