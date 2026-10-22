package dev.promptcraft;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One creation Claude made: the exact shape of the create_mod tool input, plus the slot it lives in. */
public class ModDef {
	public String slot;
	public String kind = "item";
	public String name = "Mystery Thing";
	public String replace;
	public Texture texture = new Texture();
	public Props props = new Props();
	public Map<String, List<String>> behaviors = new HashMap<>();
	@SerializedName("native") public Native nativeProps = new Native();
	public Recipe recipe;

	public String armor_slot;
	public String armor_base;
	/** Optional 3D model: {"elements": [...], "display": {...}} in Minecraft's block-model format. */
	public JsonObject model;
	public MobSpec mob;
	public EventSpec event;

	/** A custom mob built on a vanilla one. */
	public static class MobSpec {
		public String base = "minecraft:zombie";
		public double health;
		public double damage;
		/** Multiplier on the base mob's speed. */
		public double speed = 1;
		/** Size multiplier (1 = normal). */
		public double scale = 1;
		public boolean glow;
		public String glow_color = "red";
		/** Entity ids to hunt, or "player". Empty keeps the base mob's own targets. */
		public List<String> attacks = new ArrayList<>();
		public List<String> effects = new ArrayList<>();
		/** Slot (mainhand, offhand, head, chest, legs, feet) to item id or {Creation Name}. */
		public Map<String, String> equipment = new HashMap<>();
		public int spawn_count = 1;
	}

	/** A temporary world effect, e.g. raining crystals for 30 seconds. */
	public static class EventSpec {
		public int duration_seconds = 30;
		/** Commands run as and at every player each period. */
		public List<String> commands = new ArrayList<>();
		public int every_ticks = 20;
		public Rain rain;
	}

	public static class Rain {
		/** A block id, entity id, or creation name to drop from the sky. */
		public String what;
		public int per_second = 4;
		public int radius = 16;
		public int height = 20;
		/** Damage falling blocks deal to whatever they land on. */
		public float damage;
	}

	public static class Texture {
		/** A vanilla texture to start from, e.g. "item/diamond_sword" or "block/stone". */
		public String base;
		/** Colours dark to light; the base's shading is remapped onto them. */
		public List<String> gradient;
		/** Leave brown wooden handles alone when recolouring. */
		public boolean keep_wood = true;
		public Map<String, String> palette = new HashMap<>();
		/** Fully custom 16x16 art when there is no base, otherwise details painted on top of it. */
		public List<String> rows = new ArrayList<>();
	}

	public static class Props {
		public int max_stack = 64;
		public int durability;
		public double attack_damage;
		public int food;
		public int light;
		public float hardness = 1.5f;
		public double armor;
		public double toughness;
	}

	public static class Native {
		public int cooldown_ticks;
		public double launch;
		public boolean bouncy;
		public String projectile;
		public float projectile_speed = 1.5f;
	}

	public static class Recipe {
		public List<String> pattern;
		public Map<String, String> key;
		public int count = 1;
	}

	/** Gson leaves nested objects null when Claude sends null, so fill them in. */
	public ModDef normalize() {
		kind = List.of("block", "armor", "mob", "event").contains(kind) ? kind : "item";
		if (isMob() && mob == null) mob = new MobSpec();
		if (mob != null) {
			if (mob.attacks == null) mob.attacks = new ArrayList<>();
			if (mob.effects == null) mob.effects = new ArrayList<>();
			if (mob.equipment == null) mob.equipment = new HashMap<>();
		}
		if (isEvent() && event == null) event = new EventSpec();
		if (event != null && event.commands == null) event.commands = new ArrayList<>();
		if (texture == null) texture = new Texture();
		if (texture.palette == null) texture.palette = new HashMap<>();
		if (texture.rows == null) texture.rows = new ArrayList<>();
		if (props == null) props = new Props();
		if (behaviors == null) behaviors = new HashMap<>();
		if (nativeProps == null) nativeProps = new Native();
		return this;
	}

	public boolean isBlock() {
		return "block".equals(kind);
	}

	public boolean has3dModel() {
		return model != null && model.has("elements") && model.get("elements").isJsonArray() && !model.getAsJsonArray("elements").isEmpty();
	}

	public boolean isArmor() {
		return "armor".equals(kind);
	}

	public boolean isMob() {
		return "mob".equals(kind);
	}

	public boolean isEvent() {
		return "event".equals(kind);
	}

	public static final List<String> ARMOR_PIECES = List.of("head", "chest", "legs", "feet");

	/** 0 head, 1 chest, 2 legs, 3 feet. */
	public int armorPiece() {
		int i = ARMOR_PIECES.indexOf(armor_slot);
		return i < 0 ? 0 : i;
	}

	public int index() {
		return Integer.parseInt(slot.substring(slot.indexOf('_') + 1));
	}

	/** Registry path, e.g. slot_item_3. */
	public String path() {
		return "slot_" + slot;
	}

	public List<String> on(String event) {
		List<String> cmds = behaviors.get(event);
		return cmds == null ? List.of() : cmds;
	}
}
