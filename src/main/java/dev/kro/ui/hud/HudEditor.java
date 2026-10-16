package dev.kro.ui.hud;

import dev.kro.core.anim.Anim;
import dev.kro.core.anim.Clock;
import dev.kro.core.anim.Spring;
import dev.kro.core.module.impl.TargetHud;
import dev.kro.core.util.Colors;
import dev.kro.core.util.Keys;
import dev.kro.ui.Mouse;
import dev.kro.ui.UiHost;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Icons;
import dev.kro.ui.render.Theme;
import dev.kro.ui.widget.Draw;

/** Lets the player drag the Target HUD into place, with soft snapping to the screen centre. */
public final class HudEditor {
	private static final float SNAP = 6f;

	private final TargetHud settings;
	private final TargetHudView view;
	private final TargetInfo sample;
	private final UiHost host;
	private final Runnable done;
	private final Mouse mouse = new Mouse();
	private final Spring open = new Spring(0f, 180f, 24f);
	private final Anim hoverCard = new Anim(0f, 14f);
	private final Anim dragAnim = new Anim(0f, 16f);
	private final Anim guideX = new Anim(0f, 18f);
	private final Anim guideY = new Anim(0f, 18f);
	private final Anim doneHover = new Anim(0f, 16f);
	private final Anim resetHover = new Anim(0f, 16f);
	private boolean dragging;
	private float grabDx;
	private float grabDy;
	private float w;
	private float h;
	private float[] doneRect = new float[4];
	private float[] resetRect = new float[4];
	private boolean closing;

	public HudEditor(TargetHud settings, TargetInfo sample, UiHost host, Runnable done) {
		this.settings = settings;
		this.view = new TargetHudView();
		this.sample = sample;
		this.host = host;
		this.done = done;
		open.to(1f);
	}

	private float cardX() {
		return settings.posX.floatValue() * w;
	}

	private float cardY() {
		return settings.posY.floatValue() * h;
	}

	public void render(Canvas c, float screenW, float screenH, float mx, float my) {
		w = screenW;
		h = screenH;
		mouse.x = mx;
		mouse.y = my;
		float o = Math.max(0f, Math.min(1f, open.to(closing ? 0f : 1f).get()));
		if (closing && o < 0.02f) {
			done.run();
			closing = false;
		}
		c.push();
		c.alpha(o);
		c.rect(0f, 0f, w, h, 0f, 0x59060709);
		// faint dot grid helps judge alignment
		for (float gx = 16f; gx < w; gx += 24f) {
			for (float gy = 16f; gy < h; gy += 24f) {
				c.circle(gx, gy, 0.6f, 0x14FFFFFF);
			}
		}
		float gxA = guideX.get(), gyA = guideY.get();
		if (gxA > 0.01f) {
			c.rect(w / 2f - 0.5f, 0f, 1f, h, 0f, Theme.accent(0.65f * gxA));
		}
		if (gyA > 0.01f) {
			c.rect(0f, h / 2f - 0.5f, w, 1f, 0f, Theme.accent(0.65f * gyA));
		}

		view.update(sample);
		float cw = view.width(settings), ch = view.height(settings);
		float cx = cardX(), cy = cardY();
		boolean over = mouse.in(cx - cw / 2f, cy - ch / 2f, cw, ch);
		float hv = hoverCard.to(over || dragging).get();
		float dg = dragAnim.to(dragging).get();
		view.render(c, cx, cy, settings);
		if (hv > 0.01f) {
			c.outline(cx - cw / 2f - 4f, cy - ch / 2f - 4f, cw + 8f, ch + 8f, 14f, 1f, Theme.accent(0.35f + 0.4f * dg));
			if (dg < 0.5f) {
				c.push();
				c.alpha(hv * (1f - dg));
				Draw.pill(c, "Drag to move", cx, cy + ch / 2f + 14f, Theme.POPOVER, Theme.TEXT_DIM, Canvas.CENTER);
				c.pop();
			}
		}
		renderBanner(c);
		c.pop();
	}

