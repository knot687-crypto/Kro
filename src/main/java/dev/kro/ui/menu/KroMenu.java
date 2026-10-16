package dev.kro.ui.menu;

import dev.kro.core.anim.Anim;
import dev.kro.core.anim.Clock;
import dev.kro.core.anim.Ease;
import dev.kro.core.anim.Spring;
import dev.kro.core.module.Module;
import dev.kro.core.module.ModuleRegistry;
import dev.kro.core.module.impl.WorldTheme;
import dev.kro.core.util.Colors;
import dev.kro.core.util.Keys;
import dev.kro.ui.Mouse;
import dev.kro.ui.UiHost;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Icons;
import dev.kro.ui.render.Theme;
import dev.kro.ui.widget.Draw;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kro's menu: one calm landscape window. A top bar holds the brand, horizontal category tabs
 * and search; below it module cards sit in a grid that re-flows on springs, and a module's
 * settings slide in as a two-column page. The window hugs its content and resizes smoothly.
 */
public final class KroMenu {
	public static final float DESIGN_W = 960f;
	public static final float DESIGN_H = 540f;

	static final float HEADER = 56f;
	static final float FOOTER = 36f;
	static final float SIDE = 20f;
	static final float GAP = 12f;
	static final float CARD_H = 146f;
	static final float GRID_TOP = 18f;
	static final float GRID_BOTTOM = 20f;
	private static final float RADIUS = 16f;
	private static final float MAX_W = 744f;
	private static final float MAX_H = 460f;
	/** Narrowest the window gets to make room for a picture beside it. */
	private static final float MIN_W = 660f;
	/** Margins of the row "window, gap, picture subject" when they share the screen. */
	private static final float EDGE_LEFT = 28f;
	private static final float EDGE_GAP = 26f;
	private static final float EDGE_RIGHT = 22f;
	private static final String[] TABS = {"All", "Combat", "Movement", "Visual", "Client"};

	private final ModuleRegistry modules;
	private final UiHost host;
	private final List<ModuleCard> cards = new ArrayList<>();
	private final Mouse mouse = new Mouse();
	private final Spring open = new Spring(0f, 170f, 23f);
	private final Spring pageAnim = new Spring(0f, 170f, 25f);
	private final Spring height = new Spring(0f, 190f, 26f);
	private final Spring tabX = new Spring(0f, Spring.SNAPPY);
	private final Spring tabW = new Spring(0f, Spring.SNAPPY);
	private final Anim[] tabHover = new Anim[TABS.length];
	private final Anim closeHover = new Anim(0f, 16f);
	private final Anim hudHover = new Anim(0f, 16f);
	private final Anim searchFocus = new Anim(0f, 14f);
	private final Anim searchHover = new Anim(0f, 16f);
	private final Anim emptyAnim = new Anim(0f, 12f);
	private final Anim statusSwap = new Anim(1f, 16f);
	private final Spring pictureIn = new Spring(0f, Spring.GENTLE);
	private final Anim drift = new Anim(0f, 2.5f);
	private final long openedAt = Clock.nanos();

	private SettingsPage page;
	private SettingsPage leavingPage;
	private int tab;
	private boolean tabPlaced;
	private boolean heightPlaced;
	private String search = "";
	private boolean searchFocused;
	private boolean closing;
	private boolean closed;
	private float u = 1f;
	private float dw = DESIGN_W;
	private float dh = DESIGN_H;
	private float wx;
	private float wy;
	private float ww;
	private float wh;
	private final float[][] tabRects = new float[TABS.length][];
	private float[] searchRect = new float[4];
	private float closeCenter;
	private float hudCenter;
	private float dim = 0.3f;
	private Wallpaper wallpaper;
	private float picX;
	private float picY;
	private float picW;
	private float picH;
	private ModuleCard pressed;
	private Runnable hudEditor;
	private String status;
	private String shownStatus;
	private String previousStatus;

