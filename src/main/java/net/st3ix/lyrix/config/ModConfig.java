package net.st3ix.lyrix.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.st3ix.lyrix.LyrixClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
	public enum DisplayMode {
		TOP("Top"), LEFT("Left"), RIGHT("Right"), WORLD("In front of you (3D)");
		public final String label;
		DisplayMode(String label) { this.label = label; }
	}

	public enum TextMode {
		WORD("Word by word"), LINE("Full line");
		public final String label;
		TextMode(String label) { this.label = label; }
	}

	public enum Effect {
		POP("Pop"), FADE("Fade"), TYPEWRITER("Typewriter"), SLIDE("Slide up"), NONE("None");
		public final String label;
		Effect(String label) { this.label = label; }
	}

	public enum CardStyle {
		FULL("Full"), COMPACT("Compact");
		public final String label;
		CardStyle(String label) { this.label = label; }
	}

	public enum Corner {
		TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right");
		public final String label;
		Corner(String label) { this.label = label; }
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ModConfig instance;

	public String clientId = "";

	public boolean enabled = true;
	public DisplayMode displayMode = DisplayMode.WORLD;
	public TextMode textMode = TextMode.WORD;
	public Effect effect = Effect.POP;
	public boolean fadeOut = true;
	public int animationMs = 280;
	public int syncOffsetMs = 0;
	public java.util.Map<String, Integer> songOffsets = new java.util.HashMap<>();
	public boolean showSyncInfo = false;

	public float wordPacing = 0.25f;
	public boolean showLyricsStatus = true;

	public boolean uppercase = true;
	public boolean customFont = true;
	public boolean shadow = true;
	public int textColor = 0xFFFFFF;
	public int highlightColor = 0x1DB954;
	public boolean smoothKaraoke = true;

	public float hudScale = 1.6f;
	public boolean lyricsBackground = true;
	public float lyricsBackgroundOpacity = 0.55f;

	public boolean showCard = true;
	public float cardScale = 1.0f;
	public CardStyle cardStyle = CardStyle.FULL;
	public Corner cardCorner = Corner.TOP_LEFT;
	public float cardOpacity = 0.85f;
	public boolean cardTinted = true;
	public boolean songToast = true;

	public float worldDistance = 5.0f;
	public float worldScale = 1.0f;
	public float worldSpread = 1.0f;
	public boolean seeThrough = true;

	public boolean shaderSafe = true;

	public static ModConfig get() {
		if (instance == null) load();
		return instance;
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("lyrix");
	}

	private static Path file() {
		return dir().resolve("config.json");
	}

	private static void migrateOldFolder() {
		Path old = FabricLoader.getInstance().getConfigDir().resolve("spotifylyrics");
		if (Files.exists(dir()) || !Files.isDirectory(old)) return;
		try {
			Files.createDirectories(dir());
			for (String name : new String[]{"config.json", "tokens.json"}) {
				if (Files.exists(old.resolve(name))) Files.copy(old.resolve(name), dir().resolve(name));
			}
		} catch (Exception e) {
			LyrixClient.LOGGER.warn("Could not copy old settings", e);
		}
	}

	public static void load() {
		migrateOldFolder();
		Path f = file();
		if (Files.exists(f)) {
			try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				instance = GSON.fromJson(r, ModConfig.class);
			} catch (Exception e) {
				LyrixClient.LOGGER.warn("Could not read config, using defaults", e);
			}
		}
		if (instance == null) instance = new ModConfig();
		instance.sanitize();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(dir());
			try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception e) {
			LyrixClient.LOGGER.warn("Could not save config", e);
		}
	}

	public int songOffset(String trackId) {
		return trackId == null ? 0 : songOffsets.getOrDefault(trackId, 0);
	}

	public void setSongOffset(String trackId, int ms) {
		if (trackId == null) return;
		ms = Math.clamp(ms, -5000, 5000);
		if (ms == 0) songOffsets.remove(trackId);
		else songOffsets.put(trackId, ms);
	}

	public int offsetFor(String trackId) {
		return syncOffsetMs + songOffset(trackId);
	}

	public static void reset() {
		String id = instance != null ? instance.clientId : "";
		instance = new ModConfig();
		instance.clientId = id;
		save();
	}

	private void sanitize() {
		if (clientId == null) clientId = "";
		if (displayMode == null) displayMode = DisplayMode.WORLD;
		if (textMode == null) textMode = TextMode.WORD;
		if (effect == null) effect = Effect.POP;
		if (cardStyle == null) cardStyle = CardStyle.FULL;
		if (cardCorner == null) cardCorner = Corner.TOP_LEFT;
		animationMs = Math.clamp(animationMs, 50, 1500);
		hudScale = Math.clamp(hudScale, 0.5f, 4f);
		lyricsBackgroundOpacity = Math.clamp(lyricsBackgroundOpacity, 0f, 1f);
		cardOpacity = Math.clamp(cardOpacity, 0.2f, 1f);
		cardScale = Math.clamp(cardScale, 0.5f, 2f);
		wordPacing = Math.clamp(wordPacing, 0f, 1f);
		worldDistance = Math.clamp(worldDistance, 2f, 16f);
		worldScale = Math.clamp(worldScale, 0.3f, 4f);
		worldSpread = Math.clamp(worldSpread, 0f, 3f);
		syncOffsetMs = Math.clamp(syncOffsetMs, -3000, 3000);
		if (songOffsets == null) songOffsets = new java.util.HashMap<>();
		songOffsets.values().removeIf(v -> v == null || v == 0);
	}
}
