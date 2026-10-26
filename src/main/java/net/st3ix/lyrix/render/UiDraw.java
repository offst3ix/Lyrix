package net.st3ix.lyrix.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class UiDraw {
	public static final int GREEN = 0xFF1DB954;
	public static final int GREEN_LIGHT = 0xFF1ED760;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFFB3B3B3;
	public static final int DIM = 0xFF727272;
	public static final int CARD = 0xFF242424;
	public static final int CARD_HOVER = 0xFF2E2E2E;
	public static final int RED = 0xFFE5534B;

	private UiDraw() {
	}

	public static void roundRect(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int color) {
		if ((color >>> 24) == 0 || x2 <= x1 || y2 <= y1) return;
		int w = x2 - x1, h = y2 - y1;
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		Identifier circle = Icons.get(Icons.Icon.CIRCLE);
		if (r == 0) {
			g.fill(x1, y1, x2, y2, color);
			return;
		}
		if (circle == null) {
			steppedRoundRect(g, x1, y1, x2, y2, r, color);
			return;
		}
		int half = Icons.SIZE / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, circle, x1, y1, 0, 0, r, r, half, half, Icons.SIZE, Icons.SIZE, color);
		g.blit(RenderPipelines.GUI_TEXTURED, circle, x2 - r, y1, half, 0, r, r, half, half, Icons.SIZE, Icons.SIZE, color);
		g.blit(RenderPipelines.GUI_TEXTURED, circle, x1, y2 - r, 0, half, r, r, half, half, Icons.SIZE, Icons.SIZE, color);
		g.blit(RenderPipelines.GUI_TEXTURED, circle, x2 - r, y2 - r, half, half, r, r, half, half, Icons.SIZE, Icons.SIZE, color);
		g.fill(x1 + r, y1, x2 - r, y1 + r, color);
		g.fill(x1, y1 + r, x2, y2 - r, color);
		g.fill(x1 + r, y2 - r, x2 - r, y2, color);
	}

	private static void steppedRoundRect(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int color) {
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
			g.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
			g.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
		}
		g.fill(x1, y1 + r, x2, y2 - r, color);
	}

	public static void shadow(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int strength) {
		for (int i = 4; i >= 1; i--) {
			int a = strength / (i + 1);
			roundRect(g, x1 - i, y1 - i + 2, x2 + i, y2 + i + 2, r + i, a << 24);
		}
	}

	public static void circle(GuiGraphics g, int cx, int cy, int radius, int color) {
		Identifier tex = Icons.get(Icons.Icon.CIRCLE);
		if (tex == null) {
			steppedRoundRect(g, cx - radius, cy - radius, cx + radius, cy + radius, radius, color);
			return;
		}
		int d = radius * 2;
		g.blit(RenderPipelines.GUI_TEXTURED, tex, cx - radius, cy - radius, 0, 0, d, d, Icons.SIZE, Icons.SIZE, Icons.SIZE, Icons.SIZE, color);
	}

	public static void circleF(GuiGraphics g, float cx, float cy, float radius, int color) {
		Identifier tex = Icons.get(Icons.Icon.CIRCLE);
		if (tex == null) {
			circle(g, Math.round(cx), Math.round(cy), Math.max(1, Math.round(radius)), color);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(cx - radius, cy - radius);
		float s = radius * 2 / 16f;
		g.pose().scale(s, s);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0, 0, 16, 16, Icons.SIZE, Icons.SIZE, Icons.SIZE, Icons.SIZE, color);
		g.pose().popMatrix();
	}

	public static void icon(GuiGraphics g, Icons.Icon icon, int x, int y, int size, int color) {
		Identifier tex = Icons.get(icon);
		if (tex == null) {
			String fallback = switch (icon) {
				case PLAY -> "▶";
				case PAUSE -> "||";
				case NEXT -> ">>";
				case PREV -> "<<";
				case PLUS -> "+";
				case CLOSE -> "x";
				default -> "•";
			};
			Component c = Component.literal(fallback);
			g.drawString(font(), c, x + size / 2 - font().width(c) / 2, y + size / 2 - 4, color, false);
			return;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, size, size, Icons.SIZE, Icons.SIZE, Icons.SIZE, Icons.SIZE, color);
	}

	public static void progressBar(GuiGraphics g, int x1, int y, int x2, int h, float progress, int bg, int fg) {
		roundRect(g, x1, y, x2, y + h, h / 2, bg);
		int fx = x1 + Math.round((x2 - x1) * clamp01(progress));
		if (fx - x1 >= h) roundRect(g, x1, y, fx, y + h, h / 2, fg);
		else if (fx > x1) circleF(g, x1 + h / 2f, y + h / 2f, h / 2f, fg);
	}

	public static void equalizer(GuiGraphics g, int x, int y, int h, int color, boolean playing) {
		double t = System.currentTimeMillis() / 1000.0;
		for (int i = 0; i < 3; i++) {
			double v = playing ? 0.35 + 0.65 * Math.abs(Math.sin(t * (3.1 + i * 1.7) + i * 1.3)) : 0.3;
			int bh = Math.max(2, (int) Math.round(h * v));
			roundRect(g, x + i * 3, y + h - bh, x + i * 3 + 2, y + h, 1, color);
		}
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	public static void text(GuiGraphics g, String s, int x, int y, int color) {
		g.drawString(font(), Fonts.ui(s), x, y, color, false);
	}

	public static void textCentered(GuiGraphics g, String s, int cx, int y, int color) {
		Component c = Fonts.ui(s);
		g.drawString(font(), c, cx - font().width(c) / 2, y, color, false);
	}

	public static void textRight(GuiGraphics g, String s, int right, int y, int color) {
		Component c = Fonts.ui(s);
		g.drawString(font(), c, right - font().width(c), y, color, false);
	}

	public static int width(String s) {
		return font().width(Fonts.ui(s));
	}

	public static String ellipsize(String s, int maxWidth) {
		if (s == null) return "";
		if (width(s) <= maxWidth) return s;
		String dots = "...";
		int end = s.length();
		while (end > 0 && width(s.substring(0, end) + dots) > maxWidth) end--;
		return s.substring(0, end) + dots;
	}

	public static void scaledText(GuiGraphics g, Component c, float x, float y, float scale, int argb, boolean shadow) {
		int alpha = argb >>> 24;
		if (alpha < 5) return;
		if (shadow) {
			float off = Math.max(0.6f, 0.9f * scale);
			int shadowColor = (Math.round(alpha * 0.55f) << 24);
			drawAt(g, c, x + off, y + off, scale, shadowColor);
			int soft = (Math.round(alpha * 0.25f) << 24);
			drawAt(g, c, x + off * 1.8f, y + off * 1.8f, scale, soft);
		}
		drawAt(g, c, x, y, scale, argb);
	}

	private static void drawAt(GuiGraphics g, Component c, float x, float y, float scale, int argb) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.drawString(font(), c, 0, 0, argb, false);
		g.pose().popMatrix();
	}

	public static void cover(GuiGraphics g, Identifier tex, int x, int y, int size) {
		if (tex != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, size, size, CoverTextures.SIZE, CoverTextures.SIZE, CoverTextures.SIZE, CoverTextures.SIZE);
		} else {
			roundRect(g, x, y, x + size, y + size, Math.max(2, size / 9), 0xFF2E2E2E);
			int is = size / 2;
			icon(g, Icons.Icon.NOTE, x + (size - is) / 2, y + (size - is) / 2, is, 0xFF5A5A5A);
		}
	}

	public static int withAlpha(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * clamp01(alpha));
		return (a << 24) | (color & 0xFFFFFF);
	}

	public static int mix(int a, int b, float t) {
		t = clamp01(t);
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
				| (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
	}

	public static float clamp01(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	public static String time(long ms) {
		long sec = Math.max(0, ms / 1000);
		return (sec / 60) + ":" + String.format("%02d", sec % 60);
	}

	public static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
		return mx >= x1 && mx < x2 && my >= y1 && my < y2;
	}
}
