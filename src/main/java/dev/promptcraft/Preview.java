package dev.promptcraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws a review sheet for one creation so Claude can see what it made: the texture enlarged with a
 * pixel grid, then (for 3D models) front, side and three-quarter views, or (for blocks) a 2x2 tiling.
 */
public final class Preview {
	private static final int PANEL = 192;
	private static final int GAP = 8;
	private static final String[] FACES = {"north", "south", "west", "east", "up", "down"};

	private Preview() {}

	public static BufferedImage sheet(ModDef d) {
		BufferedImage tex = GeneratedPack.texture(d);
		List<BufferedImage> panels = new ArrayList<>();
		panels.add(enlarged(tex));
		if (d.has3dModel()) {
			JsonArray elements = d.model.getAsJsonArray("elements");
			panels.add(view(elements, tex, 0, 0));
			panels.add(view(elements, tex, 90, 0));
			panels.add(view(elements, tex, 45, 30));
		} else if (d.isBlock()) {
			panels.add(tiled(tex));
		}
		BufferedImage sheet = new BufferedImage(panels.size() * (PANEL + GAP) - GAP, PANEL, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		for (int i = 0; i < panels.size(); i++) g.drawImage(panels.get(i), i * (PANEL + GAP), 0, null);
		g.dispose();
		return sheet;
	}

	private static BufferedImage canvas() {
		BufferedImage img = new BufferedImage(PANEL, PANEL, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < PANEL; y++)
			for (int x = 0; x < PANEL; x++)
				img.setRGB(x, y, ((x / 12 + y / 12) % 2 == 0) ? 0xFFE4E4E4 : 0xFFC8C8C8);
		return img;
	}

	private static BufferedImage enlarged(BufferedImage tex) {
		BufferedImage img = canvas();
		int k = PANEL / 16;
		for (int y = 0; y < PANEL; y++) {
			for (int x = 0; x < PANEL; x++) {
				int c = tex.getRGB(x / k, y / k);
				if ((c >>> 24) != 0) img.setRGB(x, y, c);
				if (x % k == 0 || y % k == 0) img.setRGB(x, y, blend(img.getRGB(x, y), 0xFF808080, 0.25f));
			}
		}
		return img;
	}

	private static BufferedImage tiled(BufferedImage tex) {
		BufferedImage img = canvas();
		int k = PANEL / 32;
		for (int y = 0; y < PANEL; y++)
			for (int x = 0; x < PANEL; x++)
				img.setRGB(x, y, tex.getRGB((x / k) % 16, (y / k) % 16) | 0xFF000000);
		return img;
	}

	private static int blend(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	private record Quad(double[][] screen, double depth, double[] uv, float shade) {}

	/** A tiny orthographic renderer: painter's algorithm over textured faces, one quad per texel. */
	private static BufferedImage view(JsonArray elements, BufferedImage tex, double yawDeg, double pitchDeg) {
		BufferedImage img = canvas();
		double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
		double scale = PANEL / 26.0;
		List<Quad> quads = new ArrayList<>();
		for (JsonElement el : elements) {
			if (!el.isJsonObject()) continue;
			JsonObject e = el.getAsJsonObject();
			double[] from = vec(e.get("from")), to = vec(e.get("to"));
			if (from == null || to == null || !e.has("faces") || !e.get("faces").isJsonObject()) continue;
			JsonObject faces = e.getAsJsonObject("faces");
			for (String face : FACES) {
				if (!faces.has(face) || !faces.get(face).isJsonObject()) continue;
				double[][] world = corners(face, from, to);
				double[][] screen = new double[4][];
				double depth = 0;
				for (int i = 0; i < 4; i++) {
					double x = world[i][0] - 8, y = world[i][1] - 8, z = world[i][2] - 8;
					double x1 = x * Math.cos(yaw) - z * Math.sin(yaw);
					double z1 = x * Math.sin(yaw) + z * Math.cos(yaw);
					double y2 = y * Math.cos(pitch) - z1 * Math.sin(pitch);
					double z2 = y * Math.sin(pitch) + z1 * Math.cos(pitch);
					screen[i] = new double[]{PANEL / 2.0 + x1 * scale, PANEL / 2.0 - y2 * scale, z2};
					depth += z2 / 4;
				}
				// Faces wind clockwise seen from outside, so a visible face has a negative cross product here.
				double ax = screen[1][0] - screen[0][0], ay = -(screen[1][1] - screen[0][1]);
				double bx = screen[3][0] - screen[0][0], by = -(screen[3][1] - screen[0][1]);
				if (ax * by - ay * bx >= 0) continue;
				double[] uv = uv(faces.getAsJsonObject(face), face, from, to);
				quads.add(new Quad(screen, depth, uv, shade(face)));
			}
		}
		quads.sort(Comparator.comparingDouble(Quad::depth));
		Graphics2D g = img.createGraphics();
		for (Quad q : quads) draw(g, q, tex);
		g.dispose();
		return img;
	}

	private static void draw(Graphics2D g, Quad q, BufferedImage tex) {
		double u1 = q.uv[0], v1 = q.uv[1], u2 = q.uv[2], v2 = q.uv[3];
		int nu = Math.max(1, (int) Math.round(Math.abs(u2 - u1)));
		int nv = Math.max(1, (int) Math.round(Math.abs(v2 - v1)));
		for (int i = 0; i < nu; i++) {
			for (int j = 0; j < nv; j++) {
				int tu = clamp((int) Math.floor(u1 + (u2 - u1) * (i + 0.5) / nu));
				int tv = clamp((int) Math.floor(v1 + (v2 - v1) * (j + 0.5) / nv));
				int c = tex.getRGB(tu, tv);
				if ((c >>> 24) == 0) continue;
				int shaded = blend(c, 0xFF000000, 1 - q.shade);
				double s0 = (double) i / nu, s1 = (double) (i + 1) / nu, t0 = (double) j / nv, t1 = (double) (j + 1) / nv;
				double[][] pts = {lerp(q.screen, s0, t0), lerp(q.screen, s1, t0), lerp(q.screen, s1, t1), lerp(q.screen, s0, t1)};
				Polygon poly = new Polygon();
				for (double[] p : pts) poly.addPoint((int) Math.round(p[0]), (int) Math.round(p[1]));
				g.setColor(new Color(shaded, true));
				g.fillPolygon(poly);
				g.drawPolygon(poly);
			}
		}
	}

	/** Bilinear point on a quad whose corners are top-left, top-right, bottom-right, bottom-left. */
	private static double[] lerp(double[][] c, double s, double t) {
		double topX = c[0][0] + (c[1][0] - c[0][0]) * s, topY = c[0][1] + (c[1][1] - c[0][1]) * s;
		double botX = c[3][0] + (c[2][0] - c[3][0]) * s, botY = c[3][1] + (c[2][1] - c[3][1]) * s;
		return new double[]{topX + (botX - topX) * t, topY + (botY - topY) * t};
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(15, v));
	}

	private static double[] vec(JsonElement el) {
		if (el == null || !el.isJsonArray() || el.getAsJsonArray().size() < 3) return null;
		JsonArray a = el.getAsJsonArray();
		return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
	}

	/** Face corners as seen from outside: top-left, top-right, bottom-right, bottom-left. */
	private static double[][] corners(String face, double[] f, double[] t) {
		return switch (face) {
			case "north" -> new double[][]{{t[0], t[1], f[2]}, {f[0], t[1], f[2]}, {f[0], f[1], f[2]}, {t[0], f[1], f[2]}};
			case "south" -> new double[][]{{f[0], t[1], t[2]}, {t[0], t[1], t[2]}, {t[0], f[1], t[2]}, {f[0], f[1], t[2]}};
			case "west" -> new double[][]{{f[0], t[1], f[2]}, {f[0], t[1], t[2]}, {f[0], f[1], t[2]}, {f[0], f[1], f[2]}};
			case "east" -> new double[][]{{t[0], t[1], t[2]}, {t[0], t[1], f[2]}, {t[0], f[1], f[2]}, {t[0], f[1], t[2]}};
			case "up" -> new double[][]{{f[0], t[1], f[2]}, {t[0], t[1], f[2]}, {t[0], t[1], t[2]}, {f[0], t[1], t[2]}};
			default -> new double[][]{{f[0], f[1], t[2]}, {t[0], f[1], t[2]}, {t[0], f[1], f[2]}, {f[0], f[1], f[2]}};
		};
	}

	/** The face's uv, or Minecraft's automatic one from the element's position. */
	private static double[] uv(JsonObject face, String name, double[] f, double[] t) {
		if (face.has("uv") && face.get("uv").isJsonArray() && face.getAsJsonArray("uv").size() == 4) {
			JsonArray a = face.getAsJsonArray("uv");
			return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble()};
		}
		return switch (name) {
			case "north" -> new double[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
			case "south" -> new double[]{f[0], 16 - t[1], t[0], 16 - f[1]};
			case "west" -> new double[]{f[2], 16 - t[1], t[2], 16 - f[1]};
			case "east" -> new double[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
			default -> new double[]{f[0], f[2], t[0], t[2]};
		};
	}

	private static float shade(String face) {
		return switch (face) {
			case "up" -> 1f;
			case "north", "south" -> 0.85f;
			case "east", "west" -> 0.7f;
			default -> 0.55f;
		};
	}
}