	public KroMenu(ModuleRegistry modules, UiHost host) {
		this.modules = modules;
		this.host = host;
		List<Module> all = modules.all();
		for (int i = 0; i < all.size(); i++) {
			ModuleCard card = new ModuleCard(all.get(i));
			card.delay = 0.08f + i * 0.03f;
			cards.add(card);
		}
		for (int i = 0; i < TABS.length; i++) {
			tabHover[i] = new Anim(0f, 16f);
		}
		open.to(1f);
	}

	/** Called when the HUD layout button is pressed. */
	public void onEditHud(Runnable action) {
		this.hudEditor = action;
	}

	public void setDim(float dim) {
		this.dim = dim;
	}

	/** The picture behind the menu, or {@code null} to show the world (blurred and dimmed). */
	public void setWallpaper(Wallpaper wallpaper) {
		this.wallpaper = wallpaper;
	}

	public boolean finished() {
		return closed;
	}

	/** Centre of the {@code index}-th module card in GUI coordinates, for automation and previews. */
	public float[] cardCentre(int index) {
		ModuleCard card = cards.get(index);
		return new float[] {(card.x.get() + card.w / 2f) * u, (card.y.get() + card.h / 2f) * u};
	}

	public void requestClose() {
		if (!closing) {
			closing = true;
			open.to(0f);
			host.sound("close");
			host.saveConfig();
		}
	}

	// ------------------------------------------------------------------ render

	public void render(Canvas c, float guiW, float guiH, float gmx, float gmy, float uiScale) {
		u = Math.max(0.45f, Math.min(guiW / DESIGN_W, guiH / DESIGN_H) * uiScale);
		dw = guiW / u;
		dh = guiH / u;
		mouse.x = gmx / u;
		mouse.y = gmy / u;
		status = null;

		float o = Math.max(0f, Math.min(1f, open.get()));
		if (closing && o < 0.015f && !closed) {
			closed = true;
			host.closeScreen();
		}

		c.push();
		c.scale(u);
		ww = Math.min(MAX_W, dw - 48f);
		wx = (dw - ww) / 2f;
		if (wallpaper != null) {
			layoutPicture(c);
			renderPicture(c, o);
		} else {
			pictureIn.snap(0f);
			c.rect(0f, 0f, dw, dh, 0f, Colors.withAlpha(0x000000, dim * o));
		}

		List<ModuleCard> visibleCards = filtered();
		float maxH = Math.min(MAX_H, dh - 48f);
		float target = HEADER + 1f + FOOTER + (page != null ? page.contentHeight() : gridHeight(visibleCards.size()));
		target = Math.min(maxH, target);
		if (!heightPlaced) {
			height.snap(target);
			heightPlaced = true;
		}
		wh = height.to(target).get();
		wy = (dh - maxH) / 2f;

		c.push();
		c.alpha(o);
		float e = Ease.outCubic(o);
		c.translate(0f, (1f - e) * 12f);
		c.scale(0.965f + 0.035f * e, wx + ww / 2f, wy + wh / 2f);
		Draw.window(c, wx, wy, ww, wh, RADIUS);
		c.rect(wx + 1f, wy + 1f, ww - 2f, HEADER, RADIUS - 1f, 0x08FFFFFF, 0x08FFFFFF, 0x00FFFFFF, 0x00FFFFFF);
		renderHeader(c);
		c.rect(wx, wy + HEADER, ww, 1f, 0f, Theme.DIVIDER);
		renderContent(c, visibleCards);
		renderFooter(c);
		c.pop();
		c.pop();
	}

