package dev.promptcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PromptCraft implements ModInitializer {
	public static final String MOD_ID = "promptcraft";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	// Minecraft freezes its registries at startup, so empty slots are registered now and filled by Claude later.
	public static final int ITEM_SLOTS = 64;
	public static final int BLOCK_SLOTS = 32;
	public static final SlotItem[] SLOT_ITEMS = new SlotItem[ITEM_SLOTS];
	public static final SlotBlock[] SLOT_BLOCKS = new SlotBlock[BLOCK_SLOTS];
	public static final int ARMOR_PER_PIECE = 16;
	public static final ArmorItem.Type[] ARMOR_TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
	public static final SlotArmorItem[] SLOT_ARMOR = new SlotArmorItem[ARMOR_PER_PIECE * 4];
	public static final int MOB_SLOTS = 32;
	public static final MobEggItem[] SLOT_EGGS = new MobEggItem[MOB_SLOTS];

	public static Block COMPUTER;
	public static CreativeModeTab TAB;

	/** Set by the client entrypoint; the computer block calls it to open its screen. */
	public static Runnable openComputer = () -> {};

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ClaudeConfig.load();

		COMPUTER = Registry.register(BuiltInRegistries.BLOCK, id("computer"),
				new ComputerBlock(BlockBehaviour.Properties.of().strength(2f).sound(SoundType.METAL).lightLevel(s -> 7)));
		Registry.register(BuiltInRegistries.ITEM, id("computer"), new BlockItem(COMPUTER, new Item.Properties()));

		for (int i = 0; i < ITEM_SLOTS; i++) {
			SLOT_ITEMS[i] = Registry.register(BuiltInRegistries.ITEM, id("slot_item_" + i), new SlotItem("item_" + i, new Item.Properties()));
		}
		for (int i = 0; i < BLOCK_SLOTS; i++) {
			String slot = "block_" + i;
			SLOT_BLOCKS[i] = Registry.register(BuiltInRegistries.BLOCK, id("slot_block_" + i),
					new SlotBlock(slot, BlockBehaviour.Properties.of().strength(1f).noOcclusion().lightLevel(s -> s.getValue(SlotBlock.LIGHT))));
			Registry.register(BuiltInRegistries.ITEM, id("slot_block_" + i), new SlotBlockItem(slot, SLOT_BLOCKS[i], new Item.Properties()));
		}

		Map<ArmorItem.Type, Integer> noDefense = new EnumMap<>(ArmorItem.Type.class);
		for (ArmorItem.Type type : ArmorItem.Type.values()) noDefense.put(type, 0);
		for (int i = 0; i < SLOT_ARMOR.length; i++) {
			// Defense comes from the creation's attribute modifiers; the material only names the worn texture.
			Holder<ArmorMaterial> material = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, id("slot_armor_" + i),
					new ArmorMaterial(noDefense, 15, SoundEvents.ARMOR_EQUIP_DIAMOND, () -> Ingredient.EMPTY,
							List.of(new ArmorMaterial.Layer(id("slot_armor_" + i))), 0f, 0f));
			SLOT_ARMOR[i] = Registry.register(BuiltInRegistries.ITEM, id("slot_armor_" + i),
					new SlotArmorItem("armor_" + i, material, ARMOR_TYPES[i / ARMOR_PER_PIECE], new Item.Properties().stacksTo(1)));
		}

		for (int i = 0; i < MOB_SLOTS; i++) {
			SLOT_EGGS[i] = Registry.register(BuiltInRegistries.ITEM, id("slot_mob_" + i), new MobEggItem("mob_" + i, new Item.Properties()));
		}

		TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"), FabricItemGroup.builder()
				.title(Component.translatable("itemGroup.promptcraft"))
				.icon(() -> new ItemStack(COMPUTER))
				.displayItems((params, output) -> {
					output.accept(COMPUTER);
					for (ModDef def : Defs.all()) output.accept(Defs.itemFor(def));
				})
				.build());

		PayloadTypeRegistry.playC2S().register(Payloads.Prompt.TYPE, Payloads.Prompt.CODEC);
		PayloadTypeRegistry.playS2C().register(Payloads.Reply.TYPE, Payloads.Reply.CODEC);
		PayloadTypeRegistry.playS2C().register(Payloads.Sync.TYPE, Payloads.Sync.CODEC);
		PayloadTypeRegistry.playC2S().register(Payloads.SetModel.TYPE, Payloads.SetModel.CODEC);
		PayloadTypeRegistry.playS2C().register(Payloads.Model.TYPE, Payloads.Model.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Prompt.TYPE, (payload, ctx) -> ClaudeApi.handlePrompt(ctx.player(), payload.prompt()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SetModel.TYPE, (payload, ctx) -> ClaudeApi.setModel(ctx.player(), payload.model()));

		ServerLifecycleEvents.SERVER_STARTED.register(Defs::load);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Defs.clear();
			WorldEvents.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			sender.sendPacket(new Payloads.Sync(Defs.toJson()));
			sender.sendPacket(new Payloads.Model(ClaudeConfig.INSTANCE.model));
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Behaviors.tick(server);
			WorldEvents.tick(server);
			CustomMobs.tick(server);
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> CustomMobs.configure(entity));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> CustomMobs.onDeath(entity));
	}
}
