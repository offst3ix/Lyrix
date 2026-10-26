package net.st3ix.lyrix.gui;

import net.st3ix.lyrix.LyrixClient;
import net.st3ix.lyrix.config.ModConfig;
import net.st3ix.lyrix.lyrics.Lyrics;
import net.st3ix.lyrix.lyrics.LyricsManager;
import net.st3ix.lyrix.render.Fonts;
import net.st3ix.lyrix.render.Icons.Icon;
import net.st3ix.lyrix.render.TextAnim;
import net.st3ix.lyrix.render.UiDraw;
import net.st3ix.lyrix.spotify.Device;
import net.st3ix.lyrix.spotify.PlaybackState;
import net.st3ix.lyrix.spotify.SpotifyAuth;
import net.st3ix.lyrix.spotify.SpotifyService;
import net.st3ix.lyrix.spotify.TrackResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class LyrixScreen extends Screen {
	private enum Tab {
		PLAYER("Now Playing", Icon.HOME), LYRICS("Lyrics", Icon.LYRICS), SEARCH("Search", Icon.SEARCH),
		QUEUE("Queue", Icon.QUEUE), DEVICES("Devices", Icon.DEVICE), SETTINGS("Settings", Icon.SETTINGS), ACCOUNT("Account", Icon.USER),
		DISCORD("Discord", Icon.CHAT);
		final String label;
		final Icon icon;
		Tab(String label, Icon icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	private record Hit(int x1, int y1, int x2, int y2, Runnable action, DoubleConsumer drag, Runnable release) {
	}

	private static final int SIDEBAR = 106;
	private static Tab lastTab = Tab.PLAYER;

	private final List<Hit> hits = new ArrayList<>();
	private final List<Hit> nextHits = new ArrayList<>();
	private Tab tab;
	private long openedAt;
	private long tabChangedAt;
	private Hit dragging;
	private double clickX;

	private final TextInput searchInput = new TextInput(100).padLeft(25);
	private final TextInput clientIdInput = new TextInput(64);
	private List<TrackResult> results = List.of();
	private String searchStatus = "";
	private List<TrackResult> queue = List.of();
	private String queueStatus = "";
	private List<Device> devices = List.of();
	private String devicesStatus = "";
	private int volumeDrag = -1;

	private final Scroll searchScroll = new Scroll(), settingsScroll = new Scroll(), queueScroll = new Scroll(), lyricsScroll = new Scroll();
	private int settingsContentH = 2000;
	private long lyricsUserScrollAt;

	private int clipY1 = Integer.MIN_VALUE, clipY2 = Integer.MAX_VALUE;

	public LyrixScreen() {
		super(Component.literal("Lyrix"));
		tab = LyrixClient.spotify().auth().isLoggedIn() ? lastTab : Tab.ACCOUNT;
		searchInput.onEnter(this::runSearch);
		clientIdInput.setValue(ModConfig.get().clientId);
		clientIdInput.onChange(() -> {
			ModConfig.get().clientId = clientIdInput.value().trim();
			ModConfig.save();
		});
		onTabOpened();
	}

	@Override
	protected void init() {
		openedAt = System.currentTimeMillis();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		lastTab = tab;
		ModConfig.save();
		super.onClose();
	}

	private void switchTab(Tab t) {
		searchInput.setFocused(t == Tab.SEARCH);
		if (t == tab) return;
		tab = t;
		tabChangedAt = System.currentTimeMillis();
		onTabOpened();
	}

	private void onTabOpened() {
		if (tab == Tab.SEARCH) searchInput.setFocused(true);
		if (tab == Tab.QUEUE) refreshQueue();
		if (tab == Tab.DEVICES) refreshDevices();
		if (tab == Tab.LYRICS) lyricsUserScrollAt = 0;
	}

	private int panelW() {
		return Math.min(500, width - 20);
	}

	private int panelH() {
		return Math.min(300, height - 30);
	}

	private int px() {
		return (width - panelW()) / 2;
	}

	private int py() {
		return (height - panelH()) / 2 - 6;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float delta) {
		nextHits.clear();
		float open = TextAnim.easeOutCubic(UiDraw.clamp01((System.currentTimeMillis() - openedAt) / 220f));
		int x = px(), y = py(), w = panelW(), h = panelH();

		g.pose().pushMatrix();
		float s = 0.95f + 0.05f * open;
		g.pose().translate(width / 2f, height / 2f);
		g.pose().scale(s, s);
		g.pose().translate(-width / 2f, -height / 2f);

		UiDraw.shadow(g, x, y, x + w, y + h, 14, 120);
		UiDraw.roundRect(g, x, y, x + w, y + h, 14, 0xFA121212);
		UiDraw.roundRect(g, x, y, x + SIDEBAR, y + h, 14, 0xFF0A0A0A);
		g.fill(x + SIDEBAR - 14, y, x + SIDEBAR, y + h, 0xFF0A0A0A);

		drawSidebar(g, mx, my, x, y, h);

		int cx = x + SIDEBAR + 16, cy = y + 16, cw = w - SIDEBAR - 32, ch = h - 32;

		float tabIn = TextAnim.easeOutCubic(UiDraw.clamp01((System.currentTimeMillis() - tabChangedAt) / 180f));
		g.pose().pushMatrix();
		g.pose().translate(0, (1 - tabIn) * 6);
		switch (tab) {
			case PLAYER -> drawPlayer(g, mx, my, cx, cy, cw, ch);
			case LYRICS -> drawLyrics(g, mx, my, cx, cy, cw, ch);
			case SEARCH -> drawSearch(g, mx, my, cx, cy, cw, ch);
			case QUEUE -> drawQueue(g, mx, my, cx, cy, cw, ch);
			case DEVICES -> drawDevices(g, mx, my, cx, cy, cw, ch);
			case SETTINGS -> drawSettings(g, mx, my, cx, cy, cw, ch);
			case ACCOUNT -> drawAccount(g, mx, my, cx, cy, cw, ch);
			case DISCORD -> drawDiscord(g, mx, my, cx, cy, cw, ch);
		}
		g.pose().popMatrix();

		iconButton(g, mx, my, x + w - 14, y + 14, 8, Icon.CLOSE, false, false, this::onClose);

		String msg = LyrixClient.spotify().message();
		if (!msg.isEmpty()) {
			boolean err = LyrixClient.spotify().messageIsError();
			int mw = Math.min(w - 40, UiDraw.width(msg) + 34);
			int mxx = x + (w - mw) / 2, myy = y + h + 8;
			UiDraw.shadow(g, mxx, myy, mxx + mw, myy + 18, 9, 60);
			UiDraw.roundRect(g, mxx, myy, mxx + mw, myy + 18, 9, err ? 0xF0E5534B : 0xF01DB954);
			UiDraw.icon(g, err ? Icon.CLOSE : Icon.CHECK, mxx + 7, myy + 5, 8, err ? UiDraw.WHITE : 0xFF000000);
			UiDraw.text(g, UiDraw.ellipsize(msg, mw - 30), mxx + 20, myy + 5, err ? UiDraw.WHITE : 0xFF000000);
		}
		g.pose().popMatrix();

		hits.clear();
		hits.addAll(nextHits);
	}

	private void drawSidebar(GuiGraphics g, int mx, int my, int x, int y, int h) {
		UiDraw.circle(g, x + 21, y + 22, 9, UiDraw.GREEN);
		UiDraw.icon(g, Icon.NOTE, x + 15, y + 16, 12, 0xFF000000);
		UiDraw.text(g, "Lyrix", x + 35, y + 14, UiDraw.WHITE);
		UiDraw.scaledText(g, Fonts.ui("BY ST3IX"), x + 35, y + 24, 0.7f, UiDraw.GREEN, false);
		int ty = y + 46;
		int step = Math.min(25, (h - 46 - 32) / Tab.values().length);
		for (Tab t : Tab.values()) {
			boolean active = t == tab;
			boolean hover = UiDraw.inside(mx, my, x + 8, ty, x + SIDEBAR - 8, ty + 22);
			if (active) UiDraw.roundRect(g, x + 8, ty, x + SIDEBAR - 8, ty + 22, 7, 0xFF232323);
			else if (hover) UiDraw.roundRect(g, x + 8, ty, x + SIDEBAR - 8, ty + 22, 7, 0xFF181818);
			int col = active ? UiDraw.WHITE : (hover ? 0xFFDADADA : UiDraw.GRAY);
			UiDraw.icon(g, t.icon, x + 15, ty + 6, 10, active ? UiDraw.GREEN : col);
			UiDraw.text(g, t.label, x + 31, ty + 7, col);
			Tab target = t;
			hit(x + 8, ty, x + SIDEBAR - 8, ty + 22, () -> switchTab(target));
			ty += step;
		}
		boolean loggedIn = LyrixClient.spotify().auth().isLoggedIn();
		UiDraw.roundRect(g, x + 8, y + h - 26, x + SIDEBAR - 8, y + h - 8, 9, 0xFF161616);
		UiDraw.circle(g, x + 18, y + h - 17, 3, loggedIn ? UiDraw.GREEN : UiDraw.RED);
		UiDraw.scaledText(g, Fonts.ui(loggedIn ? "Connected" : "Not connected"), x + 26, y + h - 20, 0.8f, UiDraw.GRAY, false);
	}

	private void drawPlayer(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
		SpotifyService sp = LyrixClient.spotify();
		if (!sp.auth().isLoggedIn()) {
			emptyState(g, mx, my, x, y, w, h, Icon.USER, "Not connected to Spotify yet.", "Connect", () -> switchTab(Tab.ACCOUNT));
			return;
		}
		PlaybackState s = sp.current();
		if (s == null) {
			emptyState(g, mx, my, x, y, w, h, Icon.NOTE, "Nothing is playing on Spotify right now.", "Search a song", () -> switchTab(Tab.SEARCH));
			return;
		}

		int tint = LyrixClient.covers().color(s.coverUrl(), 0xFF202020);
		UiDraw.roundRect(g, x - 6, y - 6, x + w + 6, y + 112, 12, UiDraw.withAlpha(tint, 0.9f));
		g.fillGradient(x - 6, y + 60, x + w + 6, y + 112, 0x00121212, 0xFA121212);

		int cover = 100;
		UiDraw.shadow(g, x, y, x + cover, y + cover, 8, 90);
		UiDraw.cover(g, LyrixClient.covers().get(s.coverUrl()), x, y, cover);
		int tx = x + cover + 14, tw = w - cover - 14;
		UiDraw.scaledText(g, Fonts.ui("NOW PLAYING"), tx, y + 6, 0.75f, 0xD0FFFFFF, false);
		UiDraw.scaledText(g, Fonts.ui(UiDraw.ellipsize(s.title(), (int) ((tw - 22) / 1.6f))), tx, y + 18, 1.6f, UiDraw.WHITE, false);
		UiDraw.text(g, UiDraw.ellipsize(s.artist(), tw), tx, y + 38, 0xFFE0E0E0);
		UiDraw.text(g, UiDraw.ellipsize(s.album(), tw), tx, y + 50, 0xFF9A9A9A);
		if (!s.deviceName().isEmpty()) {
			UiDraw.icon(g, Icon.DEVICE, tx, y + 64, 9, UiDraw.GREEN_LIGHT);
			UiDraw.scaledText(g, Fonts.ui("Playing on " + s.deviceName()), tx + 12, y + 65, 0.8f, UiDraw.GREEN_LIGHT, false);
		}
		Boolean liked = sp.liked();
		if (liked != null) {
			iconButton(g, mx, my, x + w - 10, y + 24, 9, liked ? Icon.HEART_FILLED : Icon.HEART, false, liked, sp::toggleLike);
		}

		int cy = y + cover + 30;
		int mid = x + w / 2;
		iconButton(g, mx, my, mid - 64, cy, 9, Icon.SHUFFLE, false, s.shuffle(), sp::toggleShuffle);
		iconButton(g, mx, my, mid - 32, cy, 10, Icon.PREV, false, false, sp::previous);
		iconButton(g, mx, my, mid, cy, 14, s.playing() ? Icon.PAUSE : Icon.PLAY, true, false, sp::togglePlay);
		iconButton(g, mx, my, mid + 32, cy, 10, Icon.NEXT, false, false, sp::next);
		Icon rep = "track".equals(s.repeat()) ? Icon.REPEAT_ONE : Icon.REPEAT;
		iconButton(g, mx, my, mid + 64, cy, 9, rep, false, !"off".equals(s.repeat()), sp::cycleRepeat);

		int barY = cy + 24, barX1 = x + 30, barX2 = x + w - 30;
		long pos = s.progressNow();
		float p = s.durationMs() > 0 ? (float) pos / s.durationMs() : 0;
		boolean hover = UiDraw.inside(mx, my, barX1, barY - 5, barX2, barY + 8);
		UiDraw.progressBar(g, barX1, barY, barX2, 4, p, 0xFF4D4D4D, hover ? UiDraw.GREEN : UiDraw.WHITE);
		if (hover) UiDraw.circle(g, barX1 + Math.round((barX2 - barX1) * p), barY + 2, 5, UiDraw.WHITE);
		UiDraw.textRight(g, UiDraw.time(pos), barX1 - 6, barY - 2, UiDraw.DIM);
		UiDraw.text(g, UiDraw.time(s.durationMs()), barX2 + 6, barY - 2, UiDraw.DIM);
		long dur = s.durationMs();
		hit(barX1, barY - 5, barX2, barY + 8,
				() -> sp.seek((long) (dur * UiDraw.clamp01((float) ((clickX - barX1) / (barX2 - barX1))))));

		int fy = y + h - 14;
		LyricsManager lm = LyrixClient.lyrics();
		UiDraw.icon(g, Icon.LYRICS, x, fy - 1, 10, lm.status() == LyricsManager.Status.FOUND ? UiDraw.GREEN : UiDraw.DIM);
		UiDraw.text(g, lm.statusText(), x + 14, fy, UiDraw.GRAY);
		if (s.volume() >= 0) {
			int vol = volumeDrag >= 0 ? volumeDrag : s.volume();
			int vx2 = x + w, vx1 = vx2 - 90;
			UiDraw.icon(g, vol == 0 ? Icon.VOLUME_MUTE : Icon.VOLUME, vx1 - 16, fy - 2, 11, UiDraw.GRAY);
			boolean vh = UiDraw.inside(mx, my, vx1 - 4, fy - 4, vx2 + 4, fy + 10);
			UiDraw.progressBar(g, vx1, fy + 2, vx2, 4, vol / 100f, 0xFF4D4D4D, vh || volumeDrag >= 0 ? UiDraw.GREEN : UiDraw.WHITE);
			if (vh || volumeDrag >= 0) UiDraw.circle(g, vx1 + Math.round(90 * vol / 100f), fy + 4, 5, UiDraw.WHITE);
			nextHits.add(new Hit(vx1 - 4, fy - 4, vx2 + 4, fy + 12, null,
					mouseX -> volumeDrag = (int) Math.round(Math.clamp((mouseX - vx1) / 90.0, 0, 1) * 100),
					() -> {
						if (volumeDrag >= 0) sp.setVolume(volumeDrag);
						volumeDrag = -1;
					}));
		}
	}

	private void emptyState(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Icon icon, String text, String button, Runnable action) {
		int cy = y + h / 2 - 34;
		UiDraw.circle(g, x + w / 2, cy, 18, 0xFF1E1E1E);
		UiDraw.icon(g, icon, x + w / 2 - 9, cy - 9, 18, UiDraw.GRAY);
		UiDraw.textCentered(g, text, x + w / 2, cy + 28, UiDraw.GRAY);
		if (button != null) {
			int bw = UiDraw.width(button) + 32;
			button(g, mx, my, x + (w - bw) / 2, cy + 44, bw, 22, button, true, action);
		}
	}

	private void drawLyrics(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
		SpotifyService sp = LyrixClient.spotify();
		PlaybackState s = sp.current();
		LyricsManager lm = LyrixClient.lyrics();
		if (s == null) {
			emptyState(g, mx, my, x, y, w, h, Icon.LYRICS, "Play a song to see its lyrics.", null, null);
			return;
		}
		ModConfig cfg = ModConfig.get();
		UiDraw.scaledText(g, Fonts.ui(UiDraw.ellipsize(s.title(), (int) ((w - 150) / 1.3f))), x, y + 2, 1.3f, UiDraw.WHITE, false);
		UiDraw.text(g, UiDraw.ellipsize(s.artist(), w - 150), x, y + 17, UiDraw.GRAY);

		String id = s.trackId();
		int songOff = cfg.songOffset(id);
		String off = (songOff > 0 ? "+" : "") + songOff + " ms";
		smallButton(g, mx, my, x + w - 126, y + 4, 26, "-0.1", () -> {
			cfg.setSongOffset(id, cfg.songOffset(id) - 100);
			ModConfig.save();
		});
		boolean offHover = UiDraw.inside(mx, my, x + w - 98, y + 4, x + w - 50, y + 18);
		UiDraw.textCentered(g, off, x + w - 74, y + 8, songOff == 0 ? UiDraw.DIM : offHover ? UiDraw.WHITE : UiDraw.GREEN);
		if (songOff != 0) hit(x + w - 98, y + 4, x + w - 50, y + 18, () -> {
			cfg.setSongOffset(id, 0);
			ModConfig.save();
		});
		smallButton(g, mx, my, x + w - 48, y + 4, 26, "+0.1", () -> {
			cfg.setSongOffset(id, cfg.songOffset(id) + 100);
			ModConfig.save();
		});
		String label = cfg.syncOffsetMs == 0 ? "THIS SONG"
				: "THIS SONG  (ALL: " + (cfg.syncOffsetMs > 0 ? "+" : "") + cfg.syncOffsetMs + " ms)";
		float lw = UiDraw.width(label) * 0.6f;
		UiDraw.scaledText(g, Fonts.ui(label), x + w - 74 - lw / 2f, y + 20, 0.6f, UiDraw.DIM, false);

		int listY = y + 34, listH = h - 34;
		UiDraw.roundRect(g, x - 4, listY - 2, x + w + 4, y + h + 2, 10, 0xFF181818);
		Lyrics lyrics = lm.lyrics();
		if (lyrics == null || !s.trackId().equals(lm.trackId())) {
			UiDraw.textCentered(g, lm.statusText(), x + w / 2, listY + listH / 2 - 4, UiDraw.GRAY);
			return;
		}
		long t = s.progressNow() + cfg.offsetFor(s.trackId());
		int current = lyrics.lineAt(t);
		int anchor = current >= 0 ? current : lyrics.lastStartedLine(t);
		int rowH = 16;
		int contentH = lyrics.lines().size() * rowH + listH / 2;
		boolean auto = System.currentTimeMillis() - lyricsUserScrollAt > 3000;
		lyricsScroll.update(contentH, listH);
		if (auto) lyricsScroll.scrollTo(Math.max(0, anchor) * rowH - listH * 0.38);

		g.enableScissor(x - 4, listY, x + w + 4, listY + listH);
		clipY1 = listY;
		clipY2 = listY + listH;
		int ry = listY + 8 - lyricsScroll.pos();
		for (int i = 0; i < lyrics.lines().size(); i++) {
			if (ry + rowH >= listY && ry <= listY + listH) {
				Lyrics.Line line = lyrics.lines().get(i);
				boolean cur = i == current;
				boolean past = i < anchor || (i == anchor && current < 0);
				boolean hov = UiDraw.inside(mx, my, x, ry, x + w, ry + rowH) && my >= listY && my < listY + listH;
				Component c = Fonts.lyric(line.text());
				float sc = Math.min(cur ? 1.15f : 0.95f, (w - 16f) / Math.max(1, font.width(c)));
				int color = cur ? UiDraw.WHITE : hov ? 0xFFDADADA : past ? 0xFF6E6E6E : 0xFF9A9A9A;
				if (cur) UiDraw.roundRect(g, x, ry - 1, x + 3, ry + rowH - 3, 1, UiDraw.GREEN);
				UiDraw.scaledText(g, c, x + 8, ry + (rowH - 9 * sc) / 2f - 1, sc, color, false);
				long start = line.start();
				hit(x, ry, x + w, ry + rowH, () -> sp.seek(Math.max(0, start - cfg.offsetFor(s.trackId()))));
			}
			ry += rowH;
		}
		g.disableScissor();
		clipY1 = Integer.MIN_VALUE;
		clipY2 = Integer.MAX_VALUE;
		if (!auto) {
			int bw = 92;
			button(g, mx, my, x + (w - bw) / 2, y + h - 24, bw, 18, "Back to current", true, () -> lyricsUserScrollAt = 0);
		}
	}

	private void drawSearch(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
		if (!LyrixClient.spotify().auth().isLoggedIn()) {
			emptyState(g, mx, my, x, y, w, h, Icon.SEARCH, "Connect Spotify to search for songs.", "Connect", () -> switchTab(Tab.ACCOUNT));
			return;
		}
		searchInput.render(g, x, y, w - 30, 24, "What do you want to listen to?", false);
		UiDraw.icon(g, Icon.SEARCH, x + 9, y + 7, 10, searchInput.focused() ? UiDraw.WHITE : UiDraw.GRAY);
		hit(x, y, x + w - 30, y + 24, () -> searchInput.setFocused(true));
		iconButton(g, mx, my, x + w - 12, y + 12, 11, Icon.CHEVRON_RIGHT, true, false, this::runSearch);

		int listY = y + 32, listH = h - 32;
		if (results.isEmpty()) {
			emptyState(g, mx, my, x, listY, w, listH, Icon.SEARCH,
					searchStatus.isEmpty() ? "Type a song, artist or album and press Enter." : searchStatus, null, null);
			return;
		}
		trackList(g, mx, my, x, listY, w, listH, results, searchScroll, true,
				(track, index) -> LyrixClient.spotify().play(track));
	}

	private void trackList(GuiGraphics g, int mx, int my, int x, int listY, int w, int listH, List<TrackResult> list, Scroll scroll,
						   boolean queueButton, java.util.function.ObjIntConsumer<TrackResult> onClick) {
		int rowH = 30;
		int contentH = list.size() * rowH;
		scroll.update(contentH, listH);
		g.enableScissor(x - 4, listY, x + w + 4, listY + listH);
		clipY1 = listY;
		clipY2 = listY + listH;
		int ry = listY - scroll.pos();
		SpotifyService sp = LyrixClient.spotify();
		PlaybackState cur = sp.current();
		for (int index = 0; index < list.size(); index++) {
			TrackResult r = list.get(index);
			if (ry + rowH >= listY && ry <= listY + listH) {
				boolean hover = my >= listY && my < listY + listH && UiDraw.inside(mx, my, x, ry, x + w, ry + rowH - 2);
				boolean playing = cur != null && r.uri().equals(cur.trackUri());
				if (hover) UiDraw.roundRect(g, x - 4, ry, x + w + 4, ry + rowH - 2, 8, 0xFF242424);
				UiDraw.cover(g, LyrixClient.covers().get(r.coverUrl()), x, ry + 3, 22);
				if (hover) {
					UiDraw.roundRect(g, x, ry + 3, x + 22, ry + 25, 3, 0x90000000);
					UiDraw.icon(g, Icon.PLAY, x + 6, ry + 9, 10, UiDraw.WHITE);
				}
				int tw = w - 100;
				UiDraw.text(g, UiDraw.ellipsize(r.title(), tw), x + 30, ry + 5, playing ? UiDraw.GREEN : UiDraw.WHITE);
				UiDraw.scaledText(g, Fonts.ui(UiDraw.ellipsize(r.artist(), (int) (tw / 0.85f))), x + 30, ry + 16, 0.85f, UiDraw.GRAY, false);
				UiDraw.textRight(g, UiDraw.time(r.durationMs()), x + w - (queueButton ? 26 : 4), ry + 10, UiDraw.DIM);
				TrackResult track = r;
				int idx = index;
				hit(x, ry, x + w - (queueButton ? 22 : 0), ry + rowH - 2, () -> onClick.accept(track, idx));
				if (queueButton && hover) {
					iconButton(g, mx, my, x + w - 8, ry + 14, 8, Icon.PLUS, false, false, () -> sp.addToQueue(track));
				}
			}
			ry += rowH;
		}
		g.disableScissor();
		clipY1 = Integer.MIN_VALUE;
		clipY2 = Integer.MAX_VALUE;
		scrollbar(g, x + w + 6, listY, listH, scroll.rawPos(), contentH);
	}

	private void runSearch() {
		String q = searchInput.value().trim();
		if (q.isEmpty()) return;
		searchInput.setFocused(false);
		searchStatus = "Searching...";
		results = List.of();
		searchScroll.reset();
		LyrixClient.spotify().search(q,
				list -> Minecraft.getInstance().execute(() -> {
					results = list;
					searchStatus = list.isEmpty() ? "No results found." : "";
				}),
				err -> Minecraft.getInstance().execute(() -> searchStatus = err));
	}

	private void drawQueue(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
		if (!LyrixClient.spotify().auth().isLoggedIn()) {
			emptyState(g, mx, my, x, y, w, h, Icon.QUEUE, "Connect Spotify to see your queue.", "Connect", () -> switchTab(Tab.ACCOUNT));
			return;
		}
		UiDraw.scaledText(g, Fonts.ui("Up next"), x, y + 2, 1.3f, UiDraw.WHITE, false);
		iconButton(g, mx, my, x + w - 30, y + 8, 8, Icon.REPEAT, false, false, this::refreshQueue);
		int listY = y + 26, listH = h - 26;
		if (queue.isEmpty()) {
			emptyState(g, mx, my, x, listY, w, listH, Icon.QUEUE,
					queueStatus.isEmpty() ? "Your queue is empty. Add songs with + in Search." : queueStatus, null, null);
			return;
		}
		trackList(g, mx, my, x, listY, w, listH, queue, queueScroll, false, (track, index) -> {
			queue = new ArrayList<>(queue.subList(Math.min(index + 1, queue.size()), queue.size()));
			queueScroll.reset();
			LyrixClient.spotify().skipTo(track, index + 1, () -> {
				try {
					Thread.sleep(900);
				} catch (InterruptedException ignored) {
				}
				Minecraft.getInstance().execute(this::refreshQueue);
			});
		});
	}

	private void refreshQueue() {
		queueStatus = "Loading...";
		LyrixClient.spotify().loadQueue(
				list -> Minecraft.getInstance().execute(() -> {
					queue = list;
					queueStatus = "";
				}),
				err -> Minecraft.getInstance().execute(() -> queueStatus = err));
	}

	private void drawDevices(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
		if (!LyrixClient.spotify().auth().isLoggedIn()) {
			emptyState(g, mx, my, x, y, w, h, Icon.DEVICE, "Connect Spotify to switch devices.", "Connect", () -> switchTab(Tab.ACCOUNT));
			return;
		}
		UiDraw.scaledText(g, Fonts.ui("Connect to a device"), x, y + 2, 1.3f, UiDraw.WHITE, false);
		iconButton(g, mx, my, x + w - 30, y + 8, 8, Icon.REPEAT, false, false, this::refreshDevices);
		int ry = y + 28;
		if (devices.isEmpty()) {
			emptyState(g, mx, my, x, ry, w, h - 28, Icon.DEVICE,
					devicesStatus.isEmpty() ? "No devices found. Open Spotify on a device." : devicesStatus, null, null);
			return;
		}
		for (Device d : devices) {
			if (ry + 34 > y + h) break;
			boolean hover = UiDraw.inside(mx, my, x, ry, x + w, ry + 32);
			UiDraw.roundRect(g, x, ry, x + w, ry + 32, 9, d.active() ? 0xFF1A2E21 : hover ? UiDraw.CARD_HOVER : UiDraw.CARD);
			UiDraw.circle(g, x + 16, ry + 16, 10, d.active() ? UiDraw.GREEN : 0xFF333333);
			UiDraw.icon(g, Icon.DEVICE, x + 10, ry + 10, 12, d.active() ? 0xFF000000 : UiDraw.GRAY);
			UiDraw.text(g, UiDraw.ellipsize(d.name(), w - 120), x + 34, ry + 7, d.active() ? UiDraw.GREEN_LIGHT : UiDraw.WHITE);
			UiDraw.scaledText(g, Fonts.ui(d.type() + (d.volume() >= 0 ? "  ·  " + d.volume() + "%" : "")), x + 34, ry + 18, 0.8f, UiDraw.GRAY, false);
			if (d.active()) {
				UiDraw.equalizer(g, x + w - 18, ry + 11, 10, UiDraw.GREEN_LIGHT, true);
			} else if (hover) {
				UiDraw.textRight(g, "Play here", x + w - 10, ry + 12, UiDraw.GREEN);
			}
			Device dev = d;
			if (!d.active()) hit(x, ry, x + w, ry + 32, () -> {
				LyrixClient.spotify().transfer(dev);
				devicesStatus = "";
				refreshDevicesLater();
			});
			ry += 36;
		}
	}

	private void refreshDevices() {
		devicesStatus = "Loading...";
		LyrixClient.spotify().loadDevices(
				list -> Minecraft.getInstance().execute(() -> {
					devices = list;
					devicesStatus = "";
				}),
				err -> Minecraft.getInstance().execute(() -> devicesStatus = err));
	}

	private void refreshDevicesLater() {
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(1200);
			} catch (InterruptedException ignored) {
			}
			Minecraft.getInstance().execute(this::refreshDevices);
		}, "Lyrix-DeviceRefresh");
		t.setDaemon(true);
		t.start();
	}

	private static final int[] COLORS = {0xFFFFFF, 0x1DB954, 0xFFE15C, 0xFF6EC7, 0x5CD6FF, 0xFF5C5C, 0xB18CFF, 0xFF9F43};
	private static final String[] COLOR_NAMES = {"White", "Green", "Yellow", "Pink", "Cyan", "Red", "Purple", "Orange"};

	private void drawSettings(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
		ModConfig c = ModConfig.get();
		g.enableScissor(x - 4, y, x + w + 4, y + h);
		clipY1 = y;
		clipY2 = y + h;

		settingsScroll.update(settingsContentH, h);
		int start = y - settingsScroll.pos();
		int ry = start;

		ry = header(g, x, ry, "LYRICS");
		ry = toggle(g, mx, my, x, ry, w, "Show lyrics", () -> c.enabled, v -> c.enabled = v);
		ry = cycle(g, mx, my, x, ry, w, "Position", () -> c.displayMode.label, d -> c.displayMode = next(ModConfig.DisplayMode.values(), c.displayMode, d));
		ry = cycle(g, mx, my, x, ry, w, "Text", () -> c.textMode.label, d -> c.textMode = next(ModConfig.TextMode.values(), c.textMode, d));
		ry = cycle(g, mx, my, x, ry, w, "Effect", () -> c.effect.label, d -> c.effect = next(ModConfig.Effect.values(), c.effect, d));
		ry = toggle(g, mx, my, x, ry, w, "Fade out", () -> c.fadeOut, v -> c.fadeOut = v);
		ry = slider(g, mx, my, x, ry, w, "Animation speed", 80, 800, () -> c.animationMs, v -> c.animationMs = (int) Math.round(v / 10) * 10, v -> (int) v + " ms");
		ry = toggle(g, mx, my, x, ry, w, "Show sync info", () -> c.showSyncInfo, v -> c.showSyncInfo = v);
		ry = slider(g, mx, my, x, ry, w, "Sync offset (all songs)", -2000, 2000, () -> c.syncOffsetMs, v -> c.syncOffsetMs = (int) Math.round(v / 50) * 50,
				v -> ((int) v > 0 ? "+" : "") + (int) v + " ms");
		ry = slider(g, mx, my, x, ry, w, "Word timing", 0, 1, () -> c.wordPacing, v -> c.wordPacing = (float) (Math.round(v * 20) / 20.0),
				v -> v < 0.15 ? "Calm" : v > 0.85 ? "Fast" : Math.round(v * 100) + "%", () -> {
					ModConfig.save();
					LyrixClient.lyrics().reparse();
				});
		ry = toggle(g, mx, my, x, ry, w, "Show lyrics status", () -> c.showLyricsStatus, v -> c.showLyricsStatus = v);

		ry = header(g, x, ry, "TEXT STYLE");
		ry = colorRow(g, mx, my, x, ry, w, "Text color", () -> c.textColor, v -> c.textColor = v);
		ry = colorRow(g, mx, my, x, ry, w, "Highlight color", () -> c.highlightColor, v -> c.highlightColor = v);
		ry = toggle(g, mx, my, x, ry, w, "Uppercase", () -> c.uppercase, v -> c.uppercase = v);
		ry = toggle(g, mx, my, x, ry, w, "TikTok font", () -> c.customFont, v -> c.customFont = v);
		ry = toggle(g, mx, my, x, ry, w, "Soft shadow", () -> c.shadow, v -> c.shadow = v);
		ry = toggle(g, mx, my, x, ry, w, "Smooth karaoke fill", () -> c.smoothKaraoke, v -> c.smoothKaraoke = v);

		ry = header(g, x, ry, "HUD");
		ry = slider(g, mx, my, x, ry, w, "Lyrics size", 0.5, 4, () -> c.hudScale, v -> c.hudScale = (float) (Math.round(v * 10) / 10.0), v -> String.format("%.1fx", v));
		ry = toggle(g, mx, my, x, ry, w, "Lyrics background", () -> c.lyricsBackground, v -> c.lyricsBackground = v);
		ry = slider(g, mx, my, x, ry, w, "Background opacity", 0, 1, () -> c.lyricsBackgroundOpacity,
				v -> c.lyricsBackgroundOpacity = (float) (Math.round(v * 20) / 20.0), v -> Math.round(v * 100) + "%");

		ry = header(g, x, ry, "NOW PLAYING BANNER");
		ry = toggle(g, mx, my, x, ry, w, "Show banner", () -> c.showCard, v -> c.showCard = v);
		ry = slider(g, mx, my, x, ry, w, "Banner size", 0.5, 2, () -> c.cardScale, v -> c.cardScale = (float) (Math.round(v * 20) / 20.0),
				v -> Math.round(v * 100) + "%");
		ry = cycle(g, mx, my, x, ry, w, "Style", () -> c.cardStyle.label, d -> c.cardStyle = next(ModConfig.CardStyle.values(), c.cardStyle, d));
		ry = cycle(g, mx, my, x, ry, w, "Corner", () -> c.cardCorner.label, d -> c.cardCorner = next(ModConfig.Corner.values(), c.cardCorner, d));
		ry = slider(g, mx, my, x, ry, w, "Opacity", 0.2, 1, () -> c.cardOpacity, v -> c.cardOpacity = (float) (Math.round(v * 20) / 20.0), v -> Math.round(v * 100) + "%");
		ry = toggle(g, mx, my, x, ry, w, "Cover color background", () -> c.cardTinted, v -> c.cardTinted = v);
		ry = toggle(g, mx, my, x, ry, w, "Slide in on song change", () -> c.songToast, v -> c.songToast = v);

		ry = header(g, x, ry, "3D TEXT (IN FRONT OF YOU)");
		ry = slider(g, mx, my, x, ry, w, "Distance", 2, 16, () -> c.worldDistance, v -> c.worldDistance = (float) (Math.round(v * 2) / 2.0), v -> String.format("%.1f blocks", v));
		ry = slider(g, mx, my, x, ry, w, "Size", 0.3, 4, () -> c.worldScale, v -> c.worldScale = (float) (Math.round(v * 10) / 10.0), v -> String.format("%.1fx", v));
		ry = slider(g, mx, my, x, ry, w, "Spread", 0, 3, () -> c.worldSpread, v -> c.worldSpread = (float) (Math.round(v * 10) / 10.0), v -> String.format("%.1f", v));
		ry = toggle(g, mx, my, x, ry, w, "Shader-safe (always bright)", () -> c.shaderSafe, v -> c.shaderSafe = v);
		if (!c.shaderSafe) {
			ry = toggle(g, mx, my, x, ry, w, "Visible through walls", () -> c.seeThrough, v -> c.seeThrough = v);
		}

		ry += 8;
		button(g, mx, my, x, ry, 130, 20, "Reset to defaults", false, () -> {
			ModConfig.reset();
			LyrixClient.spotify().info("Settings reset");
		});
		ry += 30;

		g.disableScissor();
		clipY1 = Integer.MIN_VALUE;
		clipY2 = Integer.MAX_VALUE;
		settingsContentH = ry - start;
		scrollbar(g, x + w + 6, y, h, settingsScroll.rawPos(), settingsContentH);
	}

	private static <E> E next(E[] values, E current, int dir) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] == current) return values[Math.floorMod(i + dir, values.length)];
		}
		return values[0];
	}

	private int header(GuiGraphics g, int x, int y, String text) {
		UiDraw.scaledText(g, Fonts.ui(text), x + 2, y + 10, 0.8f, UiDraw.GREEN, false);
		return y + 24;
	}

	private void rowBg(GuiGraphics g, int mx, int my, int x, int y, int w) {
		boolean hover = rowHover(mx, my, x, y, w);
		UiDraw.roundRect(g, x, y, x + w, y + 22, 7, hover ? UiDraw.CARD_HOVER : 0xFF1A1A1A);
	}

	private int toggle(GuiGraphics g, int mx, int my, int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
		boolean on = get.getAsBoolean();
		rowBg(g, mx, my, x, y, w);
		UiDraw.text(g, label, x + 10, y + 7, UiDraw.WHITE);
		int sx = x + w - 34, sy = y + 5;
		UiDraw.roundRect(g, sx, sy, sx + 26, sy + 12, 6, on ? UiDraw.GREEN : 0xFF4A4A4A);
		UiDraw.circle(g, on ? sx + 20 : sx + 6, sy + 6, 4, UiDraw.WHITE);
		hit(x, y, x + w, y + 22, () -> {
			set.accept(!on);
			ModConfig.save();
		});
		return y + 25;
	}

	private int cycle(GuiGraphics g, int mx, int my, int x, int y, int w, String label, Supplier<String> value, IntConsumer change) {
		rowBg(g, mx, my, x, y, w);
		UiDraw.text(g, label, x + 10, y + 7, UiDraw.WHITE);
		String v = value.get();
		int vw = Math.max(UiDraw.width(v) + 38, 100);
		int bx = x + w - vw - 5;
		UiDraw.roundRect(g, bx, y + 3, x + w - 5, y + 19, 8, 0xFF2E2E2E);
		UiDraw.icon(g, Icon.CHEVRON_LEFT, bx + 4, y + 7, 8, UiDraw.GRAY);
		UiDraw.icon(g, Icon.CHEVRON_RIGHT, x + w - 17, y + 7, 8, UiDraw.GRAY);
		UiDraw.textCentered(g, v, bx + vw / 2, y + 7, UiDraw.GREEN_LIGHT);
		int mid = bx + vw / 2;
		hit(bx, y + 3, mid, y + 19, () -> {
			change.accept(-1);
			ModConfig.save();
		});
		hit(mid, y + 3, x + w - 5, y + 19, () -> {
			change.accept(1);
			ModConfig.save();
		});
		return y + 25;
	}

	private int colorRow(GuiGraphics g, int mx, int my, int x, int y, int w, String label, Supplier<Integer> get, IntConsumer set) {
		rowBg(g, mx, my, x, y, w);
		UiDraw.text(g, label, x + 10, y + 7, UiDraw.WHITE);
		int cur = get.get() & 0xFFFFFF;
		int sx = x + w - 8 - COLORS.length * 14;
		for (int i = 0; i < COLORS.length; i++) {
			int cx = sx + i * 14 + 6, cy = y + 11;
			boolean sel = COLORS[i] == cur;
			if (sel) UiDraw.circle(g, cx, cy, 6, UiDraw.WHITE);
			UiDraw.circle(g, cx, cy, sel ? 4 : 5, 0xFF000000 | COLORS[i]);
			int color = COLORS[i];
			hit(cx - 6, cy - 6, cx + 6, cy + 6, () -> {
				set.accept(color);
				ModConfig.save();
			});
		}
		String name = "";
		for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == cur) name = COLOR_NAMES[i];
		UiDraw.textRight(g, name, sx - 6, y + 7, UiDraw.DIM);
		return y + 25;
	}

	private int slider(GuiGraphics g, int mx, int my, int x, int y, int w, String label, double min, double max,
					   DoubleSupplier get, DoubleConsumer set, DoubleFunction<String> format) {
		return slider(g, mx, my, x, y, w, label, min, max, get, set, format, ModConfig::save);
	}

	private int slider(GuiGraphics g, int mx, int my, int x, int y, int w, String label, double min, double max,
					   DoubleSupplier get, DoubleConsumer set, DoubleFunction<String> format, Runnable onRelease) {
		rowBg(g, mx, my, x, y, w);
		boolean hover = rowHover(mx, my, x, y, w);
		UiDraw.text(g, label, x + 10, y + 7, UiDraw.WHITE);
		double v = get.getAsDouble();
		int trackW = Math.min(120, w / 2 - 30);
		int tx2 = x + w - 12, tx1 = tx2 - trackW;
		UiDraw.textRight(g, format.apply(v), tx1 - 10, y + 7, UiDraw.GRAY);
		float p = (float) ((v - min) / (max - min));
		UiDraw.progressBar(g, tx1, y + 9, tx2, 4, p, 0xFF4A4A4A, UiDraw.GREEN);
		UiDraw.circle(g, tx1 + Math.round(trackW * UiDraw.clamp01(p)), y + 11, hover ? 5 : 4, UiDraw.WHITE);
		DoubleConsumer drag = mouseX -> set.accept(min + Math.clamp((mouseX - tx1) / trackW, 0, 1) * (max - min));
		if (y + 22 > clipY1 && y < clipY2) {
			nextHits.add(new Hit(tx1 - 6, Math.max(y, clipY1), tx2 + 6, Math.min(y + 22, clipY2), null, drag, onRelease));
		}
		return y + 25;
	}

	private boolean rowHover(int mx, int my, int x, int y, int w) {
		return my >= clipY1 && my < clipY2 && UiDraw.inside(mx, my, x, y, x + w, y + 22);
	}

	private void scrollbar(GuiGraphics g, int x, int y, int h, double scroll, int contentH) {
		if (contentH <= h) return;
		int thumbH = Math.max(20, h * h / contentH);
		int thumbY = y + (int) ((h - thumbH) * (scroll / (contentH - h)));
		UiDraw.roundRect(g, x, thumbY, x + 3, thumbY + thumbH, 1, 0xFF4A4A4A);
	}

	private void drawAccount(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
		SpotifyAuth auth = LyrixClient.spotify().auth();
		UiDraw.scaledText(g, Fonts.ui("Connect Spotify"), x, y + 2, 1.4f, UiDraw.WHITE, false);
		int ry = y + 24;
		if (auth.isLoggedIn()) {
			UiDraw.roundRect(g, x, ry, x + w, ry + 40, 10, 0xFF1A2E21);
			UiDraw.circle(g, x + 20, ry + 20, 10, UiDraw.GREEN);
			UiDraw.icon(g, Icon.CHECK, x + 14, ry + 14, 12, 0xFF000000);
			UiDraw.text(g, "You are connected.", x + 38, ry + 10, UiDraw.WHITE);
			UiDraw.scaledText(g, Fonts.ui("Press K in game to open this menu."), x + 38, ry + 22, 0.85f, UiDraw.GRAY, false);
			ry += 50;
			button(g, mx, my, x, ry, 110, 22, "Go to player", true, () -> switchTab(Tab.PLAYER));
			button(g, mx, my, x + 118, ry, 110, 22, "Reconnect", false, auth::startLogin);
			button(g, mx, my, x + 236, ry, 90, 22, "Log out", false, auth::logout);
			ry += 32;
			UiDraw.scaledText(g, Fonts.ui("Tip: Reconnect once if the like button does not show up."), x, ry, 0.85f, UiDraw.DIM, false);
			ry += 14;
		} else {
			ry = step(g, x, ry, w, "1", "Open the Spotify Developer Dashboard and create an app (tick \"Web API\").");
			button(g, mx, my, x + 24, ry, 130, 18, "Open dashboard", false,
					() -> Util.getPlatform().openUri(URI.create("https://developer.spotify.com/dashboard")));
			ry += 26;
			ry = step(g, x, ry, w, "2", "Add this Redirect URI to the app:");
			UiDraw.roundRect(g, x + 24, ry, x + w - 70, ry + 18, 9, 0xFF242424);
			UiDraw.text(g, SpotifyAuth.REDIRECT_URI, x + 32, ry + 5, UiDraw.GREEN_LIGHT);
			button(g, mx, my, x + w - 64, ry, 64, 18, "Copy", false, () -> {
				Minecraft.getInstance().keyboardHandler.setClipboard(SpotifyAuth.REDIRECT_URI);
				LyrixClient.spotify().info("Copied to clipboard");
			});
			ry += 26;
			ry = step(g, x, ry, w, "3", "Paste the Client ID of your app here (Ctrl+V):");
			clientIdInput.render(g, x + 24, ry, w - 24, 20, "Client ID", false);
			hit(x + 24, ry, x + w, ry + 20, () -> clientIdInput.setFocused(true));
			ry += 28;
			ry = step(g, x, ry, w, "4", "Click Connect and allow access in your browser.");
			button(g, mx, my, x + 24, ry, 150, 22, "Connect with Spotify", true, auth::startLogin);
			ry += 30;
		}

		int bw = 76;
		UiDraw.roundRect(g, x, ry, x + w, ry + 28, 9, 0xFF1C2433);
		UiDraw.icon(g, Icon.LINK, x + 8, ry + 8, 12, 0xFF7AA7FF);
		UiDraw.text(g, auth.isLoggedIn() ? "Something not working?" : "Not sure how to connect?", x + 26, ry + 5, UiDraw.WHITE);
		UiDraw.scaledText(g, Fonts.ui(UiDraw.ellipsize("The docs walk you through it step by step.", (int) ((w - bw - 40) / 0.8f))),
				x + 26, ry + 16, 0.8f, UiDraw.GRAY, false);
		button(g, mx, my, x + w - bw - 5, ry + 4, bw, 20, "Open guide", false,
				() -> Util.getPlatform().openUri(URI.create(SETUP_GUIDE)));
		ry += 34;
		String status = auth.status();
		if (!status.isEmpty()) {
			UiDraw.text(g, UiDraw.ellipsize(status, w), x, Math.min(ry, y + h - 10), UiDraw.GRAY);
		}
	}

	private static final String SETUP_GUIDE = "https://docs.st3ix.net/docs/plugins/lyrix/spotify-setup";
	private static final String DISCORD_URL = "https://discord.gg/6NQe7SaZtC";
	private static final String DISCORD_FALLBACK = "https://st3ix.net/discord";

	private void drawDiscord(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
		int blurple = 0xFF5865F2;
		UiDraw.roundRect(g, x - 6, y - 6, x + w + 6, y + 96, 12, blurple);
		g.fillGradient(x - 6, y + 50, x + w + 6, y + 96, 0x00121212, 0xFA121212);
		UiDraw.circle(g, x + 34, y + 34, 26, 0xFFFFFFFF);
		UiDraw.icon(g, Icon.CHAT, x + 18, y + 18, 32, blurple);
		UiDraw.scaledText(g, Fonts.ui("Join the St3ix Discord"), x + 72, y + 16, 1.5f, UiDraw.WHITE, false);
		UiDraw.text(g, "Help, updates and news for Lyrix and every St3ix project.", x + 72, y + 36, 0xFFE6E8FF);

		int ry = y + 76;
		String[][] perks = {
				{"Support", "Stuck with the Spotify setup or found a bug? Ask in the support channel."},
				{"Updates", "Get pinged when a new Lyrix version is out."},
				{"Ideas", "Tell us what Lyrix should do next."}
		};
		for (String[] perk : perks) {
			UiDraw.roundRect(g, x, ry, x + w, ry + 28, 9, UiDraw.CARD);
			UiDraw.circle(g, x + 14, ry + 14, 4, blurple);
			UiDraw.text(g, perk[0], x + 26, ry + 5, UiDraw.WHITE);
			UiDraw.scaledText(g, Fonts.ui(UiDraw.ellipsize(perk[1], (int) ((w - 34) / 0.8f))), x + 26, ry + 16, 0.8f, UiDraw.GRAY, false);
			ry += 32;
		}

		ry += 6;
		boolean hover = UiDraw.inside(mx, my, x, ry, x + 140, ry + 24);
		UiDraw.roundRect(g, x, ry, x + 140, ry + 24, 12, hover ? 0xFF6D78F5 : blurple);
		UiDraw.icon(g, Icon.CHAT, x + 12, ry + 6, 12, UiDraw.WHITE);
		UiDraw.text(g, "Join Discord", x + 30, ry + 8, UiDraw.WHITE);
		hit(x, ry, x + 140, ry + 24, () -> Util.getPlatform().openUri(URI.create(DISCORD_URL)));
		button(g, mx, my, x + 148, ry + 2, 90, 20, "Copy link", false, () -> {
			Minecraft.getInstance().keyboardHandler.setClipboard(DISCORD_URL);
			LyrixClient.spotify().info("Discord link copied");
		});

		ry += 32;
		UiDraw.scaledText(g, Fonts.ui("Invite not working?"), x, ry, 0.85f, UiDraw.DIM, false);
		int lx = x + Math.round(UiDraw.width("Invite not working?") * 0.85f) + 6;
		String fb = "st3ix.net/discord";
		boolean lh = UiDraw.inside(mx, my, lx, ry - 2, lx + UiDraw.width(fb), ry + 9);
		UiDraw.scaledText(g, Fonts.ui(fb), lx, ry, 0.85f, lh ? 0xFF8C95FF : 0xFF7983F5, false);
		if (lh) g.fill(lx, ry + 8, lx + Math.round(UiDraw.width(fb) * 0.85f), ry + 9, 0xFF8C95FF);
		hit(lx, ry - 2, lx + UiDraw.width(fb), ry + 9, () -> Util.getPlatform().openUri(URI.create(DISCORD_FALLBACK)));
	}

	private int step(GuiGraphics g, int x, int y, int w, String number, String text) {
		UiDraw.circle(g, x + 8, y + 6, 8, UiDraw.GREEN);
		UiDraw.textCentered(g, number, x + 8, y + 2, 0xFF000000);
		List<FormattedCharSequence> lines = font.split(Fonts.ui(text), w - 24);
		int ly = y + 2;
		for (var line : lines) {
			g.drawString(font, line, x + 24, ly, 0xFFD0D0D0, false);
			ly += 10;
		}
		return Math.max(y + 18, ly + 4);
	}

	private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, boolean primary, Runnable action) {
		boolean hover = UiDraw.inside(mx, my, x, y, x + w, y + h) && my >= clipY1 && my < clipY2;
		int bg = primary ? (hover ? UiDraw.GREEN_LIGHT : UiDraw.GREEN) : (hover ? 0xFF3A3A3A : 0xFF2A2A2A);
		UiDraw.roundRect(g, x, y, x + w, y + h, h / 2, bg);
		UiDraw.textCentered(g, label, x + w / 2, y + (h - 8) / 2, primary ? 0xFF000000 : UiDraw.WHITE);
		hit(x, y, x + w, y + h, action);
	}

	private void smallButton(GuiGraphics g, int mx, int my, int x, int y, int w, String label, Runnable action) {
		boolean hover = UiDraw.inside(mx, my, x, y, x + w, y + 14);
		UiDraw.roundRect(g, x, y, x + w, y + 14, 7, hover ? 0xFF3A3A3A : 0xFF262626);
		UiDraw.scaledText(g, Fonts.ui(label), x + w / 2f - UiDraw.width(label) * 0.4f, y + 4, 0.8f, UiDraw.WHITE, false);
		hit(x, y, x + w, y + 14, action);
	}

	private void iconButton(GuiGraphics g, int mx, int my, int cx, int cy, int r, Icon icon, boolean primary, boolean active, Runnable action) {
		boolean hover = UiDraw.inside(mx, my, cx - r, cy - r, cx + r, cy + r) && my >= clipY1 && my < clipY2;
		if (primary) {
			int rr = hover ? r + 1 : r;
			UiDraw.circle(g, cx, cy, rr, hover ? UiDraw.GREEN_LIGHT : UiDraw.WHITE);
		} else if (hover) {
			UiDraw.circle(g, cx, cy, r, 0xFF2E2E2E);
		}
		int is = Math.round(r * (primary ? 0.9f : 1.05f));
		int color = primary ? 0xFF000000 : active ? UiDraw.GREEN_LIGHT : (hover ? UiDraw.WHITE : UiDraw.GRAY);
		UiDraw.icon(g, icon, cx - is / 2, cy - is / 2, is, color);
		if (active && !primary && icon != Icon.HEART_FILLED) UiDraw.circle(g, cx, cy + r + 2, 1, UiDraw.GREEN_LIGHT);
		hit(cx - r, cy - r, cx + r, cy + r, action);
	}

	private void hit(int x1, int y1, int x2, int y2, Runnable action) {
		int cy1 = Math.max(y1, clipY1), cy2 = Math.min(y2, clipY2);
		if (cy2 <= cy1) return;
		nextHits.add(new Hit(x1, cy1, x2, cy2, action, null, null));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (e.button() != 0) return super.mouseClicked(e, doubleClick);
		searchInput.setFocused(false);
		clientIdInput.setFocused(false);
		double mx = e.x(), my = e.y();
		clickX = mx;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (UiDraw.inside(mx, my, hit.x1, hit.y1, hit.x2, hit.y2)) {
				if (hit.drag != null) {
					dragging = hit;
					hit.drag.accept(mx);
				} else if (hit.action != null) {
					hit.action.run();
				}
				return true;
			}
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging != null) {
			dragging.drag.accept(e.x());
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragging != null) {
			if (dragging.release != null) dragging.release.run();
			dragging = null;
			return true;
		}
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		double d = v * 22;
		switch (tab) {
			case SEARCH -> searchScroll.wheel(d);
			case SETTINGS -> settingsScroll.wheel(d);
			case QUEUE -> queueScroll.wheel(d);
			case LYRICS -> {
				lyricsScroll.wheel(d);
				lyricsUserScrollAt = System.currentTimeMillis();
			}
			default -> {
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (searchInput.keyPressed(e) || clientIdInput.keyPressed(e)) return true;
		if (LyrixClient.openKey().matches(e)) {
			onClose();
			return true;
		}

		if (e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE && LyrixClient.spotify().current() != null) {
			LyrixClient.spotify().togglePlay();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		if (searchInput.charTyped(e) || clientIdInput.charTyped(e)) return true;
		return super.charTyped(e);
	}
}