	/**
	 * Sizes the picture, one texel per screen pixel when its texture was made for this screen,
	 * and moves the window aside so a portrait subject stands next to it instead of behind it.
	 * Spare width on wide screens is shared evenly between the margins and the gap.
	 */
	private void layoutPicture(Canvas c) {
		Wallpaper wp = wallpaper;
		float px = u * c.guiScale();
		if (Math.abs(wp.pixelHeight() - dh * px) <= 1.5f) {
			picH = wp.pixelHeight() / px;
			picW = wp.pixelWidth() / px;
		} else {
			picH = dh;
			picW = dh * wp.aspect();
		}
		if (!wp.portrait()) {
			float cover = Math.max(dw / picW, dh / picH);
			picW *= cover;
			picH *= cover;
			picX = (dw - picW) / 2f;
			picY = (dh - picH) / 2f;
			return;
		}
		picY = 0f;
		float subject = (wp.subjectRight() - wp.subjectLeft()) * picW;
		float room = dw - EDGE_LEFT - EDGE_GAP - subject - EDGE_RIGHT;
		float spare = 0f;
		if (room >= MAX_W) {
			ww = MAX_W;
			spare = (room - MAX_W) / 3f;
		} else {
			ww = Math.min(Math.max(MIN_W, room), dw - 48f);
		}
		wx = Math.min(EDGE_LEFT + spare, (dw - ww) / 2f);
		picX = dw - EDGE_RIGHT - spare - wp.subjectRight() * picW;
	}

	/** Black stage, then the picture gliding in from the right with its edges melted into black. */
	private void renderPicture(Canvas c, float o) {
		c.rect(0f, 0f, dw, dh, 0f, Colors.withAlpha(0x000000, o));
		if (wallpaper.image() == null) {
			// still being prepared: hold the reveal until it arrives
			pictureIn.snap(0f);
			return;
		}
		float p = Math.max(0f, Math.min(1f, pictureIn.to(1f).get()));
		float a = o * p;
		if (a < 0.004f) {
			return;
		}
		float e = Ease.outCubic(p);
		float px = u * c.guiScale();
		float shift = drift.to(interactive() ? (0.5f - mouse.x / Math.max(1f, dw)) * 8f : 0f).get();
		float x = Math.round((picX + (1f - e) * 30f + shift) * px) / px;
		float y = Math.round(picY * px) / px;
		c.push();
		c.alpha(a);
		c.image(wallpaper.image(), x, y, picW, picH, 0f, 0xFFFFFFFF, 0f, 0f, 1f, 1f);
		if (wallpaper.portrait()) {
			float left = wallpaper.subjectLeft() * picW * 0.9f;
			c.rectH(x - 1f, y, left + 1f, picH, 0f, 0xFF000000, 0x00000000);
			float right = (1f - Math.min(1f, wallpaper.subjectRight() + 0.02f)) * picW;
			if (right > 0.5f) {
				c.rectH(x + picW - right, y, right + 1f, picH, 0f, 0x00000000, 0xFF000000);
			}
			c.rectV(x - 1f, y + picH * 0.82f, picW + 2f, picH * 0.18f + 1f, 0f, 0x00000000, 0x8C000000);
		} else {
			// keep the window legible over a full-screen picture
			c.rect(0f, 0f, dw, dh, 0f, 0x80000000);
		}
		c.pop();
	}

	private static int rowsFor(int count) {
		return Math.max(1, (count + 3) / 4);
	}

	private static float gridHeight(int count) {
		int rows = rowsFor(count);
		return GRID_TOP + rows * CARD_H + (rows - 1) * GAP + GRID_BOTTOM;
	}

	private boolean interactive() {
		return !closing;
	}

