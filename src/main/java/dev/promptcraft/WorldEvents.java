package dev.promptcraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Temporary world effects Claude starts: repeating commands and things raining from the sky around players. */
public final class WorldEvents {
	private record Active(ModDef def, long endTick, float[] rainBudget) {}

	private static final List<Active> ACTIVE = new ArrayList<>();
	private static final int MAX_SECONDS = 600;

	private WorldEvents() {}

	public static void start(MinecraftServer server, ModDef def) {
		stop(def.name);
		int seconds = Math.max(1, Math.min(MAX_SECONDS, def.event.duration_seconds));
		ACTIVE.add(new Active(def, server.overworld().getGameTime() + seconds * 20L, new float[1]));
	}

	public static boolean stop(String name) {
		return ACTIVE.removeIf(a -> a.def.name.equalsIgnoreCase(name.strip()));
	}

	public static List<String> activeNames() {
		return ACTIVE.stream().map(a -> a.def.name).toList();
	}

	public static void clear() {
		ACTIVE.clear();
	}

	static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) return;
		long now = server.overworld().getGameTime();
		ACTIVE.removeIf(a -> now >= a.endTick);
		for (Active a : List.copyOf(ACTIVE)) {
			ModDef.EventSpec e = a.def.event;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!e.commands.isEmpty() && now % Math.max(1, e.every_ticks) == 0) {
					Behaviors.run(e.commands, player.createCommandSourceStack());
				}
			}
			if (e.rain != null && e.rain.what != null) {
				// per_second is spread across ticks; the fraction carries over between ticks.
				a.rainBudget[0] += Math.min(40, e.rain.per_second) / 20f;
				while (a.rainBudget[0] >= 1) {
					a.rainBudget[0] -= 1;
					for (ServerPlayer player : server.getPlayerList().getPlayers()) drop(player, e.rain);
				}
			}
		}
	}

	private static void drop(ServerPlayer player, ModDef.Rain rain) {
		ServerLevel level = player.serverLevel();
		int r = Math.max(1, Math.min(48, rain.radius));
		BlockPos pos = BlockPos.containing(
				player.getX() + level.random.nextInt(r * 2 + 1) - r,
				Math.min(level.getMaxBuildHeight() - 1, player.getY() + Math.max(4, rain.height)),
				player.getZ() + level.random.nextInt(r * 2 + 1) - r);
		if (!level.isEmptyBlock(pos)) return;

		BlockState block = blockFor(rain.what);
		if (block != null) {
			FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, block);
			falling.dropItem = false;
			if (rain.damage > 0) falling.setHurtsEntities(rain.damage, (int) Math.max(rain.damage, 20));
			return;
		}
		Optional<EntityType<?>> type = EntityType.byString(id(rain.what));
		if (type.isEmpty()) return;
		Entity entity = type.get().create(level);
		if (entity == null) return;
		entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		level.addFreshEntity(entity);
	}

	/** A creation's block, or a vanilla block id; null if it names an entity instead. */
	private static BlockState blockFor(String what) {
		ModDef d = Defs.byName(what);
		if (d != null && d.isBlock()) return PromptCraft.SLOT_BLOCKS[d.index()].placedState();
		ResourceLocation id = ResourceLocation.tryParse(id(what));
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return null;
		Block block = BuiltInRegistries.BLOCK.get(id);
		return block == Blocks.AIR ? null : block.defaultBlockState();
	}

	private static String id(String what) {
		String s = what.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
		return s.contains(":") ? s : "minecraft:" + s;
	}
}
