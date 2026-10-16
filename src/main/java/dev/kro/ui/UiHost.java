package dev.kro.ui;

/** Services the UI needs from whoever hosts it (the game, or the offline preview). */
public interface UiHost {
	UiHost NONE = new UiHost() {
	};

	/** Soft UI feedback sounds: "click", "on", "off", "open", "close", "tick". */
	default void sound(String kind) {
	}

	default void closeScreen() {
	}

	default void saveConfig() {
	}

	/** Whether a GLFW key is physically held (for modifier aware behaviour). */
	default boolean isKeyDown(int key) {
		return false;
	}
}
