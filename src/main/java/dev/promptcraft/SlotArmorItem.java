package dev.promptcraft;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/** A pre-registered armor piece; its own armor material points the worn texture at the generated pack. */
public class SlotArmorItem extends ArmorItem {
	private final String slot;
	private ModDef cachedDef;
	private DataComponentMap cachedComponents;

	public SlotArmorItem(String slot, Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
		this.slot = slot;
	}

	public ModDef def() {
		return Defs.get(slot);
	}

	@Override
	public DataComponentMap components() {
		ModDef d = def();
		if (d == null) return super.components();
		if (d != cachedDef) {
			EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(getEquipmentSlot());
			String piece = getType().getName();
			cachedComponents = DataComponentMap.builder()
					.addAll(SlotItem.withDef(super.components(), d))
					.set(DataComponents.MAX_STACK_SIZE, 1)
					.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
							.add(Attributes.ARMOR, new AttributeModifier(PromptCraft.id("armor." + piece), d.props.armor, AttributeModifier.Operation.ADD_VALUE), group)
							.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(PromptCraft.id("toughness." + piece), d.props.toughness, AttributeModifier.Operation.ADD_VALUE), group)
							.build())
					.build();
			cachedDef = d;
		}
		return cachedComponents;
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
		ModDef d = def();
		if (d == null || level.isClientSide || level.getGameTime() % 10 != 0) return;
		if (entity instanceof LivingEntity living && living.getItemBySlot(getEquipmentSlot()) == stack) {
			Behaviors.run(d.on("on_tick_worn"), entity.createCommandSourceStack());
		}
	}
}
