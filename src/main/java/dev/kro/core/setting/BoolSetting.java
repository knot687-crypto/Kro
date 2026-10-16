package dev.kro.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting<Boolean> {
	private final boolean defaultValue;
	private boolean value;

	public BoolSetting(String name, String description, boolean defaultValue) {
		super(name, description);
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	@Override
	public Boolean get() {
		return value;
	}

	public boolean on() {
		return value;
	}

	@Override
	public void set(Boolean value) {
		this.value = value;
	}

	public void toggle() {
		value = !value;
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
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
			value = json.getAsBoolean();
		}
	}
}
