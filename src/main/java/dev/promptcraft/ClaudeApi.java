package dev.promptcraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/** Sends a prompt to the Claude Messages API and applies the tool calls that come back. */
public final class ClaudeApi {
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
	private static final Set<UUID> BUSY = ConcurrentHashMap.newKeySet();

	private static final String SYSTEM = """
			You are the AI Computer, a block inside Minecraft Java Edition 1.21.1. The player types a request and you fulfil it with tool calls, plus ONE short, punchy sentence of text.

			TOOLS
			- create_mod: invent something NEW that becomes real in-game instantly. kind is one of:
			  item (weapons, tools, food, gadgets), block, armor (wearables), mob (a custom creature), event (a temporary world effect like something raining from the sky).
			  To change an existing creation, call create_mod again with replace = its name and the full new definition.
			- run_commands: one-off world edits (build structures, weather, time). Commands run as the player: ~ ~ ~ is their feet, ^ ^ ^ is relative to where they look. Prefer fill over many setblock calls. At most ~300 commands.
			- delete_mod: remove a creation or stop an event, by name.
			You may call several tools in one reply, and combine kinds: "crystals that rain from the sky" = a crystal block (with on_place / on_step behaviours) PLUS an event that rains it.
			Refer to a creation from commands, equipment or rain as {Its Name} (e.g. "give @s {Soul Crystal}"); it becomes its real id, even for creations made in the same reply.

			UNDERSTAND THE REQUEST FIRST: build exactly what was asked, every part of it. Who/what does it affect, how is it triggered, what does it look like? Don't swap it for something easier (a raining hazard is not a sword; a villager-killing golem must actually hunt villagers). If something truly can't be done with these tools, make the closest version and say plainly in your sentence what's missing.

			TEXTURE (looks matter most: this is what people see in the video)
			Start from a vanilla texture and recolour it ONLY when the base already has the right shape (a fire sword from diamond_sword: yes; a gauntlet from gold_ingot: NO, that just looks like an ingot). Otherwise draw it yourself or build a 3D model. Recolouring keeps Minecraft's own hand-made shading and outlines:
			- base: a vanilla texture path. Items: item/diamond_sword, item/diamond_axe, item/diamond_pickaxe, item/diamond_shovel, item/diamond_hoe, item/mace, item/bow, item/stick, item/blaze_rod, item/breeze_rod, item/bone, item/apple, item/golden_apple, item/bread, item/cookie, item/cooked_beef, item/carrot, item/potion, item/honey_bottle, item/snowball, item/ender_pearl, item/ender_eye, item/fire_charge, item/wind_charge, item/nether_star, item/heart_of_the_sea, item/emerald, item/diamond, item/amethyst_shard, item/echo_shard, item/iron_ingot, item/gold_nugget, item/totem_of_undying, item/book, item/feather, item/slime_ball, item/magma_cream, item/redstone, item/gunpowder. Blocks: block/stone, block/cobblestone, block/bricks, block/stone_bricks, block/obsidian, block/diamond_block, block/gold_block, block/iron_block, block/emerald_block, block/glowstone, block/sea_lantern, block/oak_planks, block/oak_log, block/slime_block, block/amethyst_block, block/sculk, block/magma, block/netherrack, block/end_stone, block/purpur_block, block/prismarine, block/ice, block/sand, block/dirt, block/copper_block, block/redstone_block, block/lapis_block, block/bookshelf.
			- gradient: 3-5 colours from darkest to lightest; the base's shading is remapped onto them. Make the darkest nearly black (it becomes the outline) and the lightest a bright highlight. Example fire: ["#1a0500","#8a1a05","#e04a10","#ffa030","#fff0a0"].
			- keep_wood (default true): wooden tool handles keep their brown.
			- palette + rows (optional): details painted on top of the base, like gems, eyes or runes. rows is 16 strings of 16 chars, "." = leave the base pixel. Keep it to a few pixels.
			HELD ITEMS FACE TOP-RIGHT: Minecraft holds sprites by the bottom-left corner. Draw the handle/grip at the bottom-left and the business end (blade tip, gun barrel, staff head) toward the top-right. A gun's barrel points up-right, never left. Guns, blasters and launchers usually look best as 3D models.
			Only when no base fits at all, omit base and draw the full 16x16 in rows, like a Minecraft pixel artist:
			- bold silhouette filling ~12-14px; readable at a glance
			- 1px outline in a darker, more saturated version of the neighbouring colour (not pure black everywhere)
			- one light source from the top-left: highlights top-left, shadows bottom-right; never pillow-shade
			- 3-4 shades per material plus 1-2 accent colours; no lone stray pixels; avoid gradients of single pixels (banding)

			REFERENCES: for anything not in vanilla Minecraft (pop culture, real objects, mythology), web search first to learn its key shapes, colours and iconic details, then draw your own original pixel art from that. Never copy another artist's texture.

			3D MODELS (optional "model" on items and blocks): {"elements":[{"from":[x,y,z],"to":[x,y,z],"faces":{"north":{"uv":[u1,v1,u2,v2]},"south":{...},"east":{...},"west":{...},"up":{...},"down":{...}}}],"display":{...}}
			- Coordinates 0-16 in Minecraft's block-model format; boxes only, no rotation; 3-25 boxes.
			- Every face samples the creation's ONE 16x16 texture, so design the texture as an atlas: give each part (metal, gem, handle, glow) its own small region and point each face's uv at the right region.
			- Use 3D when the shape matters when held or placed: gauntlets, hammers, staffs, orbs, lamps, statues, pedestals, mini machines. Simple flat things (gems, food, plain swords) look better as normal sprites.
			- For held 3D items add display transforms (rotation, translation, scale) for thirdperson_righthand, firstperson_righthand and gui so it sits nicely in the hand and inventory.
			- Blocks with a model still collide as a full cube; transparent texture pixels show through.

			ARMOR (wearables): kind "armor", armor_slot head|chest|legs|feet, armor_base leather|chainmail|iron|gold|diamond|netherite (the shape it starts from; the gradient recolours both the icon and the worn look). Leave texture.base empty; it is picked from armor_base. For a full set, call create_mod once per piece with matching gradients. Hats, crowns, masks are helmets; capes and robes are chestplates; boots and shoes are feet. When the shape differs from plain armor (a crown's points, a mask's eyes, gems), paint those pixels on the icon with palette + rows.
			Event on_tick_worn runs twice per second while worn, as the wearer (great for effect give @s night_vision 15 0 true).

			PROPS
			max_stack (default 64), durability (0 = unbreakable; >0 makes it unstackable), attack_damage (total damage; diamond sword = 7), food (hunger points; 0 = not edible), light (blocks, 0-15), hardness (blocks; dirt 0.5, stone 1.5, obsidian 50), armor (defense points per piece; diamond: head 3, chest 8, legs 6, feet 3), toughness (diamond 2).

			BEHAVIORS: lists of vanilla 1.21.1 commands (no leading slash), run at permission level 2, per event:
			- on_use (item right-click): as the player, at the player.
			- on_hit (item hits a mob): as the VICTIM, at the victim (@s = victim, @p = attacker).
			- on_tick_held: twice per second while held, as the player.
			- on_eat: after eating it (needs props.food > 0).
			- on_step (block): twice per second, as the entity standing on it.
			- on_right_click (block): as the player, positioned at the block centre.
			- on_break (block): as the player, positioned at the block.
			- on_place (block): whenever the block appears (placed, landed from the sky, set by command), positioned at the block. Great for "burns what it lands on": e.g. "execute as @e[distance=..2] run damage @s 4 minecraft:in_fire", "fill ~-1 ~-1 ~-1 ~1 ~-1 ~1 soul_soil replace #minecraft:dirt", "fill ~-1 ~ ~-1 ~1 ~ ~1 soul_fire replace air".
			- on_mob_tick (mob): twice per second, as the mob. on_death (mob): as the mob where it died.
			Good building blocks: effect give, particle, playsound, summon (lightning_bolt, tnt, mobs with NBT), execute, fill, setblock, tp, give, damage.

			NATIVE (built-in, reliable, use these when they fit)
			cooldown_ticks (20 = 1 second), launch (upward velocity on use/step; 1.0 is about 5 blocks), bouncy (block, slime-like bounce), projectile_speed (default 1.5),
			projectile: ANY entity id, shot where the player looks on use. Real projectiles (fireball, small_fireball, wither_skull, dragon_fireball, wind_charge, snowball, egg, arrow, trident, ender_pearl) fly normally; anything else (pig, tnt, creeper, cow...) is launched like a cannonball. A "pig pistol" shoots pig.

			MOBS: kind "mob" with "mob": {base: vanilla entity id to build on, health, damage, speed (multiplier), scale (size, 0.1-10), glow, glow_color (red, gold, aqua, dark_purple, green...), attacks: [entity ids or "player"] (it hunts these; replaces its normal targets), effects: ["strength 1", "fire_resistance"], equipment: {mainhand/head/chest/legs/feet: item id or {Creation}}, spawn_count}.
			It keeps the base mob's look (no custom texture), so pick the base that looks closest and use name, glow, scale, gear and particles (on_mob_tick) to sell it. To fight, the base must be able to melee: zombie, husk, iron_golem, wolf, spider, vindicator, piglin_brute, ravager, polar_bear, warden, hoglin... (pigs, cows, villagers can't attack). The player gets a spawn egg and spawn_count spawn in front of them. texture.gradient recolours the egg.

			EVENTS: kind "event" with "event": {duration_seconds (max 600), commands (run as and at every player every every_ticks ticks), every_ticks, rain: {what: block id, entity id or {Creation}, per_second, radius, height, damage (falling blocks hurt what they hit)}}. No texture needed. Stop one early with delete_mod.

			RECIPE: shaped crafting. pattern is up to 3 rows of up to 3 chars (space = empty); key maps chars to item ids like "minecraft:diamond" or tags like "#minecraft:planks". Make the cost fair for how powerful it is.

			This is for showing off on video: make creations fun, over-the-top and visual (particles, sounds), but keep them working.
			""";

