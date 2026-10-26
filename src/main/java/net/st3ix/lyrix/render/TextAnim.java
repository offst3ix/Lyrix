package net.st3ix.lyrix.render;

import net.st3ix.lyrix.config.ModConfig;

public record TextAnim(float alpha, float scale, float offsetY, int visibleChars) {
	public static TextAnim compute(ModConfig.Effect effect, long ageMs, long fadeAgeMs, int length, long typeMs) {
		ModConfig cfg = ModConfig.get();
		float anim = cfg.animationMs;
		float t = clamp01(ageMs / anim);
		float alpha = 1, scale = 1, offsetY = 0;
		int visible = length;
		switch (effect) {
			case POP -> {
				scale = 0.35f + 0.65f * easeOutBack(t);
				alpha = clamp01(t * 2.5f);
			}
			case FADE -> {
				alpha = easeOutCubic(t);
				scale = 0.92f + 0.08f * easeOutCubic(t);
			}
			case TYPEWRITER -> {
				float tt = typeMs <= 0 ? 1 : clamp01(ageMs / (float) typeMs);
				visible = Math.min(length, (int) Math.ceil(length * tt));
			}
			case SLIDE -> {
				float e = easeOutCubic(t);
				offsetY = (1 - e) * 10f;
				alpha = e;
			}
			case NONE -> {
			}
		}
		if (fadeAgeMs >= 0) {
			float f = cfg.fadeOut ? 1 - clamp01(fadeAgeMs / anim) : 0;
			alpha *= f;
			if (effect == ModConfig.Effect.POP) scale *= 0.8f + 0.2f * f;
			if (effect == ModConfig.Effect.SLIDE) offsetY -= (1 - f) * 8f;
		}
		return new TextAnim(clamp01(alpha), scale, offsetY, visible);
	}

	public static float clamp01(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	public static float easeOutCubic(float t) {
		float u = 1 - t;
		return 1 - u * u * u;
	}

	public static float easeOutBack(float t) {
		float c1 = 1.70158f, c3 = c1 + 1;
		float u = t - 1;
		return 1 + c3 * u * u * u + c1 * u * u;
	}

	public static int argb(float alpha, int rgb) {
		int a = Math.round(clamp01(alpha) * 255);
		return (a << 24) | (rgb & 0xFFFFFF);
	}
}
