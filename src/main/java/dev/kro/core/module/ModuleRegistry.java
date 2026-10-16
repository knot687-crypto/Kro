package dev.kro.core.module;

import dev.kro.core.module.impl.AimAssist;
import dev.kro.core.module.impl.HitSparks;
import dev.kro.core.module.impl.Interface;
import dev.kro.core.module.impl.JumpReset;
import dev.kro.core.module.impl.Sprint;
import dev.kro.core.module.impl.TargetHud;
import dev.kro.core.module.impl.Triggerbot;
import dev.kro.core.module.impl.WorldTheme;

import java.util.List;

/** The fixed set of Kro modules, in the order they appear around the hub. */
public final class ModuleRegistry {
	public final AimAssist aimAssist = new AimAssist();
	public final Triggerbot triggerbot = new Triggerbot();
	public final JumpReset jumpReset = new JumpReset();
	public final Sprint sprint = new Sprint();
	public final HitSparks hitSparks = new HitSparks();
	public final WorldTheme worldTheme = new WorldTheme();
	public final TargetHud targetHud = new TargetHud();
	public final Interface ui = new Interface();

	private final List<Module> all = List.of(aimAssist, triggerbot, jumpReset, sprint, hitSparks, worldTheme, targetHud, ui);
	private final List<Module> orbit = List.of(aimAssist, triggerbot, jumpReset, sprint, hitSparks, worldTheme, targetHud);

	/** Every module including client preferences. */
	public List<Module> all() {
		return all;
	}

	/** Modules shown as nodes on the hub ring. */
	public List<Module> orbit() {
		return orbit;
	}

	public Module byName(String name) {
		for (Module m : all) {
			if (m.name().equalsIgnoreCase(name)) {
				return m;
			}
		}
		return null;
	}
}
