package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.core.module.impl.WorldTheme;
import dev.kro.core.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Vector4f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Client-side world restyling: a fixed time of day, sky, fog and cloud tints, clear weather and
 * drifting ambient particles. Purely cosmetic and never sent to the server.
 */
public final class WorldThemeController {
	private final Kro kro;
	private Vector4f lastFog;
	private float particleDebt;

	public WorldThemeController(Kro kro) {
		this.kro = kro;
	}

	public boolean active() {
		return kro.modules.worldTheme.enabled() && Minecraft.getInstance().level != null;
	}

	public long dayTime(long original) {
		WorldTheme.Look look = kro.modules.worldTheme.currentLook();
		long t = Math.floorMod((long) (look.hours() * 1000f), 24000L);
		return Math.floorDiv(original, 24000L) * 24000L + t;
	}

	public boolean clearWeather() {
		return active() && kro.modules.worldTheme.clearWeather.on();
	}

	/** Tints a colour toward the theme hue while keeping its brightness, so night stays night. */
	private static int tint(int original, int theme, float amount) {
		if (amount <= 0f) {
			return original;
		}
		float lo = Math.max(0.02f, Colors.luminance(original | 0xFF000000));
		float lt = Math.max(0.02f, Colors.luminance(theme | 0xFF000000));
		float k = Math.min(2.4f, (lo + 0.06f) / (lt + 0.06f));
		int scaled = Colors.argb(255,
				Math.round(Colors.red(theme) * k),
				Math.round(Colors.green(theme) * k),
				Math.round(Colors.blue(theme) * k));
		int mixed = Colors.lerp(original | 0xFF000000, scaled, amount);
		return (original & 0xFF000000) | (mixed & 0x00FFFFFF);
	}

	public int sky(int original) {
		WorldTheme m = kro.modules.worldTheme;
		return tint(original, m.currentLook().sky(), m.skyTint.floatValue() / 100f);
	}

	public int cloud(int original) {
		WorldTheme m = kro.modules.worldTheme;
		return tint(original, Colors.lerp(m.currentLook().fog(), 0xFFFFFFFF, 0.55f), m.skyTint.floatValue() / 160f);
	}

	public void fog(Vector4f fog) {
		if (fog == null || fog == lastFog) {
			return;
		}
		lastFog = fog;
		WorldTheme m = kro.modules.worldTheme;
		int original = Colors.argbF(1f, clamp(fog.x), clamp(fog.y), clamp(fog.z));
		int tinted = tint(original, m.currentLook().fog(), m.fogTint.floatValue() / 100f);
		fog.x = Colors.red(tinted) / 255f;
		fog.y = Colors.green(tinted) / 255f;
		fog.z = Colors.blue(tinted) / 255f;
	}

	private static float clamp(float v) {
		return v < 0f ? 0f : Math.min(v, 1f);
	}

	public void tick(Minecraft mc) {
		WorldTheme m = kro.modules.worldTheme;
		LocalPlayer player = mc.player;
		if (!active() || !m.particles.on() || player == null || mc.isPaused()) {
			return;
		}
		particleDebt += 1.6f * m.density.floatValue();
		ThreadLocalRandom r = ThreadLocalRandom.current();
		WorldTheme.Look look = m.currentLook();
		while (particleDebt >= 1f) {
			particleDebt -= 1f;
			double x = player.getX() + (r.nextDouble() - 0.5) * 28.0;
			double y = player.getY() + r.nextDouble() * 10.0 - 1.0;
			double z = player.getZ() + (r.nextDouble() - 0.5) * 28.0;
			ParticleOptions type;
			double vx = 0, vy = 0, vz = 0;
			switch (m.preset.get()) {
				case "Sakura" -> type = ParticleTypes.CHERRY_LEAVES;
				case "Midnight" -> type = ParticleTypes.FIREFLY;
				case "Frost" -> type = ParticleTypes.SNOWFLAKE;
				case "Ember" -> {
					type = new DustParticleOptions(Colors.lerp(look.particle(), 0xFFFF4D2E, r.nextFloat() * 0.5f) & 0xFFFFFF, 0.8f + r.nextFloat() * 0.5f);
					vy = 0.02 + r.nextDouble() * 0.03;
				}
				case "Custom" -> type = new DustParticleOptions(look.fog() & 0xFFFFFF, 0.9f);
				default -> {
					type = ParticleTypes.END_ROD;
					vx = (r.nextDouble() - 0.5) * 0.01;
					vy = 0.005 + r.nextDouble() * 0.01;
					vz = (r.nextDouble() - 0.5) * 0.01;
				}
			}
			mc.level.addParticle(type, x, y, z, vx, vy, vz);
		}
	}
}
