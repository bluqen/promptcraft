package dev.promptcraft;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** A spawn egg for a custom mob slot. */
public class MobEggItem extends Item {
	private final String slot;
	private ModDef cachedDef;
	private DataComponentMap cachedComponents;

	public MobEggItem(String slot, Properties properties) {
		super(properties);
		this.slot = slot;
	}

	@Override
	public DataComponentMap components() {
		ModDef d = Defs.get(slot);
		if (d == null) return super.components();
		if (d != cachedDef) {
			cachedComponents = DataComponentMap.builder()
					.addAll(SlotItem.withDef(super.components(), d))
					.set(DataComponents.ITEM_NAME, Component.literal(d.name + " Spawn Egg"))
					.build();
			cachedDef = d;
		}
		return cachedComponents;
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		ModDef d = Defs.get(slot);
		if (d == null) return InteractionResult.PASS;
		if (ctx.getLevel() instanceof ServerLevel level) {
			CustomMobs.spawn(level, d, Vec3.atBottomCenterOf(ctx.getClickedPos().relative(ctx.getClickedFace())));
			if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
		}
		return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
	}
}
