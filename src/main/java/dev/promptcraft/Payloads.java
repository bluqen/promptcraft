package dev.promptcraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class Payloads {
	private Payloads() {}

	/** Client to server: what the player typed into the computer. */
	public record Prompt(String prompt) implements CustomPacketPayload {
		public static final Type<Prompt> TYPE = new Type<>(PromptCraft.id("prompt"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Prompt> CODEC =
				StreamCodec.composite(ByteBufCodecs.stringUtf8(2000), Prompt::prompt, Prompt::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server to client: Claude's answer, shown on the computer screen. */
	public record Reply(String text) implements CustomPacketPayload {
		public static final Type<Reply> TYPE = new Type<>(PromptCraft.id("reply"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC =
				StreamCodec.composite(ByteBufCodecs.stringUtf8(8000), Reply::text, Reply::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client to server: switch the model the computer uses. */
	public record SetModel(String model) implements CustomPacketPayload {
		public static final Type<SetModel> TYPE = new Type<>(PromptCraft.id("set_model"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SetModel> CODEC =
				StreamCodec.composite(ByteBufCodecs.stringUtf8(100), SetModel::model, SetModel::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server to client: the model currently in use. */
	public record Model(String model) implements CustomPacketPayload {
		public static final Type<Model> TYPE = new Type<>(PromptCraft.id("model"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Model> CODEC =
				StreamCodec.composite(ByteBufCodecs.stringUtf8(100), Model::model, Model::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server to client: every creation in the world, as JSON. */
	public record Sync(String json) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(PromptCraft.id("sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC =
				StreamCodec.composite(ByteBufCodecs.stringUtf8(1_000_000), Sync::json, Sync::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
