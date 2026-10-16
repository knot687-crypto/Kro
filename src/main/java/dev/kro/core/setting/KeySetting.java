package dev.kro.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.kro.core.util.Keys;

/** A GLFW key code, or {@link Keys#NONE} when unbound. */
public final class KeySetting extends Setting<Integer> {
	private final int defaultValue;
	private int value;

	public KeySetting(String name, String description, int defaultValue) {
		super(name, description);
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	@Override
	public Integer get() {
		return value;
	}

	public int key() {
		return value;
	}

	public boolean bound() {
		return value != Keys.NONE;
	}

	@Override
	public void set(Integer v) {
		value = v;
	}

	@Override
	public void reset() {
		value = defaultValue;
	}

	public String label() {
		return Keys.name(value);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			value = json.getAsInt();
		}
	}
}
