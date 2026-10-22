package dev.promptcraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An in-memory pack built from the current creations: textures, models and blockstates for the client,
 * recipes and loot tables for the server. It is rebuilt on every resource or data reload.
 */
public class GeneratedPack implements PackResources {
	private static final String ID = "promptcraft_generated";
	private static final PackLocationInfo INFO = new PackLocationInfo(ID, Component.literal("PromptCraft creations"), PackSource.BUILT_IN, Optional.empty());
	private static final List<String> ARMOR_PIECE_NAMES = List.of("helmet", "chestplate", "leggings", "boots");
	private static final Set<String> ARMOR_MATERIALS = Set.of("leather", "chainmail", "iron", "gold", "diamond", "netherite");

	public static final RepositorySource SOURCE = consumer -> {
		Pack pack = Pack.readMetaAndCreate(INFO, new Pack.ResourcesSupplier() {
			@Override
			public PackResources openPrimary(PackLocationInfo info) {
				return new GeneratedPack();
			}

			@Override
			public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
				return new GeneratedPack();
			}
		}, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
		if (pack != null) consumer.accept(pack);
	};

	private Map<String, byte[]> assets;
	private Map<String, byte[]> data;

	/** Client assets; built on first use so the server never draws textures. */
	private Map<String, byte[]> assets() {
		if (assets != null) return assets;
		assets = new HashMap<>();
		// Every slot gets a model, even empty ones, so the game doesn't log missing models for them.
		for (int i = 0; i < PromptCraft.ITEM_SLOTS; i++) {
			ModDef d = Defs.get("item_" + i);
			String path = "slot_item_" + i;
			String texture = "promptcraft:item/" + path;
			if (d != null && d.has3dModel()) {
				text(assets, "models/item/" + path + ".json", modelJson(d, texture));
			} else {
				String parent = d != null && d.props.attack_damage > 0 ? "minecraft:item/handheld" : "minecraft:item/generated";
				text(assets, "models/item/" + path + ".json", "{\"parent\":\"" + parent + "\",\"textures\":{\"layer0\":\"" + texture + "\"}}");
			}
			assets.put("textures/item/" + path + ".png", png(render(d, null, false)));
		}
		for (int i = 0; i < PromptCraft.BLOCK_SLOTS; i++) {
			ModDef d = Defs.get("block_" + i);
			String path = "slot_block_" + i;
			String texture = "promptcraft:block/" + path;
			text(assets, "blockstates/" + path + ".json", "{\"variants\":{\"\":{\"model\":\"promptcraft:block/" + path + "\"}}}");
			if (d != null && d.has3dModel()) {
				text(assets, "models/block/" + path + ".json", modelJson(d, texture));
			} else {
				text(assets, "models/block/" + path + ".json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"" + texture + "\"}}");
			}
			text(assets, "models/item/" + path + ".json", "{\"parent\":\"promptcraft:block/" + path + "\"}");
			assets.put("textures/block/" + path + ".png", png(d == null ? render(null, null, true) : texture(d)));
		}
		for (int i = 0; i < PromptCraft.SLOT_ARMOR.length; i++) {
			ModDef d = Defs.get("armor_" + i);
			String path = "slot_armor_" + i;
			int piece = i / PromptCraft.ARMOR_PER_PIECE;
			String material = armorMaterial(d);
			text(assets, "models/item/" + path + ".json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"promptcraft:item/" + path + "\"}}");
			assets.put("textures/item/" + path + ".png", png(render(d, armorIcon(material, piece), false)));
			if (d != null) {
				for (int layer = 1; layer <= 2; layer++) {
					BufferedImage worn = loadVanilla("models/armor/" + material + "_layer_" + layer, false);
					if (worn != null) assets.put("textures/models/armor/" + path + "_layer_" + layer + ".png", png(gradientMap(worn, d.texture.gradient, false)));
				}
			}
		}
		for (int i = 0; i < PromptCraft.MOB_SLOTS; i++) {
			ModDef d = Defs.get("mob_" + i);
			String path = "slot_mob_" + i;
			text(assets, "models/item/" + path + ".json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"promptcraft:item/" + path + "\"}}");
			assets.put("textures/item/" + path + ".png", png(render(d, "item/spawn_egg", false)));
		}
		return assets;
	}

	private Map<String, byte[]> data() {
		if (data != null) return data;
		data = new HashMap<>();
		for (int i = 0; i < PromptCraft.BLOCK_SLOTS; i++) {
			String path = "slot_block_" + i;
			text(data, "loot_table/blocks/" + path + ".json",
					"{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"promptcraft:" + path + "\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}");
		}
		for (ModDef d : Defs.all()) {
			String recipe = recipe(d);
			if (recipe != null) text(data, "recipe/" + d.path() + ".json", recipe);
		}
		return data;
	}

