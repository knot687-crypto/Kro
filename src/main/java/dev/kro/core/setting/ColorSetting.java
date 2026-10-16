package dev.kro.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An opaque RGB colour (alpha is controlled by the renderer). */
public final class ColorSetting extends Setting<Integer> {
	private final int defaultValue;
	private int value;

	public ColorSetting(String name, String description, int defaultValue) {
		super(name, description);
		this.defaultValue = defaultValue | 0xFF000000;
		this.value = this.defaultValue;
	}

	@Override
	public Integer get() {
		return value;
	}

	public int color() {
		return value;
	}

	@Override
	public void set(Integer v) {
		value = v | 0xFF000000;
	}

	@Override
	public void reset() {
		value = defaultValue;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(String.format("#%06X", value & 0xFFFFFF));
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive()) {
			String s = json.getAsString().trim();
			if (s.startsWith("#")) {
				s = s.substring(1);
			}
			try {
				set(Integer.parseUnsignedInt(s, 16));
			} catch (NumberFormatException ignored) {
				// keep current value
			}
		}
	}
}