	private void renderHeader(Canvas c) {
		float cy = wy + HEADER / 2f;
		float lx = wx + SIDE;
		c.rect(lx, cy - 11f, 22f, 22f, 7f, Theme.accentLight(), Theme.accent(), Theme.accent(), Theme.accentLight());
		c.outline(lx, cy - 11f, 22f, 22f, 7f, 1f, 0x47FFFFFF, 0x1FFFFFFF, 0x00FFFFFF, 0x1FFFFFFF);
		c.text(Fonts.display(), 13f, "K", lx + 11f, cy, Theme.onAccent(), Canvas.CENTER);
		c.text(Fonts.semibold(), 11.5f, "Kro", lx + 30f, cy, Theme.TEXT);
		float divX = lx + 30f + c.textWidth(Fonts.semibold(), 11.5f, "Kro") + 16f;
		c.rect(divX, cy - 9f, 1f, 18f, 0f, Theme.DIVIDER);

		float tx = divX + 14f;
		for (int i = 0; i < TABS.length; i++) {
			float tw = c.textWidth(Fonts.medium(), 8.5f, TABS[i]) + 24f;
			tabRects[i] = new float[] {tx, cy - 13f, tw, 26f};
			tx += tw + 2f;
		}
		float[] sel = tabRects[tab];
		if (!tabPlaced) {
			tabX.snap(sel[0]);
			tabW.snap(sel[2]);
			tabPlaced = true;
		}
		float ix = tabX.to(sel[0]).get(), iw = tabW.to(sel[2]).get();
		c.rect(ix, cy - 13f, iw, 26f, 8f, 0x17FFFFFF, 0x17FFFFFF, 0x11FFFFFF, 0x11FFFFFF);
		c.outline(ix, cy - 13f, iw, 26f, 8f, 1f, 0x14FFFFFF, 0x14FFFFFF, 0x05FFFFFF, 0x05FFFFFF);
		for (int i = 0; i < TABS.length; i++) {
			float[] r = tabRects[i];
			boolean over = interactive() && mouse.in(r[0], r[1], r[2], r[3]);
			float hv = tabHover[i].to(over && i != tab).get();
			int col = i == tab ? Theme.TEXT : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, hv);
			c.text(Fonts.medium(), 8.5f, TABS[i], r[0] + r[2] / 2f, cy, col, Canvas.CENTER);
		}

		float closeX = wx + ww - SIDE - 13f;
		boolean overClose = interactive() && mouse.inCircle(closeX, cy, 14f);
		float ch = closeHover.to(overClose).get();
		Draw.iconButton(c, Icons.X, closeX, cy, 28f, ch, Theme.TEXT_DIM);
		float hudX = closeX - 32f;
		boolean overHud = interactive() && mouse.inCircle(hudX, cy, 14f);
		float hh = hudHover.to(overHud).get();
		Draw.iconButton(c, Icons.LAYOUT_DASHBOARD, hudX, cy, 28f, hh, Theme.TEXT_DIM);
		if (overClose) {
			status = "Close the menu";
		} else if (overHud) {
			status = "Edit HUD layout: drag the Target HUD where you want it";
		}

