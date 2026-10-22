package dev.promptcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * All creations in the current world, keyed by slot. Server and client share this in singleplayer,
 * so updates swap in a fresh map instead of mutating the one other threads may be reading.
 */
public final class Defs {
	private static final Gson GSON = new GsonBuilder().create();
	private static volatile Map<String, ModDef> bySlot = Map.of();
	private static Path file;

	private Defs() {}

	public static ModDef get(String slot) {
		return bySlot.get(slot);
	}

	public static Collection<ModDef> all() {
		List<ModDef> list = new ArrayList<>(bySlot.values());
		list.sort(Comparator.comparing((ModDef d) -> d.kind).thenComparingInt(ModDef::index));
		return list;
	}

	public static ModDef byName(String name) {
		if (name == null) return null;
		for (ModDef d : bySlot.values()) if (d.name.equalsIgnoreCase(name.strip())) return d;
		return null;
	}

	/** Armor slots are grouped by piece: armor_0..15 are helmets, 16..31 chestplates, and so on. */
	public static String freeSlot(ModDef d) {
		int from = 0;
		int to = d.isBlock() ? PromptCraft.BLOCK_SLOTS : d.isMob() ? PromptCraft.MOB_SLOTS : PromptCraft.ITEM_SLOTS;
		if (d.isArmor()) {
			from = d.armorPiece() * PromptCraft.ARMOR_PER_PIECE;
			to = from + PromptCraft.ARMOR_PER_PIECE;
		}
		for (int i = from; i < to; i++) if (!bySlot.containsKey(d.kind + "_" + i)) return d.kind + "_" + i;
		return null;
	}

	/** Whether d can take over old's slot (same kind, and same armor piece). */
	public static boolean fits(ModDef old, ModDef d) {
		if (!old.kind.equals(d.kind)) return false;
		return !d.isArmor() || old.index() / PromptCraft.ARMOR_PER_PIECE == d.armorPiece();
	}

	public static Item itemFor(ModDef d) {
		if (d.isBlock()) return PromptCraft.SLOT_BLOCKS[d.index()].asItem();
		if (d.isArmor()) return PromptCraft.SLOT_ARMOR[d.index()];
		if (d.isMob()) return PromptCraft.SLOT_EGGS[d.index()];
		return PromptCraft.SLOT_ITEMS[d.index()];
	}

	public static synchronized void put(ModDef d) {
		Map<String, ModDef> next = new HashMap<>(bySlot);
		next.put(d.slot, d.normalize());
		bySlot = next;
	}

	public static synchronized void remove(String slot) {
		Map<String, ModDef> next = new HashMap<>(bySlot);
		next.remove(slot);
		bySlot = next;
	}

	public static void clear() {
		bySlot = Map.of();
	}

	public static String toJson() {
		return GSON.toJson(all());
	}

	public static synchronized void replaceAll(String json) {
		List<ModDef> list = GSON.fromJson(json, new TypeToken<List<ModDef>>() {}.getType());
		Map<String, ModDef> next = new HashMap<>();
		if (list != null) for (ModDef d : list) if (d.slot != null) next.put(d.slot, d.normalize());
		bySlot = next;
	}

	static void load(MinecraftServer server) {
		file = server.getWorldPath(LevelResource.ROOT).resolve("promptcraft_creations.json");
		clear();
		try {
			if (Files.exists(file)) replaceAll(Files.readString(file));
		} catch (Exception e) {
			PromptCraft.LOG.error("Could not read {}", file, e);
		}
		// Data packs load before this event, so existing creations need one reload for their recipes and loot.
		if (!bySlot.isEmpty()) reloadData(server);
	}

	/** Saves to disk, pushes the new definitions to every client, then reloads recipes and loot tables. */
	public static CompletableFuture<Void> saveAndSync(MinecraftServer server) {
		String json = toJson();
		try {
			if (file != null) Files.writeString(file, json);
		} catch (Exception e) {
			PromptCraft.LOG.error("Could not save {}", file, e);
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(p, new Payloads.Sync(json));
		return reloadData(server);
	}

	private static CompletableFuture<Void> reloadData(MinecraftServer server) {
		return server.reloadResources(server.getPackRepository().getSelectedIds());
	}
}
