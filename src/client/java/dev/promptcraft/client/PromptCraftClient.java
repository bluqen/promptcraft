package dev.promptcraft.client;

import dev.promptcraft.PromptCraft;
import dev.promptcraft.Defs;
import dev.promptcraft.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;

public class PromptCraftClient implements ClientModInitializer {
	/** The generated pack starts out empty, so the first world with creations needs a reload too. */
	private static String lastSynced = "[]";

	@Override
	public void onInitializeClient() {
		PromptCraft.openComputer = () -> Minecraft.getInstance().setScreen(new ComputerScreen());
		// Slot blocks may become 3D shapes with see-through pixels.
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), PromptCraft.SLOT_BLOCKS);

		ClientPlayNetworking.registerGlobalReceiver(Payloads.Reply.TYPE, (payload, ctx) -> ComputerScreen.onReply(payload.text()));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Model.TYPE, (payload, ctx) -> ComputerScreen.onModel(payload.model()));

		ClientPlayNetworking.registerGlobalReceiver(Payloads.Sync.TYPE, (payload, ctx) -> {
			Defs.replaceAll(payload.json());
			if (payload.json().equals(lastSynced)) return;
			lastSynced = payload.json();
			// Rebuilds textures and models from the generated pack; takes a couple of seconds.
			ctx.client().reloadResourcePacks();
			LocalPlayer player = ctx.client().player;
			if (player != null) {
				PromptCraft.TAB.buildContents(new CreativeModeTab.ItemDisplayParameters(
						player.connection.enabledFeatures(), true, player.level().registryAccess()));
			}
		});
	}
}
