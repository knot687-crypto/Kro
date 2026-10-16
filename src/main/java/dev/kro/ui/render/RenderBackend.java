package dev.kro.ui.render;

/**
 * Receives finished draw commands from a {@link Canvas}, in paint order. The in-game backend
 * turns them into GUI render states; the preview backend rasterises them in software.
 */
public interface RenderBackend {
	void shape(ShapeCmd cmd);

	void glyphs(GlyphRun run);

	/**
	 * Escape hatch for backend specific drawing (e.g. a 3D entity preview) that must stay in
	 * paint order with Kro's own commands. Backends that cannot honour it may ignore it.
	 */
	default void custom(Object payload) {
	}
}
