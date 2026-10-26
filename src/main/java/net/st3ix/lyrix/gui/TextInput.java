package net.st3ix.lyrix.gui;

import net.st3ix.lyrix.render.UiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

public final class TextInput {
	private String value = "";
	private int cursor;
	private boolean focused;
	private final int maxLength;
	private Runnable onEnter = () -> {
	};
	private Runnable onChange = () -> {
	};

	public TextInput(int maxLength) {
		this.maxLength = maxLength;
	}

	private int padLeft = 10;

	public TextInput padLeft(int p) {
		this.padLeft = p;
		return this;
	}

	public TextInput onEnter(Runnable r) {
		this.onEnter = r;
		return this;
	}

	public TextInput onChange(Runnable r) {
		this.onChange = r;
		return this;
	}

	public String value() {
		return value;
	}

	public void setValue(String v) {
		value = v == null ? "" : v;
		cursor = value.length();
	}

	public boolean focused() {
		return focused;
	}

	public void setFocused(boolean f) {
		focused = f;
		if (f) cursor = value.length();
	}

	public void render(GuiGraphics g, int x, int y, int w, int h, String placeholder, boolean masked) {
		if (focused) UiDraw.roundRect(g, x - 1, y - 1, x + w + 1, y + h + 1, h / 2 + 1, 0xFFFFFFFF);
		UiDraw.roundRect(g, x, y, x + w, y + h, h / 2, focused ? 0xFF2E2E2E : 0xFF242424);
		int tx = x + padLeft, ty = y + (h - 8) / 2;
		int innerW = w - padLeft - 10;
		if (value.isEmpty() && !focused) {
			UiDraw.text(g, placeholder, tx, ty, UiDraw.DIM);
			return;
		}
		String shown = masked ? "•".repeat(value.length()) : value;

		String before = shown.substring(0, Math.min(cursor, shown.length()));
		int start = 0;
		while (start < before.length() && UiDraw.width(before.substring(start)) > innerW - 4) start++;
		String visible = shown.substring(start);
		while (!visible.isEmpty() && UiDraw.width(visible) > innerW) visible = visible.substring(0, visible.length() - 1);
		UiDraw.text(g, visible, tx, ty, UiDraw.WHITE);
		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = tx + UiDraw.width(before.substring(start));
			g.fill(cx, ty - 1, cx + 1, ty + 9, UiDraw.GREEN);
		}
	}

	public boolean keyPressed(KeyEvent e) {
		if (!focused) return false;
		int key = e.key();
		if (e.hasControlDown() && key == GLFW.GLFW_KEY_V) {
			insert(Minecraft.getInstance().keyboardHandler.getClipboard());
			return true;
		}
		if (e.hasControlDown() && key == GLFW.GLFW_KEY_A) {
			cursor = value.length();
			return true;
		}
		switch (key) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (cursor > 0) {
					int from = e.hasControlDown() ? wordStart() : cursor - 1;
					value = value.substring(0, from) + value.substring(cursor);
					cursor = from;
					onChange.run();
				}
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if (cursor < value.length()) {
					value = value.substring(0, cursor) + value.substring(cursor + 1);
					onChange.run();
				}
			}
			case GLFW.GLFW_KEY_LEFT -> cursor = Math.max(0, cursor - 1);
			case GLFW.GLFW_KEY_RIGHT -> cursor = Math.min(value.length(), cursor + 1);
			case GLFW.GLFW_KEY_HOME -> cursor = 0;
			case GLFW.GLFW_KEY_END -> cursor = value.length();
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> onEnter.run();
			case GLFW.GLFW_KEY_ESCAPE -> {
				focused = false;
				return true;
			}
			default -> {
				return key != GLFW.GLFW_KEY_TAB;
			}
		}
		return true;
	}

	public boolean charTyped(CharacterEvent e) {
		if (!focused || !e.isAllowedChatCharacter()) return false;
		insert(e.codepointAsString());
		return true;
	}

	private void insert(String s) {
		if (s == null) return;
		s = s.replaceAll("[\\r\\n\\t]", " ");
		int room = maxLength - value.length();
		if (room <= 0) return;
		if (s.length() > room) s = s.substring(0, room);
		value = value.substring(0, cursor) + s + value.substring(cursor);
		cursor += s.length();
		onChange.run();
	}

	private int wordStart() {
		int i = cursor;
		while (i > 0 && value.charAt(i - 1) == ' ') i--;
		while (i > 0 && value.charAt(i - 1) != ' ') i--;
		return i;
	}
}
