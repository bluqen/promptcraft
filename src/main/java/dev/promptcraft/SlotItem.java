package dev.promptcraft;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/** A pre-registered item whose name, stats and abilities come from the creation stored in its slot. */
public class SlotItem extends Item {
	private final String slot;
	private ModDef cachedDef;
	private DataComponentMap cachedComponents;

	public SlotItem(String slot, Properties properties) {
		super(properties);
		this.slot = slot;
	}

	public ModDef def() {
		return Defs.get(slot);
	}

	/** New stacks copy this prototype, so overriding it gives them the creation's name and stats. */
	@Override
	public DataComponentMap components() {
		ModDef d = def();
		if (d == null) return super.components();
		if (d != cachedDef) {
			cachedComponents = withDef(super.components(), d);
			cachedDef = d;
		}
		return cachedComponents;
	}

	static DataComponentMap withDef(DataComponentMap base, ModDef d) {
		ModDef.Props p = d.props;
		DataComponentMap.Builder b = DataComponentMap.builder().addAll(base);
		b.set(DataComponents.ITEM_NAME, Component.literal(d.name));
		if (p.durability > 0) {
			b.set(DataComponents.MAX_STACK_SIZE, 1);
			b.set(DataComponents.MAX_DAMAGE, p.durability);
			b.set(DataComponents.DAMAGE, 0);
		} else {
			b.set(DataComponents.MAX_STACK_SIZE, Mth.clamp(p.max_stack, 1, 99));
		}
		if (p.attack_damage > 0) {
			b.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
					.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, p.attack_damage - 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.build());
		}
		if (p.food > 0) {
			b.set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(p.food).saturationModifier(0.6f).alwaysEdible().build());
		}
		return b.build();
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		ModDef d = def();
		if (d == null || d.props.food > 0) return super.use(level, player, hand);
		if (d.on("on_use").isEmpty() && d.nativeProps.launch == 0 && d.nativeProps.projectile == null) return InteractionResultHolder.pass(stack);
		if (!level.isClientSide && Behaviors.ready(player, this, d)) {
			Behaviors.launch(player, d);
			Behaviors.shoot(player, d);
			Behaviors.run(d.on("on_use"), player.createCommandSourceStack());
			if (d.props.durability > 0) stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		ModDef d = def();
		if (d == null) return false;
		if (!attacker.level().isClientSide) {
			// on_hit runs as the victim, so @s and ~ ~ ~ point at whatever got hit.
			Behaviors.run(d.on("on_hit"), target.createCommandSourceStack());
			if (d.props.durability > 0) stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
		}
		return true;
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
		ModDef d = def();
		if (d == null || !selected || level.isClientSide || level.getGameTime() % 10 != 0) return;
		Behaviors.run(d.on("on_tick_held"), entity.createCommandSourceStack());
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ModDef d = def();
		if (d != null && !level.isClientSide) Behaviors.run(d.on("on_eat"), entity.createCommandSourceStack());
		return super.finishUsingItem(stack, level, entity);
	}
}
