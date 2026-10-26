package net.st3ix.lyrix.render;

import net.st3ix.lyrix.LyrixClient;
import net.st3ix.lyrix.config.ModConfig;
import net.st3ix.lyrix.gui.LyrixScreen;
import net.st3ix.lyrix.lyrics.Lyrics;
import net.st3ix.lyrix.lyrics.LyricsManager;
import net.st3ix.lyrix.spotify.PlaybackState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class LyricsHud implements HudElement {
	private static final int MARGIN = 6;
	private static final long TOAST_MS = 650;

	@Override
	public void render(GuiGraphics g, DeltaTracker delta) {
		ModConfig cfg = ModConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof LyrixScreen) return;

		LyrixClient.world().renderHud(g);
		PlaybackState state = LyrixClient.spotify().current();
		if (state == null) return;

		int[] card = drawCard(g, state, cfg);
		if (cfg.showSyncInfo) drawSyncInfo(g, state, card, cfg);
		if (!cfg.enabled) return;

		LyricsManager lm = LyrixClient.lyrics();
		Lyrics lyrics = lm.lyrics();
		boolean ready = lyrics != null && state.trackId().equals(lm.trackId());
		if (!ready) {
			if (cfg.showLyricsStatus && card != null && lm.status() != LyricsManager.Status.NONE) {
				drawStatusPill(g, lm.statusText(), card, cfg);
			}
			return;
		}
		long t = state.progressNow() + cfg.offsetFor(state.trackId());
		switch (cfg.displayMode) {
			case TOP -> drawTop(g, lyrics, t, cfg);
			case LEFT -> drawSide(g, lyrics, t, cfg, false);
			case RIGHT -> drawSide(g, lyrics, t, cfg, true);
			default -> {
			}
		}
	}

	private void drawSyncInfo(GuiGraphics g, PlaybackState s, int[] card, ModConfig cfg) {
		var sp = LyrixClient.spotify();
		LyricsManager lm = LyrixClient.lyrics();
		long drift = sp.lastDriftMs();
		Long match = lm.matchDiffMs(s.trackId());
		int song = cfg.songOffset(s.trackId());
		int total = cfg.offsetFor(s.trackId());

		String[] parts = {
				"Pos " + timeTenths(s.progressNow()),
				"Drift " + seconds(drift),
				"Ping " + sp.lastRttMs() + " ms",
				"Offset " + seconds(total) + (song != 0 ? " (song " + seconds(song) + ")" : ""),
				"Match " + (match == null ? "-" : seconds(match))
		};
		int[] colors = {
				0xFFD0D0D0,
				Math.abs(drift) < 300 ? UiDraw.GREEN_LIGHT : Math.abs(drift) < 1000 ? 0xFFFFC857 : UiDraw.RED,
				sp.lastRttMs() < 400 ? UiDraw.GREEN_LIGHT : 0xFFFFC857,
				total == 0 ? 0xFFD0D0D0 : 0xFF7AA7FF,
				match == null ? 0xFF8A8A8A : Math.abs(match) < 1000 ? UiDraw.GREEN_LIGHT : Math.abs(match) < 2500 ? 0xFFFFC857 : UiDraw.RED
		};

		float sc = 0.72f;
		String sep = "   ";
		int textW = 0;
		for (String p : parts) textW += UiDraw.width(p);
		textW += UiDraw.width(sep) * (parts.length - 1);
		int w = Math.round(textW * sc) + 14, h = 12;
		boolean bottom = cfg.cardCorner == ModConfig.Corner.BOTTOM_LEFT || cfg.cardCorner == ModConfig.Corner.BOTTOM_RIGHT;
		boolean right = cfg.cardCorner == ModConfig.Corner.TOP_RIGHT || cfg.cardCorner == ModConfig.Corner.BOTTOM_RIGHT;
		int x, y;
		if (card != null) {
			x = right ? card[2] - w : card[0];
			y = bottom ? card[1] - h - 20 : card[3] + 20;
		} else {
			x = MARGIN;
			y = MARGIN;
		}
		x = Math.max(2, Math.min(x, g.guiWidth() - w - 2));
		UiDraw.roundRect(g, x, y, x + w, y + h, 6, 0xC0101010);
		float cx = x + 7;
		for (int i = 0; i < parts.length; i++) {
			UiDraw.scaledText(g, Fonts.ui(parts[i]), cx, y + 3, sc, colors[i], false);
			cx += UiDraw.width(parts[i] + (i < parts.length - 1 ? sep : "")) * sc;
		}
	}

	private static String seconds(long ms) {
		return (ms > 0 ? "+" : ms < 0 ? "-" : "") + String.format(java.util.Locale.ROOT, "%.2f s", Math.abs(ms) / 1000.0);
	}

	private static String timeTenths(long ms) {
		long t = Math.max(0, ms);
		return (t / 60000) + ":" + String.format(java.util.Locale.ROOT, "%04.1f", (t % 60000) / 1000.0);
	}

	private int[] drawCard(GuiGraphics g, PlaybackState s, ModConfig cfg) {
		if (!cfg.showCard) return null;
		boolean full = cfg.cardStyle == ModConfig.CardStyle.FULL;
		int w = full ? 196 : 150, h = full ? 54 : 24;
		boolean right = cfg.cardCorner == ModConfig.Corner.TOP_RIGHT || cfg.cardCorner == ModConfig.Corner.BOTTOM_RIGHT;
		boolean bottom = cfg.cardCorner == ModConfig.Corner.BOTTOM_LEFT || cfg.cardCorner == ModConfig.Corner.BOTTOM_RIGHT;

		float scale = cfg.cardScale;
		float anchorX = right ? g.guiWidth() - MARGIN : MARGIN;
		float anchorY = bottom ? g.guiHeight() - MARGIN : MARGIN;
		g.pose().pushMatrix();
		g.pose().translate(anchorX, anchorY);
		g.pose().scale(scale, scale);
		int[] local = drawCardLocal(g, s, cfg, full, w, h, right ? -w : 0, bottom ? -h : 0, right);
		g.pose().popMatrix();
		return new int[]{
				Math.round(anchorX + local[0] * scale), Math.round(anchorY + local[1] * scale),
				Math.round(anchorX + local[2] * scale), Math.round(anchorY + local[3] * scale)};
	}

	private int[] drawCardLocal(GuiGraphics g, PlaybackState s, ModConfig cfg, boolean full, int w, int h, int x, int y, boolean right) {
		long since = System.currentTimeMillis() - LyrixClient.spotify().trackChangedAt();
		float in = cfg.songToast ? TextAnim.easeOutCubic(UiDraw.clamp01(since / (float) TOAST_MS)) : 1;
		int slide = Math.round((1 - in) * (w + MARGIN * 2));
		x += right ? slide : -slide;
		float alpha = cfg.cardOpacity * (0.3f + 0.7f * in);

		int base = 0xFF121212;
		int tint = cfg.cardTinted ? LyrixClient.covers().color(s.coverUrl(), base) : base;
		int r = full ? 10 : 8;
		UiDraw.shadow(g, x, y, x + w, y + h, r, Math.round(70 * alpha));
		UiDraw.roundRect(g, x, y, x + w, y + h, r, UiDraw.withAlpha(base, alpha));
		if (tint != base) UiDraw.roundRect(g, x, y, x + w, y + h, r, UiDraw.withAlpha(tint, alpha * 0.85f));

		var cover = LyrixClient.covers().get(s.coverUrl());
		Boolean liked = LyrixClient.spotify().liked();
		if (full) {
			int cs = h - 12;
			UiDraw.cover(g, cover, x + 6, y + 6, cs);
			int tx = x + cs + 13, tw = w - cs - 21;
			boolean showLabel = cfg.songToast && since < 4000;
			int titleW = tw - (Boolean.TRUE.equals(liked) ? 12 : 0) - 10;
			if (showLabel) {
				float la = since < 3500 ? 1 : 1 - (since - 3500) / 500f;
				UiDraw.scaledText(g, Fonts.ui("NOW PLAYING"), tx, y + 6, 0.7f, UiDraw.withAlpha(UiDraw.GREEN_LIGHT, la), false);
			} else {
				UiDraw.scaledText(g, Fonts.ui(UiDraw.ellipsize(s.deviceName().isEmpty() ? "Spotify" : s.deviceName(), (int) (tw / 0.7f))),
						tx, y + 6, 0.7f, 0xB0FFFFFF, false);
			}
			UiDraw.text(g, UiDraw.ellipsize(s.title(), titleW), tx, y + 14, UiDraw.WHITE);
			if (Boolean.TRUE.equals(liked)) {
				UiDraw.icon(g, Icons.Icon.HEART_FILLED, tx + Math.min(UiDraw.width(s.title()), titleW) + 4, y + 14, 8, UiDraw.GREEN_LIGHT);
			}
			UiDraw.text(g, UiDraw.ellipsize(s.artist(), tw), tx, y + 25, 0xFFC8C8C8);
			UiDraw.equalizer(g, x + w - 14, y + 6, 8, s.playing() ? UiDraw.GREEN_LIGHT : 0x80FFFFFF, s.playing());

			float p = s.durationMs() > 0 ? (float) s.progressNow() / s.durationMs() : 0;
			int barY = y + 40;
			UiDraw.progressBar(g, tx, barY, x + w - 8, 3, p, 0x40FFFFFF, UiDraw.WHITE);
			UiDraw.scaledText(g, Fonts.ui(UiDraw.time(s.progressNow())), tx, barY + 5, 0.62f, 0xA0FFFFFF, false);
			String total = UiDraw.time(s.durationMs());
			float tw2 = UiDraw.width(total) * 0.62f;
			UiDraw.scaledText(g, Fonts.ui(total), x + w - 8 - tw2, barY + 5, 0.62f, 0xA0FFFFFF, false);
		} else {
			UiDraw.cover(g, cover, x + 4, y + 4, h - 8);
			int tx = x + h + 2, tw = w - h - 18;
			String line = s.title() + "  ·  " + s.artist();
			UiDraw.text(g, UiDraw.ellipsize(line, tw), tx, y + 6, UiDraw.WHITE);
			UiDraw.equalizer(g, x + w - 12, y + 7, 8, s.playing() ? UiDraw.GREEN_LIGHT : 0x80FFFFFF, s.playing());
			float p = s.durationMs() > 0 ? (float) s.progressNow() / s.durationMs() : 0;
			UiDraw.progressBar(g, tx, y + h - 6, x + w - 8, 2, p, 0x40FFFFFF, UiDraw.GREEN_LIGHT);
		}
		return new int[]{x, y, x + w, y + h};
	}

	private void drawStatusPill(GuiGraphics g, String text, int[] card, ModConfig cfg) {
		boolean bottom = cfg.cardCorner == ModConfig.Corner.BOTTOM_LEFT || cfg.cardCorner == ModConfig.Corner.BOTTOM_RIGHT;
		boolean right = cfg.cardCorner == ModConfig.Corner.TOP_RIGHT || cfg.cardCorner == ModConfig.Corner.BOTTOM_RIGHT;
		int w = Math.round(UiDraw.width(text) * 0.75f) + 14, h = 12;
		int x = right ? card[2] - w : card[0];
		int y = bottom ? card[1] - h - 4 : card[3] + 4;
		UiDraw.roundRect(g, x, y, x + w, y + h, 6, 0xA0121212);
		UiDraw.scaledText(g, Fonts.ui(text), x + 7, y + 3, 0.75f, 0xFFB3B3B3, false);
	}

	private void drawTop(GuiGraphics g, Lyrics lyrics, long t, ModConfig cfg) {
		Font font = UiDraw.font();
		int cx = g.guiWidth() / 2;
		float y = 24;
		int li = lyrics.lineAt(t);
		if (li < 0) return;
		Lyrics.Line line = lyrics.lines().get(li);
		float scale = cfg.hudScale;

		if (cfg.textMode == ModConfig.TextMode.WORD) {
			int wi = line.wordAt(t);
			if (wi < 0) return;
			Lyrics.Word word = line.words().get(wi);
			String full = Fonts.lyricString(word.text());
			long fadeAge = t > word.end() ? t - word.end() : -1;
			TextAnim a = TextAnim.compute(cfg.effect, t - word.start(), fadeAge, full.length(),
					Math.min(word.end() - word.start(), full.length() * 45L));
			float s = scale * 1.6f * a.scale();
			Component shown = Fonts.lyric(word.text().substring(0, Math.min(word.text().length(), a.visibleChars())));
			float fullW = font.width(Fonts.lyric(word.text()));
			if (cfg.lyricsBackground) {
				float bw = fullW * scale * 1.6f + 24, bh = 9 * scale * 1.6f + 12;
				UiDraw.roundRect(g, Math.round(cx - bw / 2), Math.round(y - 6), Math.round(cx + bw / 2), Math.round(y - 6 + bh),
						Math.round(bh / 2), UiDraw.withAlpha(0xFF000000, cfg.lyricsBackgroundOpacity * a.alpha()));
			}
			UiDraw.scaledText(g, shown, cx - fullW * s / 2f, y + a.offsetY() + (1 - a.scale()) * 4,
					s, TextAnim.argb(a.alpha(), cfg.textColor), cfg.shadow);
		} else {
			float maxW = g.guiWidth() * 0.8f;
			float w = font.width(Fonts.lyric(line.text()));
			float s = Math.min(scale, maxW / Math.max(1, w));
			long fadeAge = fadeAge(lyrics, li, t);
			TextAnim a = TextAnim.compute(cfg.effect, t - line.start(), fadeAge, line.text().length(), 0);
			float lineH = 9 * s;
			Lyrics.Line next = li + 1 < lyrics.lines().size() ? lyrics.lines().get(li + 1) : null;
			float ns = s * 0.6f;
			if (cfg.lyricsBackground) {
				float bw = w * s + 28;
				if (next != null) bw = Math.max(bw, font.width(Fonts.lyric(next.text())) * ns + 28);
				float bh = lineH + 14 + (next != null ? 9 * ns + 4 : 0);
				UiDraw.roundRect(g, Math.round(cx - bw / 2), Math.round(y - 7), Math.round(cx + bw / 2), Math.round(y - 7 + bh),
						12, UiDraw.withAlpha(0xFF000000, cfg.lyricsBackgroundOpacity * a.alpha()));
			}
			drawKaraokeLine(g, line, t, cx - w * s * a.scale() / 2f, y + a.offsetY(), s * a.scale(), a.alpha(), cfg);
			if (next != null) {
				Component nc = Fonts.lyric(next.text());
				UiDraw.scaledText(g, nc, cx - font.width(nc) * ns / 2f, y + lineH + 4, ns,
						TextAnim.argb(0.5f * a.alpha(), 0xFFFFFF), cfg.shadow);
			}
		}
	}

	private void drawSide(GuiGraphics g, Lyrics lyrics, long t, ModConfig cfg, boolean right) {
		Font font = UiDraw.font();
		int sw = g.guiWidth(), sh = g.guiHeight();
		int maxW = (int) (sw * 0.34f);
		int current = lyrics.lineAt(t);
		int anchor = current >= 0 ? current : lyrics.lastStartedLine(t);

		float scale = cfg.hudScale * 0.72f;
		float lineH = 12 * scale;
		int padding = 9;
		int rows = 5;
		int boxH = Math.round(rows * lineH + padding * 2);
		int boxY = sh / 2 - boxH / 2 - 10;
		int boxX1 = right ? sw - 10 - maxW : 10;
		if (cfg.lyricsBackground) {
			UiDraw.shadow(g, boxX1, boxY, boxX1 + maxW, boxY + boxH, 12, Math.round(60 * cfg.lyricsBackgroundOpacity));
			UiDraw.roundRect(g, boxX1, boxY, boxX1 + maxW, boxY + boxH, 12, UiDraw.withAlpha(0xFF0E0E0E, cfg.lyricsBackgroundOpacity));
		}

		float scroll = 1;
		if (anchor >= 0) {
			float p = UiDraw.clamp01((t - lyrics.lines().get(anchor).start()) / (cfg.animationMs * 1.5f));
			scroll = TextAnim.easeOutCubic(p);
		}
		float offset = (1 - scroll) * lineH;
		float innerW = maxW - padding * 2;

		g.enableScissor(boxX1, boxY + 2, boxX1 + maxW, boxY + boxH - 2);
		for (int r = -2; r <= 3; r++) {
			int idx = anchor < 0 ? r : anchor + r;
			if (idx < 0 || idx >= lyrics.lines().size()) continue;
			float y = boxY + padding + (r + 1) * lineH + offset;
			Lyrics.Line line = lyrics.lines().get(idx);
			float w = font.width(Fonts.lyric(line.text()));
			boolean isCurrent = idx == current;
			float baseS = scale * (isCurrent ? 1f : 0.82f);
			float s = Math.min(baseS, innerW / Math.max(1, w));
			float rowY = y + (lineH - 9 * s) / 2f;
			if (isCurrent) {
				TextAnim a = TextAnim.compute(cfg.effect, t - line.start(), -1, line.text().length(), 0);
				float as = s * a.scale();
				float ax = right ? boxX1 + maxW - padding - w * as : boxX1 + padding;
				drawKaraokeLine(g, line, t, ax, rowY + a.offsetY(), as, a.alpha(), cfg);
			} else {
				int dist = Math.abs(idx - (current >= 0 ? current : anchor));
				float alpha = idx < (current >= 0 ? current : anchor + 1) ? 0.28f : Math.max(0.2f, 0.6f - dist * 0.12f);
				float x = right ? boxX1 + maxW - padding - w * s : boxX1 + padding;
				UiDraw.scaledText(g, Fonts.lyric(line.text()), x, rowY, s, TextAnim.argb(alpha, cfg.textColor), cfg.shadow);
			}
		}
		g.disableScissor();
	}

	private void drawKaraokeLine(GuiGraphics g, Lyrics.Line line, long t, float x, float y, float s, float alpha, ModConfig cfg) {
		Font font = UiDraw.font();
		int wi = line.wordAt(t);
		float space = font.width(Fonts.lyric(" "));
		float cursor = 0;
		for (int i = 0; i < line.words().size(); i++) {
			Lyrics.Word word = line.words().get(i);
			String text = word.text();
			Component full = Fonts.lyric(text);
			float wordW = font.width(full);
			float wx = x + cursor * s;
			cursor += wordW + space;

			if (cfg.effect == ModConfig.Effect.TYPEWRITER) {
				if (i > wi) break;
				Component c = full;
				if (i == wi) {
					float p = UiDraw.clamp01((t - word.start()) / (float) Math.max(1, word.end() - word.start()));
					c = Fonts.lyric(text.substring(0, Math.max(1, (int) Math.ceil(text.length() * p))));
				}
				UiDraw.scaledText(g, c, wx, y, s, TextAnim.argb(alpha, i == wi ? cfg.highlightColor : cfg.textColor), cfg.shadow);
				continue;
			}

			if (i < wi) {
				UiDraw.scaledText(g, full, wx, y, s, TextAnim.argb(alpha, cfg.textColor), cfg.shadow);
			} else if (i > wi) {
				UiDraw.scaledText(g, full, wx, y, s, TextAnim.argb(alpha * 0.4f, cfg.textColor), cfg.shadow);
			} else if (!cfg.smoothKaraoke) {
				UiDraw.scaledText(g, full, wx, y, s, TextAnim.argb(alpha, cfg.highlightColor), cfg.shadow);
			} else {
				float p = UiDraw.clamp01((t - word.start()) / (float) Math.max(1, word.end() - word.start()));
				UiDraw.scaledText(g, full, wx, y, s, TextAnim.argb(alpha * 0.4f, cfg.textColor), cfg.shadow);
				int clipX2 = (int) Math.ceil(wx + wordW * s * p);
				if (clipX2 > wx) {
					g.enableScissor((int) Math.floor(wx) - 2, (int) Math.floor(y) - 3, clipX2, (int) Math.ceil(y + 11 * s) + 3);
					UiDraw.scaledText(g, full, wx, y, s, TextAnim.argb(alpha, cfg.highlightColor), false);
					g.disableScissor();
				}
			}
		}
	}

	private static long fadeAge(Lyrics lyrics, int li, long t) {
		Lyrics.Line line = lyrics.lines().get(li);
		Lyrics.Word last = line.words().get(line.words().size() - 1);
		boolean nextFollowsDirectly = li + 1 < lyrics.lines().size() && lyrics.lines().get(li + 1).start() <= line.end();
		if (nextFollowsDirectly) return -1;
		long fadeStart = Math.max(last.end(), line.end() - ModConfig.get().animationMs);
		return t > fadeStart ? t - fadeStart : -1;
	}
}
