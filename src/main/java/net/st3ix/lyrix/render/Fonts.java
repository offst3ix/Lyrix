package net.st3ix.lyrix.render;

import net.st3ix.lyrix.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.Locale;

public final class Fonts {
	public static final FontDescription LYRICS = new FontDescription.Resource(Identifier.fromNamespaceAndPath("lyrix", "lyrics"));
	public static final FontDescription UI = new FontDescription.Resource(Identifier.fromNamespaceAndPath("lyrix", "ui"));

	private Fonts() {
	}

	public static MutableComponent lyric(String text) {
		ModConfig cfg = ModConfig.get();
		String s = cfg.uppercase ? text.toUpperCase(Locale.ROOT) : text;
		MutableComponent c = Component.literal(s);
		if (cfg.customFont) c = c.withStyle(style -> style.withFont(LYRICS));
		else c = c.withStyle(style -> style.withBold(true).withItalic(true));
		return c;
	}

	public static String lyricString(String text) {
		return ModConfig.get().uppercase ? text.toUpperCase(Locale.ROOT) : text;
	}

	public static MutableComponent ui(String text) {
		return Component.literal(text).withStyle(style -> style.withFont(UI));
	}
}