	private static final String TOOLS = """
			[
			 {"name":"create_mod","description":"Create (or with replace, overwrite) a custom item or block that appears in-game instantly.",
			  "input_schema":{"type":"object","required":["kind","name"],"properties":{
			   "kind":{"type":"string","enum":["item","block","armor","mob","event"]},
			   "mob":{"type":"object","properties":{
			     "base":{"type":"string"},"health":{"type":"number"},"damage":{"type":"number"},"speed":{"type":"number"},"scale":{"type":"number"},
			     "glow":{"type":"boolean"},"glow_color":{"type":"string"},
			     "attacks":{"type":"array","items":{"type":"string"}},"effects":{"type":"array","items":{"type":"string"}},
			     "equipment":{"type":"object","additionalProperties":{"type":"string"}},"spawn_count":{"type":"integer"}}},
			   "event":{"type":"object","properties":{
			     "duration_seconds":{"type":"integer"},"every_ticks":{"type":"integer"},"commands":{"type":"array","items":{"type":"string"}},
			     "rain":{"type":"object","properties":{"what":{"type":"string"},"per_second":{"type":"integer"},"radius":{"type":"integer"},"height":{"type":"integer"},"damage":{"type":"number"}}}}},
			   "name":{"type":"string"},
			   "replace":{"type":"string","description":"Name of an existing creation to overwrite."},
			   "armor_slot":{"type":"string","enum":["head","chest","legs","feet"]},
			   "armor_base":{"type":"string","enum":["leather","chainmail","iron","gold","diamond","netherite"]},
			   "model":{"type":"object","description":"Optional 3D model in block-model format.","properties":{
			     "elements":{"type":"array","items":{"type":"object","properties":{
			       "from":{"type":"array","items":{"type":"number"}},
			       "to":{"type":"array","items":{"type":"number"}},
			       "faces":{"type":"object","additionalProperties":{"type":"object","properties":{"uv":{"type":"array","items":{"type":"number"}}}}}}}},
			     "display":{"type":"object","additionalProperties":{"type":"object","properties":{
			       "rotation":{"type":"array","items":{"type":"number"}},
			       "translation":{"type":"array","items":{"type":"number"}},
			       "scale":{"type":"array","items":{"type":"number"}}}}}}},
			   "texture":{"type":"object","properties":{
			     "base":{"type":"string"},
			     "gradient":{"type":"array","items":{"type":"string"}},
			     "keep_wood":{"type":"boolean"},
			     "palette":{"type":"object","additionalProperties":{"type":"string"}},
			     "rows":{"type":"array","items":{"type":"string"}}}},
			   "props":{"type":"object","properties":{"max_stack":{"type":"integer"},"durability":{"type":"integer"},"attack_damage":{"type":"number"},"food":{"type":"integer"},"light":{"type":"integer"},"hardness":{"type":"number"},"armor":{"type":"number"},"toughness":{"type":"number"}}},
			   "behaviors":{"type":"object","description":"Event name -> list of commands. Events: on_use, on_hit, on_tick_held, on_eat, on_step, on_right_click, on_break, on_place, on_tick_worn, on_mob_tick, on_death.","additionalProperties":{"type":"array","items":{"type":"string"}}},
			   "native":{"type":"object","properties":{"cooldown_ticks":{"type":"integer"},"launch":{"type":"number"},"bouncy":{"type":"boolean"},"projectile":{"type":"string"},"projectile_speed":{"type":"number"}}},
			   "recipe":{"type":"object","properties":{"pattern":{"type":"array","items":{"type":"string"}},"key":{"type":"object","additionalProperties":{"type":"string"}},"count":{"type":"integer"}}}}}},
			 {"name":"run_commands","description":"Run vanilla commands once, as the player, spread over a few ticks.",
			  "input_schema":{"type":"object","required":["commands"],"properties":{"commands":{"type":"array","items":{"type":"string"}}}}},
			 {"name":"delete_mod","description":"Delete a creation by name, freeing its slot.",
			  "input_schema":{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}}
			]
			""";

