package dev.promptcraft;

import dev.promptcraft.mixin.MobAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Custom mobs are vanilla mobs tagged with their creation's slot. Name, stats, glow, gear and effects
 * are applied once at spawn and saved with the entity; goals aren't saved, so they're re-added on every load.
 */
public final class CustomMobs {
	private static final String TAG = "promptcraft_mob:";
	private static final Map<Entity, String> LIVE = new WeakHashMap<>();

	private CustomMobs() {}

	public static void spawnNear(ServerPlayer player, ModDef d) {
		Vec3 ahead = player.getLookAngle().multiply(1, 0, 1).normalize();
		int count = Math.max(1, Math.min(10, d.mob.spawn_count));
		for (int i = 0; i < count; i++) spawn(player.serverLevel(), d, player.position().add(ahead.scale(3 + i * 1.5)));
	}

	public static void spawn(ServerLevel level, ModDef d, Vec3 pos) {
		Optional<EntityType<?>> type = EntityType.byString(id(d.mob.base));
		if (type.isEmpty()) return;
		Entity e = type.get().create(level);
		if (e == null) return;
		e.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360f, 0f);
		if (e instanceof Mob mob) mob.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
		e.addTag(TAG + d.slot);
		applyStats(level, e, d);
		level.addFreshEntity(e);
		configure(e);
	}

	private static void applyStats(ServerLevel level, Entity e, ModDef d) {
		ModDef.MobSpec m = d.mob;
		e.setCustomName(Component.literal(d.name));
		e.setCustomNameVisible(true);
		if (m.glow) {
			e.setGlowingTag(true);
			Scoreboard scoreboard = level.getScoreboard();
			ChatFormatting color = Optional.ofNullable(ChatFormatting.getByName(m.glow_color)).filter(ChatFormatting::isColor).orElse(ChatFormatting.RED);
			String teamName = "promptcraft_" + color.getName();
			PlayerTeam team = scoreboard.getPlayerTeam(teamName);
			if (team == null) team = scoreboard.addPlayerTeam(teamName);
			team.setColor(color);
			scoreboard.addPlayerToTeam(e.getScoreboardName(), team);
		}
		if (!(e instanceof LivingEntity living)) return;
		if (m.health > 0) {
			setBase(living, Attributes.MAX_HEALTH, Math.min(m.health, 1024));
			living.setHealth((float) Math.min(m.health, 1024));
		}
		if (m.damage > 0) setBase(living, Attributes.ATTACK_DAMAGE, Math.min(m.damage, 2048));
		AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && m.speed > 0 && m.speed != 1) speed.setBaseValue(speed.getBaseValue() * Math.min(m.speed, 5));
		if (m.scale > 0 && m.scale != 1) setBase(living, Attributes.SCALE, Math.max(0.1, Math.min(m.scale, 10)));
		for (String effect : m.effects) {
			String[] parts = effect.strip().split("\\s+");
			ResourceLocation rl = ResourceLocation.tryParse(id(parts[0]));
			int amplifier = parts.length > 1 ? parseInt(parts[1]) : 0;
			Optional<Holder.Reference<MobEffect>> holder = rl == null ? Optional.empty() : BuiltInRegistries.MOB_EFFECT.getHolder(rl);
			holder.ifPresent(h -> living.addEffect(new MobEffectInstance(h, MobEffectInstance.INFINITE_DURATION, amplifier, false, false)));
		}
		if (living instanceof Mob mob) {
			m.equipment.forEach((slotName, itemName) -> {
				try {
					EquipmentSlot slot = EquipmentSlot.byName(slotName.toLowerCase(Locale.ROOT));
					Item item = itemFor(itemName);
					if (item == null) return;
					mob.setItemSlot(slot, new ItemStack(item));
					mob.setDropChance(slot, 0f);
				} catch (IllegalArgumentException ignored) {
				}
			});
		}
	}

	/** Re-adds goals; called on spawn and whenever a tagged mob loads from disk. */
	public static void configure(Entity e) {
		ModDef d = defOf(e);
		if (d == null || LIVE.containsKey(e)) return;
		LIVE.put(e, d.slot);
		if (d.mob.attacks.isEmpty() || !(e instanceof PathfinderMob mob)) return;
		MobAccessor goals = (MobAccessor) mob;
		List<String> attacks = d.mob.attacks.stream().map(CustomMobs::id).toList();
		goals.getTargetSelector().removeAllGoals(g -> true);
		goals.getTargetSelector().addGoal(1, new HurtByTargetGoal(mob));
		goals.getTargetSelector().addGoal(2, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 5, true, false,
				target -> !isSameCreation(target, d) && matches(target, attacks)));
		// Mobs without an attack damage attribute (pigs, cows) can't melee; adding the goal would crash them.
		if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) goals.getGoalSelector().addGoal(2, new MeleeAttackGoal(mob, 1.2, false));
	}

	static void tick(MinecraftServer server) {
		if (LIVE.isEmpty() || server.overworld().getGameTime() % 10 != 0) return;
		for (Entity e : new ArrayList<>(LIVE.keySet())) {
			if (e.isRemoved()) {
				LIVE.remove(e);
				continue;
			}
			ModDef d = defOf(e);
			if (d != null) Behaviors.run(d.on("on_mob_tick"), e.createCommandSourceStack());
		}
	}

	static void onDeath(LivingEntity e) {
		ModDef d = defOf(e);
		if (d != null) Behaviors.run(d.on("on_death"), e.createCommandSourceStack());
	}

	private static ModDef defOf(Entity e) {
		for (String tag : e.getTags()) {
			if (tag.startsWith(TAG)) {
				ModDef d = Defs.get(tag.substring(TAG.length()));
				return d != null && d.isMob() ? d : null;
			}
		}
		return null;
	}

	private static boolean isSameCreation(LivingEntity target, ModDef d) {
		return target.getTags().contains(TAG + d.slot);
	}

	private static boolean matches(LivingEntity target, List<String> attacks) {
		if (target instanceof Player p) return attacks.contains("minecraft:player") && !p.isCreative() && !p.isSpectator();
		return attacks.contains(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
	}

	private static void setBase(LivingEntity e, Holder<Attribute> attribute, double value) {
		AttributeInstance inst = e.getAttribute(attribute);
		if (inst != null) inst.setBaseValue(value);
	}

	/** A creation by name ("{Soul Blade}" or "Soul Blade"), or a vanilla item id. */
	static Item itemFor(String name) {
		String n = name.strip().replaceAll("^\\{|\\}$", "");
		ModDef d = Defs.byName(n);
		if (d != null && !d.isMob() && !d.isEvent()) return Defs.itemFor(d);
		ResourceLocation rl = ResourceLocation.tryParse(id(n));
		return rl != null && BuiltInRegistries.ITEM.containsKey(rl) ? BuiltInRegistries.ITEM.get(rl) : null;
	}

	private static int parseInt(String s) {
		try {
			return Math.max(0, Math.min(9, Integer.parseInt(s)));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	static String id(String s) {
		String t = s.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
		return t.contains(":") ? t : "minecraft:" + t;
	}
}