	private void renderBanner(Canvas c) {
		float bw = 300f, bh = 34f;
		float bx = (w - bw) / 2f, by = 14f;
		Draw.popover(c, bx, by, bw, bh, 10f);
		c.icon(Icons.MOVE, bx + 16f, by + bh / 2f, 10f, Theme.TEXT_DIM);
		c.text(Fonts.medium(), 8f, "Drag the Target HUD into place", bx + 28f, by + bh / 2f, Theme.TEXT);
		float dW = 46f, dH = 22f;
		float dX = bx + bw - 6f - dW, dY = by + (bh - dH) / 2f;
		float dh = doneHover.to(mouse.in(dX, dY, dW, dH)).get();
		c.rect(dX, dY, dW, dH, 7f, Colors.lerp(Theme.accent(), Colors.lighten(Theme.accent(), 0.12f), dh));
		c.text(Fonts.semibold(), 7.5f, "Done", dX + dW / 2f, dY + dH / 2f, Theme.onAccent(), Canvas.CENTER);
		float rW = 46f;
		float rX = dX - 6f - rW;
		float rh = resetHover.to(mouse.in(rX, dY, rW, dH)).get();
		c.rect(rX, dY, rW, dH, 7f, Colors.lerp(Theme.FIELD, Theme.SURFACE_ACTIVE, rh));
		c.text(Fonts.semibold(), 7.5f, "Reset", rX + rW / 2f, dY + dH / 2f, Theme.TEXT_DIM, Canvas.CENTER);
		doneRect = new float[] {dX, dY, dW, dH};
		resetRect = new float[] {rX, dY, rW, dH};
	}

	public boolean mouseClicked(double mx, double my, int button) {
		mouse.x = (float) mx;
		mouse.y = (float) my;
		if (button != 0) {
			return false;
		}
		if (mouse.in(doneRect[0], doneRect[1], doneRect[2], doneRect[3])) {
			finish();
			return true;
		}
		if (mouse.in(resetRect[0], resetRect[1], resetRect[2], resetRect[3])) {
			settings.posX.reset();
			settings.posY.reset();
			host.sound("click");
			return true;
		}
		float cw = view.width(settings), ch = view.height(settings);
		float cx = cardX(), cy = cardY();
		if (mouse.in(cx - cw / 2f, cy - ch / 2f, cw, ch)) {
			dragging = true;
			grabDx = mouse.x - cx;
			grabDy = mouse.y - cy;
			host.sound("tick");
			return true;
		}
		return false;
	}

	public void mouseDragged(double mx, double my) {
		mouse.x = (float) mx;
		mouse.y = (float) my;
		if (!dragging) {
			return;
		}
		float cw = view.width(settings), ch = view.height(settings);
		float nx = mouse.x - grabDx, ny = mouse.y - grabDy;
		nx = Math.max(cw / 2f + 4f, Math.min(w - cw / 2f - 4f, nx));
		ny = Math.max(ch / 2f + 4f, Math.min(h - ch / 2f - 4f, ny));
		boolean snapX = Math.abs(nx - w / 2f) < SNAP;
		boolean snapY = Math.abs(ny - h / 2f) < SNAP;
		if (snapX) {
			nx = w / 2f;
		}
		if (snapY) {
			ny = h / 2f;
		}
		guideX.to(snapX);
		guideY.to(snapY);
		settings.posX.set((double) (nx / w));
		settings.posY.set((double) (ny / h));
	}

	public void mouseReleased() {
		dragging = false;
		guideX.to(false);
		guideY.to(false);
	}

	public boolean keyPressed(int key) {
		if (key == Keys.ESCAPE || key == Keys.ENTER) {
			finish();
			return true;
		}
		return false;
	}

	private void finish() {
		if (!closing) {
			closing = true;
			host.sound("close");
			host.saveConfig();
		}
	}

	@SuppressWarnings("unused")
	private static float pulse() {
		return (float) (0.5 + 0.5 * Math.sin(Clock.seconds() * 4.0));
	}
}
