package dev.kro.core.module;

import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ColorSetting;
import dev.kro.core.setting.KeySetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.core.setting.RangeSetting;
import dev.kro.core.setting.Setting;
import dev.kro.core.util.Keys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A feature's model: identity, enabled state, keybind and settings. Behaviour lives in
 * controllers that observe the module, which keeps this layer free of game code.
 */
public abstract class Module {
	private final String name;
	private final String description;
	private final Category category;
	private final char icon;
	private final List<Setting<?>> settings = new ArrayList<>();
	private final List<Consumer<Boolean>> listeners = new CopyOnWriteArrayList<>();
	private final KeySetting bind = new KeySetting("Keybind", "Toggle this module from anywhere in game.", Keys.NONE);
	private boolean enabled;

	protected Module(String name, String description, Category category, char icon) {
		this.name = name;
		this.description = description;
		this.category = category;
		this.icon = icon;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public Category category() {
		return category;
	}

	public char icon() {
		return icon;
	}

	public boolean enabled() {
		return enabled;
	}

	/** Modules that cannot be switched off (e.g. client preferences). */
	public boolean alwaysOn() {
		return false;
	}

	public void setEnabled(boolean on) {
		if (alwaysOn()) {
			on = true;
		}
		if (this.enabled == on) {
			return;
		}
		this.enabled = on;
		for (Consumer<Boolean> l : listeners) {
			l.accept(on);
		}
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	public void onToggle(Consumer<Boolean> listener) {
		listeners.add(listener);
	}

	public KeySetting bind() {
		return bind;
	}

	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	protected BoolSetting bool(String name, String description, boolean def) {
		return add(new BoolSetting(name, description, def));
	}

	protected NumberSetting number(String name, String description, double def, double min, double max, double step, String unit) {
		return add(new NumberSetting(name, description, def, min, max, step, unit));
	}

	protected RangeSetting range(String name, String description, double low, double high, double min, double max, double step, String unit) {
		return add(new RangeSetting(name, description, low, high, min, max, step, unit));
	}

	protected ModeSetting mode(String name, String description, String def, String... modes) {
		return add(new ModeSetting(name, description, def, modes));
	}

	protected ColorSetting color(String name, String description, int def) {
		return add(new ColorSetting(name, description, def));
	}

	protected KeySetting key(String name, String description, int def) {
		return add(new KeySetting(name, description, def));
	}
}
