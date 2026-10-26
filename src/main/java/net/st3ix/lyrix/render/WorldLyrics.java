package net.st3ix.lyrix.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.st3ix.lyrix.LyrixClient;
import net.st3ix.lyrix.config.ModConfig;
import net.st3ix.lyrix.lyrics.Lyrics;
import net.st3ix.lyrix.lyrics.LyricsManager;
import net.st3ix.lyrix.spotify.PlaybackState;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class WorldLyrics {
	private static final float BASE_SCALE = 0.04f;
	private static final int MAX_WORDS_PER_LINE = 6;

	private static final class Placed {
		final String text;
		final Vec3 pos;
		final int line;
		final long spawnNs;
		final float scaleMul;
		final long typeMs;
		long fadeNs = -1;

		Placed(String text, Vec3 pos, int line, long spawnNs, float scaleMul, long typeMs) {
			this.text = text;
			this.pos = pos;
			this.line = line;
			this.spawnNs = spawnNs;
			this.scaleMul = scaleMul;
			this.typeMs = typeMs;
		}
	}

	private final List<Placed> placed = new ArrayList<>();
	private final Random random = new Random();
	private String trackId;
	private int lastLine = -1;
	private int lastWord = -1;
	private long lastT;
	private long lastUpdateAt;

	private final Matrix4f viewProjection = new Matrix4f();
	private float projectionScaleY = 1;
	private Vec3 matrixCamPos = Vec3.ZERO;
	private boolean hasMatrices;

	public void onExtraction(WorldExtractionContext ctx) {
		viewProjection.set(ctx.cullProjectionMatrix()).mul(ctx.viewMatrix());
		projectionScaleY = ctx.cullProjectionMatrix().m11();
		matrixCamPos = ctx.camera().position();
		hasMatrices = true;
	}

	private boolean active(ModConfig cfg, Minecraft mc) {
		if (!cfg.enabled || cfg.displayMode != ModConfig.DisplayMode.WORLD || mc.player == null) {
			placed.clear();
			lastLine = lastWord = -1;
			return false;
		}
		return true;
	}

	public void render(WorldRenderContext ctx) {
		ModConfig cfg = ModConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (cfg.shaderSafe || !active(cfg, mc)) return;
		long now = System.nanoTime();
		Camera camera = mc.gameRenderer.getMainCamera();
		update(cfg, camera, now);
		draw(ctx, cfg, camera, now);
	}

	public void renderHud(GuiGraphics g) {
		ModConfig cfg = ModConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.shaderSafe || !active(cfg, mc) || !hasMatrices) return;
		long now = System.nanoTime();
		update(cfg, mc.gameRenderer.getMainCamera(), now);
		if (placed.isEmpty()) return;

		Font font = mc.font;
		int sw = g.guiWidth(), sh = g.guiHeight();

		List<Placed> sorted = new ArrayList<>(placed);
		sorted.sort((a, b) -> Double.compare(b.pos.distanceToSqr(matrixCamPos), a.pos.distanceToSqr(matrixCamPos)));
		Vector4f v = new Vector4f();
		for (Placed p : sorted) {
			long age = (now - p.spawnNs) / 1_000_000L;
			long fadeAge = p.fadeNs < 0 ? -1 : (now - p.fadeNs) / 1_000_000L;
			TextAnim a = TextAnim.compute(cfg.effect, age, fadeAge, p.text.length(), p.typeMs);
			if (a.alpha() < 0.02f || a.visibleChars() <= 0) continue;

			v.set((float) (p.pos.x - matrixCamPos.x), (float) (p.pos.y - matrixCamPos.y), (float) (p.pos.z - matrixCamPos.z), 1f);
			viewProjection.transform(v);
			if (v.w < 0.1f) continue;
			float sx = (v.x / v.w + 1) * 0.5f * sw;
			float sy = (1 - v.y / v.w) * 0.5f * sh;

			Component full = Fonts.lyric(p.text);
			Component shown = a.visibleChars() >= p.text.length() ? full : Fonts.lyric(p.text.substring(0, a.visibleChars()));
			float textW = font.width(full);

			float blockPx = projectionScaleY * sh * 0.5f / v.w;
			float worldScale = BASE_SCALE * cfg.worldScale * p.scaleMul;
			float maxWorldW = cfg.worldDistance * 1.1f;
			if (textW * worldScale > maxWorldW) worldScale = maxWorldW / textW;
			float scale = worldScale * a.scale() * blockPx;
			if (scale < 0.05f || scale > 40f) continue;
			float x = sx - textW * scale / 2f;
			float y = sy + (-font.lineHeight / 2f + a.offsetY()) * scale;
			if (x > sw || y > sh || x + textW * scale < 0 || y + font.lineHeight * scale < 0) continue;
			UiDraw.scaledText(g, shown, x, y, scale, TextAnim.argb(a.alpha(), cfg.textColor), cfg.shadow);
		}
	}

	private void update(ModConfig cfg, Camera camera, long now) {
		PlaybackState state = LyrixClient.spotify().current();
		LyricsManager lm = LyrixClient.lyrics();
		Lyrics lyrics = lm.lyrics();

		if (state == null || lyrics == null || !state.trackId().equals(lm.trackId())) {
			fadeAll(now);
		} else {
			if (!state.trackId().equals(trackId)) {
				placed.clear();
				trackId = state.trackId();
				lastLine = lastWord = -1;
			}
			long t = state.progressNow() + cfg.offsetFor(state.trackId());
			long realNow = System.currentTimeMillis();
			boolean paused = realNow - lastUpdateAt > 500;
			if (t < lastT - 1500 || (paused && t > lastT + 1500)) {
				placed.clear();
				lastLine = lastWord = -1;
			}
			lastT = t;
			lastUpdateAt = realNow;

			int li = lyrics.lineAt(t);
			for (Placed p : placed) {
				if (p.fadeNs < 0 && p.line != li) p.fadeNs = now;
			}
			if (li >= 0) {
				Lyrics.Line line = lyrics.lines().get(li);
				if (li != lastLine) {
					lastLine = li;
					lastWord = -1;
					if (cfg.textMode == ModConfig.TextMode.LINE) {
						spawnLine(line, li, camera, now, cfg);
					}
				}
				if (cfg.textMode == ModConfig.TextMode.WORD) {
					int wi = line.wordAt(t);

					while (lastWord < wi) {
						lastWord++;
						Lyrics.Word w = line.words().get(lastWord);
						boolean stale = lastWord < wi && t - line.words().get(lastWord + 1).start() > 400;
						if (!stale) spawnWord(w, li, camera, now, cfg);
					}
				}
			}
		}

		long animNs = cfg.animationMs * 1_000_000L;
		placed.removeIf(p -> p.fadeNs >= 0 && (!cfg.fadeOut || now - p.fadeNs > animNs));
	}

	private void fadeAll(long now) {
		for (Placed p : placed) if (p.fadeNs < 0) p.fadeNs = now;
	}

	private void spawnWord(Lyrics.Word word, int line, Camera camera, long now, ModConfig cfg) {
		int active = 0;
		for (Placed p : placed) if (p.fadeNs < 0) active++;
		if (active >= MAX_WORDS_PER_LINE) {
			for (Placed p : placed) {
				if (p.fadeNs < 0) {
					p.fadeNs = now;
					break;
				}
			}
		}

		Vec3 eye = camera.position();
		Vector3f fwd = new Vector3f(0, 0, -1).rotate(camera.rotation());
		Vector3f up = new Vector3f(0, 1, 0).rotate(camera.rotation());
		Vector3f right = new Vector3f(1, 0, 0).rotate(camera.rotation());
		float dist = cfg.worldDistance;
		float spreadX = 1.8f * cfg.worldSpread * dist / 5f;
		float spreadY = 0.9f * cfg.worldSpread * dist / 5f;

		Vec3 best = null;
		double bestScore = -1;
		for (int i = 0; i < 10; i++) {
			float rx = (random.nextFloat() * 2 - 1) * spreadX;
			float ry = (random.nextFloat() * 2 - 1) * spreadY + 0.15f * spreadY;
			float rz = (random.nextFloat() * 2 - 1) * 0.6f;
			Vec3 pos = eye.add(
					fwd.x() * (dist + rz) + right.x * rx + up.x() * ry,
					fwd.y() * (dist + rz) + right.y * rx + up.y() * ry,
					fwd.z() * (dist + rz) + right.z * rx + up.z() * ry);
			double minDist = 100;
			for (Placed p : placed) {
				if (p.fadeNs < 0) minDist = Math.min(minDist, p.pos.distanceTo(pos));
			}
			if (minDist > bestScore) {
				bestScore = minDist;
				best = pos;
			}
		}
		float scaleMul = 0.9f + random.nextFloat() * 0.35f;
		long typeMs = Math.min(Math.max(120, word.end() - word.start()), word.text().length() * 55L);
		placed.add(new Placed(word.text(), best, line, now, scaleMul, typeMs));
	}

	private void spawnLine(Lyrics.Line line, int index, Camera camera, long now, ModConfig cfg) {
		Vec3 eye = camera.position();
		Vector3f fwd = new Vector3f(0, 0, -1).rotate(camera.rotation());
		Vec3 pos = eye.add(fwd.x() * cfg.worldDistance, fwd.y() * cfg.worldDistance + 0.4, fwd.z() * cfg.worldDistance);
		long typeMs = Math.max(200, line.end() - line.start()) / 2;
		placed.add(new Placed(line.text(), pos, index, now, 0.8f, typeMs));
	}

	private void draw(WorldRenderContext ctx, ModConfig cfg, Camera camera, long now) {
		if (placed.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		PoseStack ps = ctx.matrices();
		MultiBufferSource buffers = ctx.consumers();
		Vec3 cam = camera.position();
		Font.DisplayMode mode = cfg.seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

		Iterator<Placed> it = placed.iterator();
		while (it.hasNext()) {
			Placed p = it.next();
			long age = (now - p.spawnNs) / 1_000_000L;
			long fadeAge = p.fadeNs < 0 ? -1 : (now - p.fadeNs) / 1_000_000L;
			TextAnim a = TextAnim.compute(cfg.effect, age, fadeAge, p.text.length(), p.typeMs);
			if (a.alpha() < 0.02f || a.visibleChars() <= 0) continue;

			Component full = Fonts.lyric(p.text);
			Component shown = a.visibleChars() >= p.text.length() ? full : Fonts.lyric(p.text.substring(0, a.visibleChars()));
			float textW = font.width(full);
			float scale = BASE_SCALE * cfg.worldScale * p.scaleMul * a.scale();

			float maxWorldW = cfg.worldDistance * 1.1f;
			if (textW * scale > maxWorldW) scale = maxWorldW / textW;

			ps.pushPose();
			ps.translate(p.pos.x - cam.x, p.pos.y - cam.y, p.pos.z - cam.z);
			ps.mulPose(camera.rotation());
			ps.scale(scale, -scale, scale);
			font.drawInBatch(shown, -textW / 2f, -font.lineHeight / 2f + a.offsetY(),
					TextAnim.argb(a.alpha(), cfg.textColor), cfg.shadow, ps.last().pose(), buffers, mode, 0, LightTexture.FULL_BRIGHT);
			ps.popPose();
		}
	}
}