		float sw = 168f, sh = 28f;
		float sx = hudX - 18f - sw, sy = cy - sh / 2f;
		float sf = searchFocus.to(searchFocused || !search.isEmpty()).get();
		float shv = searchHover.to(interactive() && mouse.in(sx, sy, sw, sh)).get();
		c.rect(sx, sy, sw, sh, 8f, Colors.lerp(Theme.FIELD, Theme.SURFACE_HOVER, Math.max(shv * 0.6f, sf * 0.5f)));
		c.outline(sx, sy, sw, sh, 8f, 1f, Colors.lerp(Colors.lerp(Theme.BORDER, Theme.BORDER_STRONG, shv), Theme.accent(0.55f), sf));
		c.icon(Icons.SEARCH, sx + 14f, cy, 10f, Colors.lerp(Theme.TEXT_FAINT, Theme.TEXT_DIM, Math.max(shv, sf)));
		float caret;
		if (search.isEmpty()) {
			c.text(Fonts.medium(), 8f, "Search modules", sx + 26f, cy, Theme.TEXT_FAINT);
			caret = sx + 26f;
		} else {
			float tw = c.text(Fonts.medium(), 8f, search, sx + 26f, cy, Theme.TEXT);
			caret = sx + 27f + tw;
		}
		if (searchFocused) {
			float blink = (float) (Math.sin(Clock.seconds() * 6.5) * 0.5 + 0.5);
			c.rect(caret, cy - 5.5f, 1f, 11f, 0f, Theme.accent(0.35f + 0.65f * blink));
		}
		searchRect = new float[] {sx, sy, sw, sh};
		closeCenter = closeX;
		hudCenter = hudX;
	}

	private List<ModuleCard> filtered() {
		List<ModuleCard> out = new ArrayList<>();
		String q = search.toLowerCase(Locale.ROOT).trim();
		for (ModuleCard card : cards) {
			Module m = card.module;
			boolean inTab = tab == 0 || m.category().title().equals(TABS[tab]);
			boolean matches = q.isEmpty() || m.name().toLowerCase(Locale.ROOT).contains(q)
					|| m.category().title().toLowerCase(Locale.ROOT).contains(q)
					|| m.description().toLowerCase(Locale.ROOT).contains(q);
			card.shown = inTab && matches;
			if (card.shown) {
				out.add(card);
			}
		}
		return out;
	}

	private void renderContent(Canvas c, List<ModuleCard> list) {
		float top = wy + HEADER + 1f;
		float bottom = wy + wh - FOOTER;
		float p = Math.max(0f, Math.min(1f, pageAnim.to(page != null ? 1f : 0f).get()));
		if (page == null && p < 0.01f) {
			leavingPage = null;
		}

		c.pushClip(wx, top, ww, bottom - top);
		if (p < 0.995f) {
			c.push();
			c.alpha(1f - p);
			c.translate(-28f * Ease.outCubic(p), 0f);
			renderGrid(c, list, top, bottom, p < 0.5f);
			c.pop();
		}
		SettingsPage visiblePage = page != null ? page : leavingPage;
		if (visiblePage != null && p > 0.005f) {
			c.push();
			c.alpha(p);
			c.translate(28f * (1f - Ease.outCubic(p)), 0f);
			boolean live = interactive() && page != null && p > 0.5f;
			visiblePage.render(c, mouse, wx, top, ww, bottom - top, live);
			if (live) {
				String d = page.hoveredDescription();
				if (d != null) {
					status = d;
				}
			}
			c.pop();
		}
		c.popClip();
	}

	private void renderGrid(Canvas c, List<ModuleCard> list, float top, float bottom, boolean interactive) {
		float areaX = wx + SIDE, areaY = top + GRID_TOP;
		float areaW = ww - SIDE * 2f;
		int cols = 4;
		float cw = (areaW - GAP * (cols - 1)) / cols;
		float elapsed = (Clock.nanos() - openedAt) / 1.0e9f * Clock.speed();
		for (int i = 0; i < list.size(); i++) {
			ModuleCard card = list.get(i);
			float tx = areaX + (i % cols) * (cw + GAP);
			float ty = areaY + (i / cols) * (CARD_H + GAP);
			card.w = cw;
			card.h = CARD_H;
			if (!card.placed) {
				card.x.snap(tx);
				card.y.snap(ty);
				card.placed = true;
			}
			card.x.to(tx);
			card.y.to(ty);
		}
		ModuleCard hoveredCard = null;
		for (ModuleCard card : cards) {
			if (!card.placed) {
				card.w = cw;
				card.h = CARD_H;
				card.x.snap(areaX);
				card.y.snap(areaY);
				card.placed = true;
			}
			boolean ready = elapsed >= card.delay;
			card.visible.to(card.shown && ready ? 1f : 0f);
			card.press.to(pressed == card ? 1f : 0f);
			card.render(c, mouse, interactive && interactive());
			if (interactive && card.contains(mouse)) {
				hoveredCard = card;
			}
		}
		if (hoveredCard != null && status == null) {
			Module m = hoveredCard.module;
			if (m.alwaysOn()) {
				status = "Open Interface settings";
			} else if (hoveredCard.overSwitch(mouse)) {
				status = (m.enabled() ? "Turn off " : "Turn on ") + m.name();
			} else {
				status = "Open " + m.name() + " settings  ·  the switch toggles it";
			}
		}
		float empty = emptyAnim.to(list.isEmpty()).get();
		if (empty > 0.01f) {
			c.push();
			c.alpha(empty);
			float cx = wx + ww / 2f, cy = top + gridHeight(1) / 2f;
			c.circle(cx, cy - 18f, 16f, Theme.FIELD);
			c.icon(Icons.SEARCH, cx, cy - 18f, 13f, Theme.TEXT_DIM);
			c.text(Fonts.semibold(), 10f, "No modules found", cx, cy + 10f, Theme.TEXT, Canvas.CENTER);
			c.text(Fonts.medium(), 8f, "Try a different name or category.", cx, cy + 24f, Theme.TEXT_DIM, Canvas.CENTER);
			c.pop();
		}
	}

	private void renderFooter(Canvas c) {
		float fy = wy + wh - FOOTER;
		c.rect(wx, fy, ww, 1f, 0f, Theme.DIVIDER);
		float cy = fy + FOOTER / 2f;

		// left: live status line, crossfading between messages
		if (!java.util.Objects.equals(status, shownStatus)) {
			previousStatus = shownStatus;
			shownStatus = status;
			statusSwap.snap(0f);
		}
		float s = statusSwap.to(1f).get();
		float right = wx + ww - SIDE;
		String key = page != null ? "Esc" : modules.ui.menuKey.label();
		String verb = page != null ? "to go back" : "to close";
		float verbW = c.textWidth(Fonts.medium(), 7.5f, verb);
		float keyW = c.textWidth(Fonts.semibold(), 7.5f, key) + 12f;
		float maxStatus = ww - SIDE * 2f - verbW - keyW - 40f;
		drawStatus(c, previousStatus, wx + SIDE, cy, 1f - s, maxStatus);
		drawStatus(c, shownStatus, wx + SIDE, cy, s, maxStatus);

		c.text(Fonts.medium(), 7.5f, verb, right, cy, Theme.TEXT_FAINT, Canvas.RIGHT);
		Draw.pill(c, key, right - verbW - 6f, cy, Theme.FIELD, Theme.TEXT_DIM, Canvas.RIGHT);
	}

	private void drawStatus(Canvas c, String text, float x, float cy, float alpha, float maxWidth) {
		if (alpha <= 0.01f) {
			return;
		}
		c.push();
		c.alpha(alpha);
		if (text == null) {
			int active = 0;
			int total = 0;
			for (Module m : modules.all()) {
				if (!m.alwaysOn()) {
					total++;
					if (m.enabled()) {
						active++;
					}
				}
			}
			c.circle(x + 3f, cy, 3f, active > 0 ? Theme.GOOD : Theme.TEXT_FAINT);
			c.text(Fonts.medium(), 7.5f, active + " of " + total + " modules active", x + 11f, cy, Theme.TEXT_DIM);
		} else {
			c.icon(Icons.INFO, x + 4f, cy, 9f, Theme.TEXT_FAINT);
			c.text(Fonts.medium(), 7.5f, c.ellipsize(Fonts.medium(), 7.5f, text, maxWidth), x + 13f, cy, Theme.TEXT_DIM);
		}
		c.pop();
	}

	// ------------------------------------------------------------------ actions

	private void openPage(Module m) {
		page = new SettingsPage(m, host, Theme::palette, preset -> {
			WorldTheme.Look look = WorldTheme.look(preset);
			return new int[] {look.sky(), look.fog()};
		});
		leavingPage = null;
		searchFocused = false;
		host.sound("open");
	}

	private void closePage() {
		if (page != null) {
			leavingPage = page;
			page = null;
			host.sound("close");
		}
	}

	private void selectTab(int i) {
		if (i != tab) {
			tab = i;
			host.sound("click");
		}
		closePage();
	}

	// ------------------------------------------------------------------ input

	private void setMouse(double gx, double gy) {
		mouse.x = (float) gx / u;
		mouse.y = (float) gy / u;
	}

	public boolean mouseClicked(double gx, double gy, int button, boolean doubleClick) {
		setMouse(gx, gy);
		if (closing) {
			return true;
		}
		if (page != null && page.capturing()) {
			page.mouseClicked(mouse, button, doubleClick);
			return true;
		}
		if (!mouse.in(wx, wy, ww, wh)) {
			searchFocused = false;
			return false;
		}
		float cy = wy + HEADER / 2f;
		if (button == 0 && mouse.inCircle(closeCenter, cy, 14f)) {
			requestClose();
			return true;
		}
		if (button == 0 && mouse.inCircle(hudCenter, cy, 14f)) {
			host.sound("click");
			if (hudEditor != null) {
				hudEditor.run();
			}
			return true;
		}
		if (mouse.in(searchRect[0], searchRect[1], searchRect[2], searchRect[3])) {
			searchFocused = true;
			closePage();
			return true;
		}
		searchFocused = false;
		for (int i = 0; i < TABS.length; i++) {
			float[] r = tabRects[i];
			if (r != null && mouse.in(r[0], r[1], r[2], r[3])) {
				selectTab(i);
				return true;
			}
		}
		if (page != null) {
			if (page.mouseClicked(mouse, button, doubleClick) == 2) {
				closePage();
			}
			return true;
		}
		for (ModuleCard card : cards) {
			if (card.contains(mouse)) {
				if (button == 0 && card.overSwitch(mouse)) {
					card.module.toggle();
					host.sound(card.module.enabled() ? "on" : "off");
				} else if (button == 0 || button == 1) {
					pressed = card;
					openPage(card.module);
				}
				return true;
			}
		}
		return true;
	}

	public void mouseReleased(double gx, double gy, int button) {
		setMouse(gx, gy);
		pressed = null;
		if (page != null) {
			page.mouseReleased(mouse, button);
		}
	}

	public void mouseDragged(double gx, double gy, int button) {
		setMouse(gx, gy);
		if (page != null) {
			page.mouseDragged(mouse);
		}
	}

	public boolean mouseScrolled(double gx, double gy, double amount) {
		setMouse(gx, gy);
		return page != null && page.mouseScrolled(mouse, amount);
	}

	public boolean keyPressed(int key, int mods, int menuKey) {
		if (page != null && page.capturing()) {
			return page.keyPressed(key, mods);
		}
		if (key == Keys.ESCAPE) {
			if (!search.isEmpty() || searchFocused) {
				search = "";
				searchFocused = false;
			} else if (page != null) {
				closePage();
			} else {
				requestClose();
			}
			return true;
		}
		if (searchFocused) {
			if (key == Keys.BACKSPACE && !search.isEmpty()) {
				search = search.substring(0, search.length() - 1);
			} else if (key == Keys.ENTER) {
				List<ModuleCard> list = filtered();
				if (!list.isEmpty()) {
					openPage(list.get(0).module);
				}
			}
			return true;
		}
		if (key == menuKey) {
			requestClose();
			return true;
		}
		if (key == Keys.TAB && page == null) {
			selectTab((tab + 1) % TABS.length);
			return true;
		}
		return false;
	}

	public boolean charTyped(int codepoint) {
		if (page != null) {
			return page.capturing();
		}
		if (!searchFocused && Character.isLetterOrDigit(codepoint)) {
			searchFocused = true;
		}
		if (searchFocused && (Character.isLetterOrDigit(codepoint) || codepoint == ' ')) {
			if (search.length() < 24) {
				search += new String(Character.toChars(codepoint));
			}
			return true;
		}
		return false;
	}

	/** True while a text field or key capture wants raw keyboard input. */
	public boolean wantsKeyboard() {
		return searchFocused || (page != null && page.capturing());
	}
}