	private static String armorMaterial(ModDef d) {
		return d != null && ARMOR_MATERIALS.contains(d.armor_base) ? d.armor_base : "iron";
	}

	private static String armorIcon(String material, int piece) {
		return "item/" + (material.equals("gold") ? "golden" : material) + "_" + ARMOR_PIECE_NAMES.get(piece);
	}

	/** The creation's 16x16 texture as the game will see it. Blocks without a 3D model are opaque. */
	public static BufferedImage texture(ModDef d) {
		if (d.isArmor()) return render(d, armorIcon(armorMaterial(d), d.armorPiece()), false);
		return render(d, null, d.isBlock() && !d.has3dModel());
	}

	/** Claude's elements with every face pointed at the creation's single texture. */
	private static String modelJson(ModDef d, String texture) {
		JsonObject json = new JsonObject();
		json.addProperty("parent", "minecraft:block/block");
		JsonObject textures = new JsonObject();
		textures.addProperty("0", texture);
		textures.addProperty("particle", texture);
		json.add("textures", textures);
		JsonArray elements = d.model.getAsJsonArray("elements").deepCopy();
		for (var el : elements) {
			if (!el.isJsonObject() || !el.getAsJsonObject().has("faces")) continue;
			for (var face : el.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
				if (face.getValue().isJsonObject()) face.getValue().getAsJsonObject().addProperty("texture", "#0");
			}
		}
		json.add("elements", elements);
		if (d.model.has("display") && d.model.get("display").isJsonObject()) json.add("display", d.model.get("display"));
		return json.toString();
	}

	private static void text(Map<String, byte[]> map, String path, String json) {
		map.put(path, json.getBytes(StandardCharsets.UTF_8));
	}

	private static String recipe(ModDef d) {
		ModDef.Recipe r = d.recipe;
		if (r == null || r.pattern == null || r.pattern.isEmpty() || r.key == null || r.key.isEmpty()) return null;
		JsonObject json = new JsonObject();
		json.addProperty("type", "minecraft:crafting_shaped");
		json.addProperty("category", "misc");
		JsonArray pattern = new JsonArray();
		for (String row : r.pattern.subList(0, Math.min(3, r.pattern.size()))) pattern.add(row.length() > 3 ? row.substring(0, 3) : row);
		json.add("pattern", pattern);
		JsonObject key = new JsonObject();
		r.key.forEach((k, v) -> {
			if (k.isEmpty() || v == null) return;
			JsonObject ingredient = new JsonObject();
			if (v.startsWith("#")) ingredient.addProperty("tag", v.substring(1));
			else ingredient.addProperty("item", v);
			key.add(k.substring(0, 1), ingredient);
		});
		json.add("key", key);
		JsonObject result = new JsonObject();
		result.addProperty("id", "promptcraft:" + d.path());
		result.addProperty("count", Math.max(1, Math.min(64, r.count)));
		json.add("result", result);
		return json.toString();
	}

	/**
	 * A 16x16 texture: a recoloured vanilla base when there is one (which keeps vanilla-quality shading),
	 * with Claude's rows painted on top; or Claude's rows alone.
	 */
	static BufferedImage render(ModDef d, String defaultBase, boolean opaque) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		if (d == null) {
			if (opaque) fill(img, 0xFF6B6B6B);
			return img;
		}
		ModDef.Texture t = d.texture;
		String basePath = t.base != null && !t.base.isBlank() ? t.base : defaultBase;
		BufferedImage base = basePath == null ? null : loadVanilla(basePath, d.isBlock());
		// Leather armor is brown, so wood detection would stop it recolouring.
		if (base != null) img = gradientMap(base.getSubimage(0, 0, 16, 16), t.gradient, t.keep_wood && !d.isArmor());

