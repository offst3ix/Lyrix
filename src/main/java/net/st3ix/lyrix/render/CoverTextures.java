package net.st3ix.lyrix.render;

import net.st3ix.lyrix.LyrixClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CoverTextures {
	public static final int SIZE = 160;
	private static final int MAX = 40;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "Lyrix-Covers");
		t.setDaemon(true);
		return t;
	});
	private final Map<String, Identifier> ready = new LinkedHashMap<>(16, 0.75f, true);
	private final Map<String, Integer> colors = new HashMap<>();
	private final Set<String> loading = new HashSet<>();
	private final Set<String> failed = new HashSet<>();
	private int counter;

	public Identifier get(String url) {
		if (url == null) return null;
		Identifier id = ready.get(url);
		if (id != null) return id;
		if (!loading.contains(url) && !failed.contains(url)) {
			loading.add(url);
			executor.execute(() -> load(url));
		}
		return null;
	}

	public int color(String url, int fallback) {
		if (url == null) return fallback;
		Integer c = colors.get(url);
		return c != null ? c : fallback;
	}

	private void load(String url) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
			byte[] bytes = http.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();
			BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
			if (src == null) throw new IllegalStateException("Unknown image format");
			int color = averageColor(src);
			BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

			g.fill(new RoundRectangle2D.Float(0, 0, SIZE, SIZE, SIZE * 0.18f, SIZE * 0.18f));
			g.setComposite(java.awt.AlphaComposite.SrcIn);
			g.drawImage(src, 0, 0, SIZE, SIZE, null);
			g.dispose();
			Minecraft.getInstance().execute(() -> register(url, img, color));
		} catch (Throwable e) {
			LyrixClient.LOGGER.debug("Could not load cover: {}", url, e);
			Minecraft.getInstance().execute(() -> {
				loading.remove(url);
				failed.add(url);
			});
		}
	}

	private static int averageColor(BufferedImage img) {
		long r = 0, gr = 0, b = 0, n = 0;
		int stepX = Math.max(1, img.getWidth() / 24), stepY = Math.max(1, img.getHeight() / 24);
		for (int y = 0; y < img.getHeight(); y += stepY) {
			for (int x = 0; x < img.getWidth(); x += stepX) {
				int p = img.getRGB(x, y);
				r += (p >> 16) & 0xFF;
				gr += (p >> 8) & 0xFF;
				b += p & 0xFF;
				n++;
			}
		}
		if (n == 0) return 0xFF181818;

		float f = 0.45f;
		int rr = (int) (r / n * f), gg = (int) (gr / n * f), bb = (int) (b / n * f);
		return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
	}

	private void register(String url, BufferedImage img, int color) {
		loading.remove(url);
		try {
			Identifier id = Icons.register("cover_" + (counter++), img);
			ready.put(url, id);
			colors.put(url, color);
			while (ready.size() > MAX) {
				var eldest = ready.entrySet().iterator().next();
				Minecraft.getInstance().getTextureManager().release(eldest.getValue());
				colors.remove(eldest.getKey());
				ready.remove(eldest.getKey());
			}
		} catch (Exception e) {
			failed.add(url);
			LyrixClient.LOGGER.debug("Cover texture failed", e);
		}
	}
}
