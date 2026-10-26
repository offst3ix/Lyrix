package net.st3ix.lyrix;

import com.mojang.blaze3d.platform.InputConstants;
import net.st3ix.lyrix.config.ModConfig;
import net.st3ix.lyrix.gui.LyrixScreen;
import net.st3ix.lyrix.lyrics.LyricsManager;
import net.st3ix.lyrix.render.CoverTextures;
import net.st3ix.lyrix.render.LyricsHud;
import net.st3ix.lyrix.render.WorldLyrics;
import net.st3ix.lyrix.spotify.SpotifyService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LyrixClient implements ClientModInitializer {
	public static final String MOD_ID = "lyrix";
	public static final Logger LOGGER = LoggerFactory.getLogger("Lyrix");

	private static LyricsManager lyrics;
	private static SpotifyService spotify;
	private static CoverTextures covers;
	private static KeyMapping openKey;
	private static WorldLyrics world;

	public static WorldLyrics world() {
		return world;
	}

	@Override
	public void onInitializeClient() {
		ModConfig.load();
		lyrics = new LyricsManager();
		spotify = new SpotifyService(lyrics);
		covers = new CoverTextures();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyrix.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
		KeyMapping toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyrix.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
		KeyMapping playPauseKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyrix.playpause", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
		KeyMapping bannerKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyrix.banner", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
		KeyMapping nextKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyrix.next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
		KeyMapping prevKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyrix.previous", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.screen == null) client.setScreen(new LyrixScreen());
			}
			while (toggleKey.consumeClick()) {
				ModConfig cfg = ModConfig.get();
				cfg.enabled = !cfg.enabled;
				ModConfig.save();
			}
			while (playPauseKey.consumeClick()) {
				spotify.togglePlay();
			}
			while (bannerKey.consumeClick()) {
				ModConfig cfg = ModConfig.get();
				cfg.showCard = !cfg.showCard;
				ModConfig.save();
			}
			while (nextKey.consumeClick()) {
				spotify.next();
			}
			while (prevKey.consumeClick()) {
				spotify.previous();
			}
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "lyrics"), new LyricsHud());

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			if (net.st3ix.lyrix.render.Icons.get(net.st3ix.lyrix.render.Icons.Icon.CIRCLE) != null) {
				LOGGER.info("Smooth icons ready");
			}
		});

		world = new WorldLyrics();
		WorldRenderEvents.END_EXTRACTION.register(world::onExtraction);
		WorldRenderEvents.AFTER_ENTITIES.register(world::render);

		LOGGER.info("Lyrix loaded. Press K to open the menu.");
	}

	public static LyricsManager lyrics() {
		return lyrics;
	}

	public static SpotifyService spotify() {
		return spotify;
	}

	public static CoverTextures covers() {
		return covers;
	}

	public static KeyMapping openKey() {
		return openKey;
	}
}