		Map<Character, Integer> palette = new HashMap<>();
		t.palette.forEach((k, v) -> {
			Integer c = parseColor(v);
			if (!k.isEmpty() && c != null) palette.put(k.charAt(0), c);
		});
		for (int y = 0; y < 16 && y < t.rows.size(); y++) {
			String row = t.rows.get(y) == null ? "" : t.rows.get(y);
			for (int x = 0; x < 16 && x < row.length(); x++) {
				Integer c = palette.get(row.charAt(x));
				if (c != null) img.setRGB(x, y, c);
			}
		}
		if (opaque) fill(img, palette.values().stream().findFirst().orElse(0xFF6B6B6B));
		return img;
	}

	/** Fills transparent pixels; block textures render solid, so holes would show as black. */
	private static void fill(BufferedImage img, int color) {
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < img.getWidth(); x++)
				if ((img.getRGB(x, y) >>> 24) == 0) img.setRGB(x, y, color);
	}

	/** Loads a vanilla texture from the game jar, e.g. "item/diamond_sword". Returns null if it doesn't exist. */
	private static BufferedImage loadVanilla(String path, boolean block) {
		String p = path.strip().replaceFirst("^minecraft:", "").replaceFirst("^textures/", "").replaceFirst("\\.png$", "");
		if (!p.contains("/")) p = (block ? "block/" : "item/") + p;
		try (InputStream in = GeneratedPack.class.getClassLoader().getResourceAsStream("assets/minecraft/textures/" + p + ".png")) {
			if (in == null) return null;
			BufferedImage raw = ImageIO.read(in);
			BufferedImage argb = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
			argb.getGraphics().drawImage(raw, 0, 0, null);
			// Animated textures are vertical strips; the 16x16 callers take the first frame.
			return argb.getWidth() < 16 || argb.getHeight() < 16 ? null : argb;
		} catch (Exception e) {
			return null;
		}
	}

	/** Remaps each pixel's brightness onto the gradient, keeping the base's outline and shading. */
	private static BufferedImage gradientMap(BufferedImage src, List<String> gradient, boolean keepWood) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		List<Integer> stops = new ArrayList<>();
		if (gradient != null) for (String g : gradient) {
			Integer c = parseColor(g);
			if (c != null) stops.add(c);
		}
		if (stops.size() == 1) stops = List.of(scale(stops.get(0), 0.25f), stops.get(0), scale(stops.get(0), 1.6f));

		float min = 1, max = 0;
		boolean[] recolor = new boolean[src.getWidth() * src.getHeight()];
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int argb = src.getRGB(x, y);
				if ((argb >>> 24) == 0 || stops.isEmpty() || (keepWood && isWood(argb))) continue;
				recolor[y * src.getWidth() + x] = true;
				float l = luminance(argb);
				min = Math.min(min, l);
				max = Math.max(max, l);
			}
		}
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int argb = src.getRGB(x, y);
				if (!recolor[y * src.getWidth() + x]) {
					out.setRGB(x, y, argb);
					continue;
				}
				float t = max > min ? (luminance(argb) - min) / (max - min) : 0.5f;
				out.setRGB(x, y, (argb & 0xFF000000) | (sample(stops, t) & 0xFFFFFF));
			}
		}
		return out;
	}

	private static float luminance(int argb) {
		return (0.299f * ((argb >> 16) & 0xFF) + 0.587f * ((argb >> 8) & 0xFF) + 0.114f * (argb & 0xFF)) / 255f;
	}

	/** Vanilla stick and wood browns, so tool handles keep their colour. */
	private static boolean isWood(int argb) {
		float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
		float hue = hsb[0] * 360;
		return hue >= 15 && hue <= 50 && hsb[1] > 0.35f && hsb[2] < 0.62f;
	}

	private static int sample(List<Integer> stops, float t) {
		float pos = Math.max(0, Math.min(1, t)) * (stops.size() - 1);
		int i = Math.min((int) pos, stops.size() - 2);
		float f = pos - i;
		int a = stops.get(i), b = stops.get(i + 1);
		int r = Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
		int bl = Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	private static int scale(int argb, float k) {
		int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * k));
		int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * k));
		int b = Math.min(255, Math.round((argb & 0xFF) * k));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static byte[] png(BufferedImage img) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(img, "png", out);
			return out.toByteArray();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Integer parseColor(String hex) {
		if (hex == null) return null;
		String h = hex.strip().replace("#", "");
		try {
			if (h.length() == 6) return 0xFF000000 | Integer.parseInt(h, 16);
			if (h.length() == 8) return (int) Long.parseLong(h, 16);
		} catch (NumberFormatException ignored) {
		}
		return null;
	}

	private Map<String, byte[]> files(PackType type) {
		return type == PackType.CLIENT_RESOURCES ? assets() : data();
	}

	@Override
	public IoSupplier<InputStream> getRootResource(String... elements) {
		return null;
	}

	@Override
	public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
		if (!location.getNamespace().equals(PromptCraft.MOD_ID)) return null;
		byte[] bytes = files(type).get(location.getPath());
		return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
	}

	@Override
	public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
		if (!namespace.equals(PromptCraft.MOD_ID)) return;
		String prefix = path.endsWith("/") ? path : path + "/";
		files(type).forEach((file, bytes) -> {
			if (file.startsWith(prefix)) output.accept(PromptCraft.id(file), () -> new ByteArrayInputStream(bytes));
		});
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		return Set.of(PromptCraft.MOD_ID);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
		if (serializer != PackMetadataSection.TYPE) return null;
		// One pack serves both resources and data, so accept every pack format.
		int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
		return (T) new PackMetadataSection(Component.literal("Things Claude made"), format, Optional.of(new InclusiveRange<>(0, 9999)));
	}

	@Override
	public PackLocationInfo location() {
		return INFO;
	}

	@Override
	public void close() {
	}
}
