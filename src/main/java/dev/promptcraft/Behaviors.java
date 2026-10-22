package dev.promptcraft;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Runs creation behaviours and Claude's world-edit commands. */
public final class Behaviors {
	private static final Set<String> BLOCKED = Set.of(
			"op", "deop", "stop", "ban", "ban-ip", "pardon", "pardon-ip", "kick", "whitelist", "reload",
			"save-all", "save-off", "save-on", "debug", "perf", "jfr", "publish", "transfer", "function", "datapack");
	private static final int COMMANDS_PER_TICK = 20;
	private static final Deque<Runnable> QUEUE = new ArrayDeque<>();

	private Behaviors() {}

	/** Rejects server-admin commands, including ones hidden behind "execute ... run". */
	static boolean allowed(String cmd) {
		String[] tokens = cmd.strip().replaceFirst("^/", "").toLowerCase(Locale.ROOT).split("\\s+");
		if (tokens.length == 0 || tokens[0].isEmpty()) return false;
		for (int i = 0; i < tokens.length; i++) {
			if (i == 0 || tokens[i - 1].equals("run")) {
				String head = tokens[i].replaceFirst("^minecraft:", "");
				if (BLOCKED.contains(head)) return false;
			}
		}
		return true;
	}

	private static void exec(String cmd, CommandSourceStack src) {
		if (!allowed(cmd)) {
			PromptCraft.LOG.warn("Blocked command from Claude: {}", cmd);
			return;
		}
		src.getServer().getCommands().performPrefixedCommand(src, cmd);
	}

	private static CommandSourceStack quiet(CommandSourceStack src) {
		return src.withPermission(2).withSuppressedOutput();
	}

	/** Runs commands right away (item and block events). */
	public static void run(List<String> cmds, CommandSourceStack src) {
		if (cmds.isEmpty()) return;
		CommandSourceStack s = quiet(src);
		for (String c : cmds) exec(c, s);
	}

	/** Spreads commands over ticks so builds visibly grow. Returns how many were queued. */
	public static int enqueue(List<String> cmds, CommandSourceStack src) {
		CommandSourceStack s = quiet(src);
		int n = Math.min(cmds.size(), ClaudeConfig.INSTANCE.max_commands);
		for (int i = 0; i < n; i++) {
			String c = cmds.get(i);
			QUEUE.add(() -> exec(c, s));
		}
		return n;
	}

	static void tick(MinecraftServer server) {
		for (int i = 0; i < COMMANDS_PER_TICK && !QUEUE.isEmpty(); i++) {
			try {
				QUEUE.poll().run();
			} catch (Exception e) {
				PromptCraft.LOG.warn("Command failed", e);
			}
		}
	}

	/** Applies the cooldown; returns false while the item is still cooling down. */
	public static boolean ready(Player player, Item item, ModDef d) {
		if (player.getCooldowns().isOnCooldown(item)) return false;
		if (d.nativeProps.cooldown_ticks > 0) player.getCooldowns().addCooldown(item, d.nativeProps.cooldown_ticks);
		return true;
	}

	public static void launch(Entity entity, ModDef d) {
		if (d.nativeProps.launch <= 0) return;
		Vec3 v = entity.getDeltaMovement();
		entity.setDeltaMovement(v.x, d.nativeProps.launch, v.z);
		entity.hurtMarked = true;
		entity.resetFallDistance();
	}

	public static void shoot(Player player, ModDef d) {
		String id = d.nativeProps.projectile;
		if (id == null || id.isBlank()) return;
		EntityType.byString(id.contains(":") ? id : "minecraft:" + id).ifPresent(type -> {
			Entity e = type.create(player.level());
			if (!(e instanceof Projectile p)) {
				if (e != null) e.discard();
				return;
			}
			p.setOwner(player);
			p.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
			p.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, d.nativeProps.projectile_speed, 1f);
			if (p instanceof AbstractArrow arrow) arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
			player.level().addFreshEntity(p);
		});
	}
}
