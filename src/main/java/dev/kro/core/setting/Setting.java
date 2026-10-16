package dev.kro.core.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/**
 * A user-tunable value belonging to a module. Settings are plain models: the UI reads and writes
 * them, controllers read them, and the config layer (de)serialises them.
 */
public abstract class Setting<T> {
	private final String name;
	private final String description;
	private BooleanSupplier visible = () -> true;

	protected Setting(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public abstract T get();

	public abstract void set(T value);

	public abstract void reset();

	public abstract JsonElement toJson();

	public abstract void fromJson(JsonElement json);

	/** Only show (and apply) this setting while the condition holds. */
	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
		this.visible = condition;
		return (S) this;
	}

	public boolean isVisible() {
		return visible.getAsBoolean();
	}
}
