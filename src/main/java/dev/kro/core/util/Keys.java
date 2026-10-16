package dev.kro.core.util;

/**
 * GLFW key codes and display names, kept free of LWJGL so the UI core stays portable.
 * Mouse buttons are encoded as {@code MOUSE_BASE + button}.
 */
public final class Keys {
	public static final int NONE = -1;
	public static final int MOUSE_BASE = 1000;

	public static final int SPACE = 32;
	public static final int ESCAPE = 256;
	public static final int ENTER = 257;
	public static final int TAB = 258;
	public static final int BACKSPACE = 259;
	public static final int DELETE = 261;
	public static final int RIGHT = 262;
	public static final int LEFT = 263;
	public static final int DOWN = 264;
	public static final int UP = 265;
	public static final int LEFT_SHIFT = 340;
	public static final int LEFT_CONTROL = 341;
	public static final int RIGHT_SHIFT = 344;
	public static final int RIGHT_CONTROL = 345;
	public static final int A = 65;
	public static final int F = 70;

	/** GLFW modifier bits. */
	public static final int MOD_SHIFT = 0x1;
	public static final int MOD_CONTROL = 0x2;
	public static final int MOD_ALT = 0x4;

	private Keys() {
	}

	public static boolean isMouse(int code) {
		return code >= MOUSE_BASE;
	}

	public static String name(int code) {
		if (code == NONE) {
			return "None";
		}
		if (code >= MOUSE_BASE) {
			int b = code - MOUSE_BASE;
			return switch (b) {
				case 0 -> "Left Mouse";
				case 1 -> "Right Mouse";
				case 2 -> "Middle Mouse";
				default -> "Mouse " + (b + 1);
			};
		}
		if (code >= 65 && code <= 90) {
			return String.valueOf((char) code);
		}
		if (code >= 48 && code <= 57) {
			return String.valueOf((char) code);
		}
		if (code >= 290 && code <= 314) {
			return "F" + (code - 289);
		}
		if (code >= 320 && code <= 329) {
			return "Num " + (code - 320);
		}
		return switch (code) {
			case 32 -> "Space";
			case 39 -> "'";
			case 44 -> ",";
			case 45 -> "-";
			case 46 -> ".";
			case 47 -> "/";
			case 59 -> ";";
			case 61 -> "=";
			case 91 -> "[";
			case 92 -> "\\";
			case 93 -> "]";
			case 96 -> "`";
			case 256 -> "Esc";
			case 257 -> "Enter";
			case 258 -> "Tab";
			case 259 -> "Backspace";
			case 260 -> "Insert";
			case 261 -> "Delete";
			case 262 -> "Right";
			case 263 -> "Left";
			case 264 -> "Down";
			case 265 -> "Up";
			case 266 -> "Page Up";
			case 267 -> "Page Down";
			case 268 -> "Home";
			case 269 -> "End";
			case 280 -> "Caps Lock";
			case 281 -> "Scroll Lock";
			case 282 -> "Num Lock";
			case 283 -> "Print";
			case 284 -> "Pause";
			case 330 -> "Num .";
			case 331 -> "Num /";
			case 332 -> "Num *";
			case 333 -> "Num -";
			case 334 -> "Num +";
			case 335 -> "Num Enter";
			case 340 -> "L Shift";
			case 341 -> "L Ctrl";
			case 342 -> "L Alt";
			case 343 -> "L Super";
			case 344 -> "R Shift";
			case 345 -> "R Ctrl";
			case 346 -> "R Alt";
			case 347 -> "R Super";
			case 348 -> "Menu";
			default -> "Key " + code;
		};
	}
}
