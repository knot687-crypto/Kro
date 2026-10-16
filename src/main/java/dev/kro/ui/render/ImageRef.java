package dev.kro.ui.render;

/** An opaque handle to a texture owned by the active backend. */
public interface ImageRef {
	/** Backend specific texture object. */
	Object handle();
}
