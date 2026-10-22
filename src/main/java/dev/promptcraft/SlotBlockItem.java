package dev.promptcraft;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/** The item form of a slot block; takes its name and stack size from the creation. */
public class SlotBlockItem extends BlockItem {
	private final String slot;
	private ModDef cachedDef;
	private DataComponentMap cachedComponents;

	public SlotBlockItem(String slot, Block block, Properties properties) {
		super(block, properties);
		this.slot = slot;
	}

	@Override
	public DataComponentMap components() {
		ModDef d = Defs.get(slot);
		if (d == null) return super.components();
		if (d != cachedDef) {
			cachedComponents = SlotItem.withDef(super.components(), d);
			cachedDef = d;
		}
		return cachedComponents;
	}
}
