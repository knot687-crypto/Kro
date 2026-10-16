package dev.kro.ui.widget;

import dev.kro.core.anim.Anim;
import dev.kro.core.setting.Setting;
import dev.kro.ui.Mouse;
import dev.kro.ui.UiHost;
import dev.kro.ui.render.Canvas;

/**
 * One line in a settings card. The card lays rows out top to bottom, assigning {@link #x},
 * {@link #y} and {@link #w} each frame before calling {@link #render}.
 */
public abstract class Row {
	public static final float LABEL_SIZE = 8.5f;
	public static final float INSET = 10f;

	protected final Setting<?> setting;
	protected final UiHost host;
	public float x;
	public float y;
	public float w;
	/** Whether the pointer is inside the card's visible viewport this frame. */
	public boolean pointerInViewport;
	protected final Anim hover = new Anim(0f, 16f);
	private final Anim reveal;

	protected Row(Setting<?> setting, UiHost host) {
		this.setting = setting;
		this.host = host;
		this.reveal = new Anim(setting == null || setting.isVisible() ? 1f : 0f, 11f);
	}

	public Setting<?> setting() {
		return setting;
	}

	public boolean visible() {
		return setting == null || setting.isVisible();
	}

	/** Full height in design units when visible. */
	public abstract float height();

	/** Height including the show/hide animation. */
	public float animatedHeight() {
		return height() * reveal();
	}

	/** 0..1 show/hide animation. */
	public float reveal() {
		return reveal.to(visible()).get();
	}

	public abstract void render(Canvas c, Mouse m);

	protected boolean hovered(Mouse m) {
		return pointerInViewport && m.in(x, y, w, height());
	}

	protected float hoverAnim(Mouse m) {
		return hover.to(hovered(m) && visible()).get();
	}

	public boolean mouseClicked(Mouse m, int button, boolean doubleClick) {
		return false;
	}

	public void mouseDragged(Mouse m) {
	}

	public void mouseReleased(Mouse m, int button) {
	}

	public boolean mouseScrolled(Mouse m, double amount) {
		return false;
	}

	public boolean keyPressed(int key, int mods) {
		return false;
	}

	/** True while the row wants all keyboard input (e.g. listening for a keybind). */
	public boolean capturing() {
		return false;
	}

	/** Description shown as a tooltip while hovering. */
	public String tooltip() {
		return setting == null ? null : setting.description();
	}
}
