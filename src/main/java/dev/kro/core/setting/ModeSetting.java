package dev.kro.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** One of a fixed list of named options. */
public final class ModeSetting extends Setting<String> {
	private final List<String> modes;
	private final String defaultValue;
	private String value;

	public ModeSetting(String name, String description, String defaultValue, String... modes) {
		super(name, description);
		this.modes = List.of(modes);
		if (!this.modes.contains(defaultValue)) {
			throw new IllegalArgumentException("Default mode " + defaultValue + " not in " + this.modes);
		}
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	@Override
	public String get() {
		return value;
	}

	public boolean is(String mode) {
		return value.equals(mode);
	}

	public int index() {
		return modes.indexOf(value);
	}

	public List<String> modes() {
		return modes;
	}

	@Override
	public void set(String v) {
		if (modes.contains(v)) {
			value = v;
		}
	}

	public void setIndex(int i) {
		value = modes.get(Math.floorMod(i, modes.size()));
	}

	public void cycle(int dir) {
		setIndex(index() + dir);
	}

	@Override
	public void reset() {
		value = defaultValue;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive()) {
			set(json.getAsString());
		}
	}
}
