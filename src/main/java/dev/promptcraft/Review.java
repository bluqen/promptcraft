package dev.promptcraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The second pass: renders every creation Claude drew from scratch or built in 3D, shows Claude the
 * renders, and swaps in its fixed texture and model. Recoloured vanilla sprites skip this; they already look right.
 */
public final class Review {
	private static final Gson GSON = new Gson();

	private static final String PROMPT = """
			Review the Minecraft creations you just designed. Each preview image shows: first the 16x16 texture enlarged with a pixel grid, then for 3D models the front, side and three-quarter views, or for blocks a 2x2 tiling.
			Open every image and judge it like a Minecraft pixel artist. Fix anything wrong:
			- silhouette unreadable at 16x16, or not filling the canvas
			- noisy, random-looking pixels; stray single pixels
			- pillow shading (dark rim all around); light must come from the top-left, shadows bottom-right
			- missing or broken dark outline; too few shades (flat) or too many colours (muddy)
			- block textures with visible seams in the tiling
			- 3D: boxes floating, overlapping wrongly or misaligned; faces showing the wrong part of the texture (check each face's uv); proportions that don't read as the object
			- iconic details missing from the rendered views (e.g. gems that exist in the texture but no face's uv shows them)
			Be a strict critic, not a proud author: first drafts always have problems. In "message", list the 3 biggest concrete problems you see, then fix all of them in the returned creations.
			Return every creation, in the same order, as complete create_mod inputs (same names; behaviours, props and recipe unchanged).

			Your creations, in order:
			""";

	private Review() {}

	static boolean needsReview(JsonObject input) {
		ModDef d = GSON.fromJson(input, ModDef.class).normalize();
		if (d.isMob() || d.isEvent()) return false;
		boolean fromScratch = !d.isArmor() && (d.texture.base == null || d.texture.base.isBlank());
		boolean painted = d.texture.rows.stream().anyMatch(r -> r != null && r.chars().anyMatch(c -> c != '.'));
		return fromScratch || painted || d.has3dModel();
	}

	public static CompletableFuture<JsonArray> review(JsonArray content, ClaudeApi.Asker asker) {
		List<JsonObject> blocks = new ArrayList<>();
		for (JsonElement el : content) {
			JsonObject b = el.getAsJsonObject();
			if ("tool_use".equals(str(b, "type")) && "create_mod".equals(str(b, "name")) && b.has("input") && needsReview(b.getAsJsonObject("input"))) {
				blocks.add(b);
			}
		}
		if (blocks.isEmpty()) return CompletableFuture.completedFuture(content);

		JsonArray inputs = new JsonArray();
		List<Path> images = new ArrayList<>();
		try {
			Path dir = Files.createTempDirectory("promptcraft-review");
			for (int i = 0; i < blocks.size(); i++) {
				JsonObject input = blocks.get(i).getAsJsonObject("input");
				inputs.add(input);
				Path png = dir.resolve("creation_" + (i + 1) + ".png");
				ImageIO.write(Preview.sheet(GSON.fromJson(input, ModDef.class).normalize()), "png", png.toFile());
				images.add(png);
			}
		} catch (Exception e) {
			PromptCraft.LOG.warn("Could not render previews; skipping review", e);
			return CompletableFuture.completedFuture(content);
		}

		return asker.ask(PROMPT + GSON.toJson(inputs), images, false).handle((revised, err) -> {
			if (err != null || revised == null) {
				PromptCraft.LOG.warn("Review pass failed; keeping first drafts", err);
				return content;
			}
			List<JsonObject> fixes = new ArrayList<>();
			for (JsonElement el : revised) {
				JsonObject b = el.getAsJsonObject();
				if ("create_mod".equals(str(b, "name")) && b.has("input")) fixes.add(b.getAsJsonObject("input"));
			}
			// Only the visuals come from the review, so a sloppy reply can't break abilities or recipes.
			for (int i = 0; i < blocks.size() && i < fixes.size(); i++) {
				JsonObject input = blocks.get(i).getAsJsonObject("input");
				JsonObject fix = fixes.get(i);
				if (fix.has("texture")) input.add("texture", fix.get("texture"));
				if (fix.has("model")) input.add("model", fix.get("model"));
			}
			return content;
		});
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
	}
}
