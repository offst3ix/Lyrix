package net.st3ix.lyrix.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.st3ix.lyrix.LyrixClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public final class Icons {
	public enum Icon {
		CIRCLE, PLAY, PAUSE, NEXT, PREV, SHUFFLE, REPEAT, REPEAT_ONE, HEART, HEART_FILLED, SEARCH, VOLUME, VOLUME_MUTE,
		QUEUE, DEVICE, SETTINGS, LYRICS, USER, NOTE, PLUS, CHECK, CLOSE, CHEVRON_LEFT, CHEVRON_RIGHT, HOME, CHAT, LINK
	}

	public static final int SIZE = 64;
	private static final Map<Icon, Identifier> TEXTURES = new EnumMap<>(Icon.class);
	private static boolean initialized;
	private static boolean failed;

	private Icons() {
	}

	public static Identifier get(Icon icon) {
		if (!initialized) init();
		return failed ? null : TEXTURES.get(icon);
	}

	private static void init() {
		initialized = true;
		try {
			for (Icon icon : Icon.values()) {
				TEXTURES.put(icon, register(icon.name().toLowerCase(), render(icon)));
			}
		} catch (Throwable t) {
			failed = true;
			LyrixClient.LOGGER.warn("Smooth icons unavailable, using fallback drawing", t);
		}
	}

	public static final class SmoothTexture extends DynamicTexture {
		public SmoothTexture(String name, NativeImage image) {
			super(() -> name, image);
			try {
				this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			} catch (LinkageError oldVersion) {
				legacySmooth(this);
			}
		}

		private static void legacySmooth(net.minecraft.client.renderer.texture.AbstractTexture texture) {
			try {
				String name = net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver()
						.mapMethodName("intermediary", "net.minecraft.class_1044", "method_4527", "(ZZ)V");
				java.lang.reflect.Method m = net.minecraft.client.renderer.texture.AbstractTexture.class.getMethod(name, boolean.class, boolean.class);
				m.invoke(texture, true, false);
			} catch (Exception e) {
				LyrixClient.LOGGER.debug("No smooth filtering for textures", e);
			}
		}
	}

	static Identifier register(String name, BufferedImage img) throws Exception {
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(img, "png", png);
		NativeImage nativeImage = NativeImage.read(png.toByteArray());
		Identifier id = Identifier.fromNamespaceAndPath("lyrix", "icon/" + name);
		Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture("lyrix " + name, nativeImage));
		return id;
	}

	private static BufferedImage render(Icon icon) {
		BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setColor(Color.WHITE);
		BasicStroke stroke = new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		g.setStroke(stroke);
		switch (icon) {
			case CIRCLE -> g.fill(new Ellipse2D.Float(0, 0, SIZE, SIZE));
			case PLAY -> g.fill(roundTriangle(new float[]{20, 12, 20, 52, 54, 32}));
			case PAUSE -> {
				g.fill(new RoundRectangle2D.Float(15, 12, 12, 40, 6, 6));
				g.fill(new RoundRectangle2D.Float(37, 12, 12, 40, 6, 6));
			}
			case NEXT -> {
				g.fill(roundTriangle(new float[]{12, 14, 12, 50, 42, 32}));
				g.fill(new RoundRectangle2D.Float(43, 14, 8, 36, 4, 4));
			}
			case PREV -> {
				g.fill(roundTriangle(new float[]{52, 14, 52, 50, 22, 32}));
				g.fill(new RoundRectangle2D.Float(13, 14, 8, 36, 4, 4));
			}
			case SHUFFLE -> {
				g.draw(curve(8, 18, 26, 18, 38, 46, 54, 46));
				g.draw(curve(8, 46, 26, 46, 38, 18, 54, 18));
				arrowHead(g, 54, 18, 1);
				arrowHead(g, 54, 46, 1);
			}
			case REPEAT, REPEAT_ONE -> {
				g.draw(new RoundRectangle2D.Float(10, 18, 44, 28, 16, 16));
				g.setColor(new Color(0, 0, 0, 0));
				g.setComposite(java.awt.AlphaComposite.Clear);
				g.fill(new java.awt.Rectangle(26, 10, 12, 14));
				g.setComposite(java.awt.AlphaComposite.SrcOver);
				g.setColor(Color.WHITE);
				arrowHead(g, 38, 18, 1);
				if (icon == Icon.REPEAT_ONE) {
					g.fill(new Ellipse2D.Float(40, 34, 20, 20));
					g.setComposite(java.awt.AlphaComposite.Clear);
					g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.draw(new Line2D.Float(50, 39, 50, 49));
					g.setComposite(java.awt.AlphaComposite.SrcOver);
				}
			}
			case HEART, HEART_FILLED -> {
				Path2D h = heart();
				if (icon == Icon.HEART_FILLED) g.fill(h);
				else g.draw(h);
			}
			case SEARCH -> {
				g.draw(new Ellipse2D.Float(12, 12, 28, 28));
				g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(new Line2D.Float(37, 37, 52, 52));
			}
			case VOLUME, VOLUME_MUTE -> {
				GeneralPath sp = new GeneralPath();
				sp.moveTo(8, 25);
				sp.lineTo(18, 25);
				sp.lineTo(30, 13);
				sp.lineTo(30, 51);
				sp.lineTo(18, 39);
				sp.lineTo(8, 39);
				sp.closePath();
				g.fill(sp);
				if (icon == Icon.VOLUME) {
					g.draw(new Arc2D.Float(24, 20, 20, 24, -50, 100, Arc2D.OPEN));
					g.draw(new Arc2D.Float(22, 10, 34, 44, -50, 100, Arc2D.OPEN));
				} else {
					g.draw(new Line2D.Float(40, 24, 54, 40));
					g.draw(new Line2D.Float(54, 24, 40, 40));
				}
			}
			case QUEUE -> {
				g.draw(new Line2D.Float(10, 16, 54, 16));
				g.draw(new Line2D.Float(10, 30, 54, 30));
				g.draw(new Line2D.Float(10, 44, 34, 44));
				g.fill(roundTriangle(new float[]{40, 38, 40, 54, 54, 46}));
			}
			case DEVICE -> {
				g.draw(new RoundRectangle2D.Float(8, 12, 48, 32, 8, 8));
				g.draw(new Line2D.Float(24, 52, 40, 52));
				g.draw(new Line2D.Float(32, 44, 32, 52));
			}
			case SETTINGS -> {
				g.draw(new Line2D.Float(10, 18, 54, 18));
				g.draw(new Line2D.Float(10, 32, 54, 32));
				g.draw(new Line2D.Float(10, 46, 54, 46));
				g.fill(new Ellipse2D.Float(16, 11, 14, 14));
				g.fill(new Ellipse2D.Float(36, 25, 14, 14));
				g.fill(new Ellipse2D.Float(22, 39, 14, 14));
			}
			case LYRICS -> {
				g.draw(new RoundRectangle2D.Float(22, 6, 20, 32, 20, 20));
				g.draw(new Arc2D.Float(14, 18, 36, 30, 180, 180, Arc2D.OPEN));
				g.draw(new Line2D.Float(32, 48, 32, 58));
			}
			case USER -> {
				g.fill(new Ellipse2D.Float(21, 8, 22, 22));
				g.fill(new Arc2D.Float(10, 34, 44, 40, 0, 180, Arc2D.CHORD));
			}
			case NOTE -> {
				g.fill(new Ellipse2D.Float(10, 38, 18, 14));
				g.fill(new Ellipse2D.Float(36, 32, 18, 14));
				g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				GeneralPath p = new GeneralPath();
				p.moveTo(26, 44);
				p.lineTo(26, 12);
				p.lineTo(52, 6);
				p.lineTo(52, 38);
				g.draw(p);
			}
			case PLUS -> {
				g.draw(new Line2D.Float(32, 12, 32, 52));
				g.draw(new Line2D.Float(12, 32, 52, 32));
			}
			case CHECK -> {
				GeneralPath p = new GeneralPath();
				p.moveTo(12, 34);
				p.lineTo(26, 48);
				p.lineTo(52, 18);
				g.draw(p);
			}
			case CLOSE -> {
				g.draw(new Line2D.Float(16, 16, 48, 48));
				g.draw(new Line2D.Float(48, 16, 16, 48));
			}
			case CHEVRON_LEFT -> {
				GeneralPath p = new GeneralPath();
				p.moveTo(40, 12);
				p.lineTo(20, 32);
				p.lineTo(40, 52);
				g.draw(p);
			}
			case CHEVRON_RIGHT -> {
				GeneralPath p = new GeneralPath();
				p.moveTo(24, 12);
				p.lineTo(44, 32);
				p.lineTo(24, 52);
				g.draw(p);
			}
			case CHAT -> {
				java.awt.geom.Area body = new java.awt.geom.Area(new RoundRectangle2D.Float(6, 12, 52, 36, 22, 22));
				GeneralPath tail = new GeneralPath();
				tail.moveTo(16, 44);
				tail.lineTo(12, 56);
				tail.lineTo(28, 46);
				tail.closePath();
				body.add(new java.awt.geom.Area(tail));
				body.subtract(new java.awt.geom.Area(new Ellipse2D.Float(19, 24, 9, 11)));
				body.subtract(new java.awt.geom.Area(new Ellipse2D.Float(36, 24, 9, 11)));
				g.fill(body);
			}
			case LINK -> {
				g.draw(new RoundRectangle2D.Float(8, 22, 28, 20, 20, 20));
				g.draw(new RoundRectangle2D.Float(28, 22, 28, 20, 20, 20));
			}
			case HOME -> {
				GeneralPath p = new GeneralPath();
				p.moveTo(10, 30);
				p.lineTo(32, 10);
				p.lineTo(54, 30);
				g.draw(p);
				g.draw(new RoundRectangle2D.Float(16, 28, 32, 26, 6, 6));
			}
		}
		g.dispose();
		return img;
	}

	private static Path2D roundTriangle(float[] p) {
		GeneralPath path = new GeneralPath();
		path.moveTo(p[0], p[1]);
		path.lineTo(p[2], p[3]);
		path.lineTo(p[4], p[5]);
		path.closePath();

		java.awt.Shape outline = new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(path);
		java.awt.geom.Area a = new java.awt.geom.Area(path);
		a.add(new java.awt.geom.Area(outline));
		return new Path2D.Float(a);
	}

	private static Path2D curve(float x1, float y1, float cx1, float cy1, float cx2, float cy2, float x2, float y2) {
		Path2D.Float p = new Path2D.Float();
		p.moveTo(x1, y1);
		p.curveTo(cx1 + 10, cy1, cx2 - 10, cy2, x2 - 4, y2);
		return p;
	}

	private static void arrowHead(Graphics2D g, float x, float y, int dir) {
		GeneralPath p = new GeneralPath();
		p.moveTo(x - 9 * dir, y - 8);
		p.lineTo(x, y);
		p.lineTo(x - 9 * dir, y + 8);
		g.draw(p);
	}

	private static Path2D heart() {
		Path2D.Float p = new Path2D.Float();
		p.moveTo(32, 54);
		p.curveTo(10, 40, 6, 28, 8, 20);
		p.curveTo(10, 10, 24, 6, 32, 18);
		p.curveTo(40, 6, 54, 10, 56, 20);
		p.curveTo(58, 28, 54, 40, 32, 54);
		p.closePath();
		return p;
	}

	static BufferedImage draw(int w, int h, Consumer<Graphics2D> painter) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		painter.accept(g);
		g.dispose();
		return img;
	}
}
