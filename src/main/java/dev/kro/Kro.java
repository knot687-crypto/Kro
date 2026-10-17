package dev.kro;

import dev.kro.core.anim.Clock;
import dev.kro.core.config.ConfigIO;
import dev.kro.core.module.Module;
import dev.kro.core.module.ModuleRegistry;
import dev.kro.mc.feature.AimAssistController;
import dev.kro.mc.feature.AutoAnchorController;
import dev.kro.mc.feature.AutoCrystalController;
import dev.kro.mc.feature.AutoHitCrystalController;
import dev.kro.mc.feature.AutoTotemController;
import dev.kro.mc.feature.FastPlaceController;
import dev.kro.mc.feature.HitSparksController;
import dev.kro.mc.feature.JumpResetController;
import dev.kro.mc.feature.MaceSwapController;
import dev.kro.mc.feature.NoClickDelayController;
import dev.kro.mc.feature.ReachController;
import dev.kro.mc.feature.ShortWindChargeController;
import dev.kro.mc.feature.TargetTracker;
import dev.kro.mc.feature.TriggerbotController;
import dev.kro.mc.feature.VelocityController;
import dev.kro.mc.feature.WTapController;
import dev.kro.mc.feature.WorldThemeController;
import dev.kro.mc.fx.WorldEffects;
import dev.kro.mc.hud.HudLayer;
import dev.kro.mc.input.KeyBinds;
import dev.kro.mc.render.WallpaperTexture;
import dev.kro.mc.screen.KroScreen;
import dev.kro.ui.hud.Toasts;
import dev.kro.ui.render.Theme;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class Kro implements ClientModInitializer {
	public static final String MOD_ID = "kro";
	public static final Logger LOGGER = LoggerFactory.getLogger("Kro");

	private static Kro instance;

	public final ModuleRegistry modules = new ModuleRegistry();
	public final Toasts toasts = new Toasts();
	public final TargetTracker targets = new TargetTracker(this);
	public final AimAssistController aimAssist = new AimAssistController(this);
	public final TriggerbotController triggerbot = new TriggerbotController(this);
	public final JumpResetController jumpReset = new JumpResetController(this);
	public final HitSparksController hitSparks = new HitSparksController(this);
	public final WorldThemeController worldTheme = new WorldThemeController(this);
	public final AutoCrystalController autoCrystal = new AutoCrystalController(this);
	public final AutoAnchorController autoAnchor = new AutoAnchorController(this);
	public final AutoTotemController autoTotem = new AutoTotemController(this);
	public final AutoHitCrystalController hitCrystal = new AutoHitCrystalController(this);
	public final WTapController wTap = new WTapController(this);
	public final MaceSwapController maceSwap = new MaceSwapController(this);
	public final ShortWindChargeController windCharge = new ShortWindChargeController(this);
	public final VelocityController velocity = new VelocityController(this);
	public final ReachController reach = new ReachController(this);
	public final FastPlaceController fastPlace = new FastPlaceController(this);
	public final NoClickDelayController noClickDelay = new NoClickDelayController(this);
	public final WorldEffects effects = new WorldEffects(this);
	public final KeyBinds keys = new KeyBinds(this);
	public WallpaperTexture wallpaper;
	private final HudLayer hud = new HudLayer(this);
	private Path configPath;
	private long saveDue = -1L;

	public static Kro get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		configPath = FabricLoader.getInstance().getConfigDir().resolve("kro.json");
		wallpaper = new WallpaperTexture(FabricLoader.getInstance().getConfigDir());
		try {
			ConfigIO.load(configPath, modules);
		} catch (IOException e) {
			LOGGER.warn("Could not read {}, using defaults", configPath, e);
		}
		applyInterface();
		for (Module m : modules.all()) {
			m.onToggle(on -> scheduleSave());
		}

		ClientTickEvents.START_CLIENT_TICK.register(this::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(this::endTick);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), hud::render);
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(hitSparks::renderWorld);
		WorldRenderEvents.END_EXTRACTION.register(ctx -> effects.capture(ctx.tickCounter().getGameTimeDeltaPartialTick(false)));
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(effects::render);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
		LOGGER.info("Kro ready");
	}

	private void startTick(Minecraft mc) {
		applyInterface();
		noClickDelay.clear(mc);
		fastPlace.tick(mc);
		jumpReset.startTick(mc);
		wTap.startTick(mc);
		maceSwap.tick(mc);
		autoTotem.tick(mc);
		windCharge.startTick(mc);
		if (!windCharge.throwing()) {
			// a throw owns this tick's hotbar and clicks; the other macros carry on next tick
			autoAnchor.tick(mc);
			autoCrystal.tick(mc);
			hitCrystal.tick(mc);
			triggerbot.tick(mc);
		}
		worldTheme.tick(mc);
		targets.tick(mc);
	}

	private void endTick(Minecraft mc) {
		jumpReset.endTick(mc);
		windCharge.endTick(mc);
		effects.tick(mc);
		if (modules.ui.window.is("Picture")) {
			// keeps the picture decoded and sized for the current window, off the render thread
			KroScreen.picture(mc);
		}
		if (saveDue > 0L && System.currentTimeMillis() >= saveDue) {
			saveDue = -1L;
			save();
		}
	}

	/** Pushes client preferences (accent, motion speed) into the UI layer. */
	public void applyInterface() {
		Theme.setAccent(modules.ui.accent.get());
		Clock.setSpeed(modules.ui.animationSpeed.floatValue());
	}

	public void scheduleSave() {
		saveDue = System.currentTimeMillis() + 1500L;
	}

	public void save() {
		if (configPath == null) {
			return;
		}
		try {
			ConfigIO.save(configPath, modules);
		} catch (IOException e) {
			LOGGER.warn("Could not save {}", configPath, e);
		}
	}
}
