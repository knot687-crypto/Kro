package dev.kro.core.module;

import dev.kro.core.module.impl.AimAssist;
import dev.kro.core.module.impl.AntiKnockback;
import dev.kro.core.module.impl.AutoAnchor;
import dev.kro.core.module.impl.AutoCrystal;
import dev.kro.core.module.impl.AutoHitCrystal;
import dev.kro.core.module.impl.AutoMace;
import dev.kro.core.module.impl.AutoTotem;
import dev.kro.core.module.impl.BowTrajectory;
import dev.kro.core.module.impl.BreachSwap;
import dev.kro.core.module.impl.HitSparks;
import dev.kro.core.module.impl.Interface;
import dev.kro.core.module.impl.JumpCircles;
import dev.kro.core.module.impl.JumpReset;
import dev.kro.core.module.impl.KillEffects;
import dev.kro.core.module.impl.LowFire;
import dev.kro.core.module.impl.LowOnFire;
import dev.kro.core.module.impl.NoExplosions;
import dev.kro.core.module.impl.PopEffects;
import dev.kro.core.module.impl.ShortWindCharge;
import dev.kro.core.module.impl.SmallerTotem;
import dev.kro.core.module.impl.Sprint;
import dev.kro.core.module.impl.TargetHud;
import dev.kro.core.module.impl.TargetRing;
import dev.kro.core.module.impl.Trails;
import dev.kro.core.module.impl.Triggerbot;
import dev.kro.core.module.impl.WTap;
import dev.kro.core.module.impl.WorldTheme;

import java.util.List;

/** The fixed set of Kro modules, in the order they appear in the menu. */
public final class ModuleRegistry {
	public final AimAssist aimAssist = new AimAssist();
	public final Triggerbot triggerbot = new Triggerbot();
	public final JumpReset jumpReset = new JumpReset();
	public final WTap wTap = new WTap();
	public final AntiKnockback antiKnockback = new AntiKnockback();
	public final AutoMace autoMace = new AutoMace();
	public final BreachSwap breachSwap = new BreachSwap();
	public final AutoCrystal autoCrystal = new AutoCrystal();
	public final AutoHitCrystal autoHitCrystal = new AutoHitCrystal();
	public final AutoAnchor autoAnchor = new AutoAnchor();
	public final AutoTotem autoTotem = new AutoTotem();
	public final Sprint sprint = new Sprint();
	public final ShortWindCharge shortWindCharge = new ShortWindCharge();
	public final HitSparks hitSparks = new HitSparks();
	public final WorldTheme worldTheme = new WorldTheme();
	public final TargetHud targetHud = new TargetHud();
	public final BowTrajectory bowTrajectory = new BowTrajectory();
	public final NoExplosions noExplosions = new NoExplosions();
	public final SmallerTotem smallerTotem = new SmallerTotem();
	public final LowFire lowFire = new LowFire();
	public final LowOnFire lowOnFire = new LowOnFire();
	public final PopEffects popEffects = new PopEffects();
	public final KillEffects killEffects = new KillEffects();
	public final JumpCircles jumpCircles = new JumpCircles();
	public final TargetRing targetRing = new TargetRing();
	public final Trails trails = new Trails();
	public final Interface ui = new Interface();

	private final List<Module> all = List.of(aimAssist, triggerbot, jumpReset, wTap, antiKnockback, autoMace, breachSwap,
			autoCrystal, autoHitCrystal, autoAnchor, autoTotem, sprint, shortWindCharge,
			targetHud, bowTrajectory, noExplosions, smallerTotem, lowFire, lowOnFire,
			popEffects, killEffects, hitSparks, jumpCircles, targetRing, trails, worldTheme, ui);

	/** Every module including client preferences. */
	public List<Module> all() {
		return all;
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