	private ClaudeApi() {}

	public static void handlePrompt(ServerPlayer player, String prompt) {
		MinecraftServer server = player.server;
		ClaudeConfig cfg = ClaudeConfig.INSTANCE;
		if (!(server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2) || cfg.allow_everyone)) {
			reply(player, "Only operators can use the AI Computer.");
			return;
		}
		String key = cfg.apiKey();
		if (!cfg.useClaudeCode() && key.isEmpty()) {
			reply(player, "No API key yet. Put yours in config/promptcraft.json (api_key) and restart the game.");
			return;
		}
		if (!BUSY.add(player.getUUID())) {
			reply(player, "Still working on your last request...");
			return;
		}
		String userMessage = context(player) + "\nRequest: " + prompt;
		UUID uuid = player.getUUID();
		Asker asker = cfg.useClaudeCode()
				? (text, images, search) -> ClaudeCodeBackend.ask(SYSTEM, TOOLS, text, images, search)
				: (text, images, search) -> apiAsk(key, text, images, search);
		// First pass may look things up online; then Claude checks renders of anything drawn from scratch or in 3D.
		asker.ask(userMessage, List.of(), true)
				.thenCompose(content -> Review.review(content, asker))
				.whenComplete((content, err) -> server.execute(() -> {
					BUSY.remove(uuid);
					ServerPlayer p = server.getPlayerList().getPlayer(uuid);
					if (p == null) return;
					if (err != null) {
						Throwable cause = err.getCause() != null ? err.getCause() : err;
						reply(p, "Claude failed: " + cause.getMessage());
					} else {
						applyContent(p, content, false);
					}
				}));
	}

	/** Models the computer's model button cycles through. */
	public static final List<String> MODELS = List.of("claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-5-5");

	static void setModel(ServerPlayer player, String model) {
		MinecraftServer server = player.server;
		if (!(server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2))) return;
		if (!MODELS.contains(model)) return;
		ClaudeConfig.INSTANCE.model = model;
		ClaudeConfig.save();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(p, new Payloads.Model(model));
	}

	/** One request to Claude: text plus optional images; returns Messages-API-style content blocks. */
	public interface Asker {
		CompletableFuture<JsonArray> ask(String text, List<Path> images, boolean search);
	}

	private static CompletableFuture<JsonArray> apiAsk(String key, String text, List<Path> images, boolean search) {
		ClaudeConfig cfg = ClaudeConfig.INSTANCE;
		JsonObject system = new JsonObject();
		system.addProperty("type", "text");
		system.addProperty("text", SYSTEM);
		JsonObject cache = new JsonObject();
		cache.addProperty("type", "ephemeral");
		system.add("cache_control", cache);
		JsonArray systemArr = new JsonArray();
		systemArr.add(system);

		JsonArray userContent = new JsonArray();
		try {
			for (Path image : images) {
				JsonObject source = new JsonObject();
				source.addProperty("type", "base64");
				source.addProperty("media_type", "image/png");
				source.addProperty("data", Base64.getEncoder().encodeToString(Files.readAllBytes(image)));
				JsonObject block = new JsonObject();
				block.addProperty("type", "image");
				block.add("source", source);
				userContent.add(block);
			}
		} catch (Exception e) {
			return CompletableFuture.failedFuture(e);
		}
		JsonObject textBlock = new JsonObject();
		textBlock.addProperty("type", "text");
		textBlock.addProperty("text", text);
		userContent.add(textBlock);

		JsonObject message = new JsonObject();
		message.addProperty("role", "user");
		message.add("content", userContent);
		JsonArray messages = new JsonArray();
		messages.add(message);

		JsonArray tools = JsonParser.parseString(TOOLS).getAsJsonArray();
		if (search) {
			JsonObject web = new JsonObject();
			web.addProperty("type", "web_search_20250305");
			web.addProperty("name", "web_search");
			web.addProperty("max_uses", 3);
			tools.add(web);
		}
		JsonObject body = new JsonObject();
		body.addProperty("model", cfg.model);
		body.addProperty("max_tokens", cfg.max_tokens);
		body.add("system", systemArr);
		body.add("tools", tools);
		body.add("messages", messages);

		HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
				.timeout(Duration.ofMinutes(4))
				.header("x-api-key", key)
				.header("anthropic-version", "2023-06-01")
				.header("content-type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			JsonObject json;
			try {
				json = JsonParser.parseString(res.body()).getAsJsonObject();
			} catch (Exception e) {
				throw new RuntimeException("unreadable reply (HTTP " + res.statusCode() + ")");
			}
			if (res.statusCode() != 200) {
				String msg = json.has("error") ? json.getAsJsonObject("error").get("message").getAsString() : res.body();
				throw new RuntimeException("API error " + res.statusCode() + ": " + msg);
			}
			JsonArray content = json.getAsJsonArray("content");
			if (json.has("stop_reason") && "max_tokens".equals(json.get("stop_reason").getAsString())) {
				JsonObject note = new JsonObject();
				note.addProperty("type", "text");
				note.addProperty("text", "(I ran out of room. Try a smaller request.)");
				content.add(note);
			}
			return content;
		});
	}

	private static String context(ServerPlayer player) {
		StringBuilder sb = new StringBuilder();
		sb.append("Player ").append(player.getGameProfile().getName())
				.append(" stands at ").append(player.blockPosition().toShortString())
				.append(", facing ").append(player.getDirection().getName())
				.append(", in ").append(player.level().dimension().location())
				.append(".\nExisting creations: ");
		List<JsonObject> summary = new ArrayList<>();
		for (ModDef d : Defs.all()) {
			JsonObject o = GSON.toJsonTree(d).getAsJsonObject();
			o.remove("texture");
			o.remove("model");
			o.remove("slot");
			summary.add(o);
		}
		sb.append(summary.isEmpty() ? "none" : GSON.toJson(summary));
		List<String> events = WorldEvents.activeNames();
		if (!events.isEmpty()) sb.append("\nActive world events: ").append(String.join(", ", events));
		return sb.toString();
	}

	/** Applies Messages-API-style content blocks: text plus create_mod / run_commands / delete_mod tool calls. */
	private static void applyContent(ServerPlayer player, JsonArray content, boolean truncated) {
		StringBuilder text = new StringBuilder();
		List<ItemStack> gifts = new ArrayList<>();
		List<String> recipes = new ArrayList<>();
		List<ModDef> mobs = new ArrayList<>();
		// Events start after everything else, so they can rain creations made in the same reply.
		List<ModDef> events = new ArrayList<>();
		boolean changed = false;
		for (JsonElement el : content) {
			JsonObject block = el.getAsJsonObject();
			String type = block.get("type").getAsString();
			if (type.equals("text")) {
				text.append(block.get("text").getAsString().strip()).append(' ');
			} else if (type.equals("tool_use")) {
				JsonObject input = block.getAsJsonObject("input");
				try {
					switch (block.get("name").getAsString()) {
						case "create_mod" -> {
							if ("event".equals(input.has("kind") ? input.get("kind").getAsString() : "")) {
								events.add(GSON.fromJson(input, ModDef.class).normalize());
								continue;
							}
							ModDef d = createMod(input);
							if (d == null) {
								text.append("(No free slots left. Delete a creation first.) ");
							} else {
								gifts.add(new ItemStack(Defs.itemFor(d)));
								if (d.recipe != null) recipes.add(d.path());
								if (d.isMob()) mobs.add(d);
								changed = true;
							}
						}
						case "run_commands" -> {
							List<String> cmds = new ArrayList<>();
							for (JsonElement c : input.getAsJsonArray("commands")) cmds.add(c.getAsString());
							Behaviors.enqueue(cmds, player.createCommandSourceStack());
						}
						case "delete_mod" -> {
							String name = input.get("name").getAsString();
							WorldEvents.stop(name);
							ModDef d = Defs.byName(name);
							if (d != null) {
								Defs.remove(d.slot);
								changed = true;
							}
						}
						default -> {}
					}
				} catch (Exception e) {
					PromptCraft.LOG.warn("Bad tool call from Claude: {}", input, e);
					text.append("(One of my tool calls failed: ").append(e.getMessage()).append(") ");
				}
			}
		}
		if (truncated) {
			text.append("(I ran out of room. Try a smaller request.)");
		}

		if (changed) {
			MinecraftServer server = player.server;
			UUID uuid = player.getUUID();
			// Sync is sent before the items so clients already know the new names and stats.
			Defs.saveAndSync(server).thenRun(() -> server.execute(() -> {
				ServerPlayer p = server.getPlayerList().getPlayer(uuid);
				if (p != null && !recipes.isEmpty()) p.awardRecipesByKey(recipes.stream().map(PromptCraft::id).toList());
			}));
			for (ItemStack stack : gifts) {
				if (!player.getInventory().add(stack)) player.drop(stack, false);
			}
		}
		for (ModDef d : mobs) CustomMobs.spawnNear(player, d);
		for (ModDef d : events) WorldEvents.start(player.server, d);
		reply(player, text.isEmpty() ? "Done." : text.toString().strip());
	}

	private static ModDef createMod(JsonObject input) {
		ModDef d = GSON.fromJson(input, ModDef.class).normalize();
		ModDef old = Defs.byName(d.replace != null ? d.replace : d.name);
		d.slot = old != null && Defs.fits(old, d) ? old.slot : Defs.freeSlot(d);
		d.replace = null;
		if (d.slot == null) return null;
		Defs.put(d);
		return d;
	}

	private static void reply(ServerPlayer player, String text) {
		ServerPlayNetworking.send(player, new Payloads.Reply(text.length() > 7900 ? text.substring(0, 7900) : text));
	}
}
