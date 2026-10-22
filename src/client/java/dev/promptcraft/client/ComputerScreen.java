package dev.promptcraft.client;

import dev.promptcraft.ClaudeApi;
import dev.promptcraft.Payloads;
import net.minecraft.client.gui.components.Button;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** A retro terminal: type a request, watch Claude reply. History survives closing the screen. */
public class ComputerScreen extends Screen {
	private static final int ORANGE = 0xFFD97757;
	private static final int PANEL = 0xF0101010;
	private static final List<Line> HISTORY = new ArrayList<>();
	private static boolean waiting;

	private record Line(String text, int color) {}

	private static String model = ClaudeApi.MODELS.get(0);

	private EditBox input;
	private Button modelButton;

	static void onModel(String m) {
		model = m;
		if (Minecraft.getInstance().screen instanceof ComputerScreen screen && screen.modelButton != null) {
			screen.modelButton.setMessage(modelLabel());
		}
	}

	/** "claude-opus-5-5" becomes "Model: Opus 5.5". */
	private static Component modelLabel() {
		String[] parts = model.replace("claude-", "").split("-");
		String name = parts[0].substring(0, 1).toUpperCase() + parts[0].substring(1);
		String version = parts.length > 2 ? parts[1] + "." + parts[2] : "";
		return Component.literal("Model: " + name + " " + version);
	}

	public ComputerScreen() {
		super(Component.literal("AI Computer"));
		if (HISTORY.isEmpty()) add("Claude is online. Ask me to make anything: a sword, a block, a food...", 0xFF9A9A9A);
	}

	static void onReply(String text) {
		waiting = false;
		add("Claude: " + text, ORANGE);
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof ComputerScreen)) {
			mc.gui.getChat().addMessage(Component.literal("[Claude] ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
		}
	}

	private static void add(String text, int color) {
		HISTORY.add(new Line(text, color));
		if (HISTORY.size() > 100) HISTORY.remove(0);
	}

	private int panelWidth() {
		return Math.min(width - 40, 440);
	}

	@Override
	protected void init() {
		int w = panelWidth();
		int x = (width - w) / 2;
		input = new EditBox(font, x + 8, height - 42, w - 16, 18, Component.literal("Prompt"));
		input.setMaxLength(1000);
		input.setHint(Component.literal("Make a sword that shoots fireballs...").withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(input);
		modelButton = Button.builder(modelLabel(), b -> {
			List<String> models = ClaudeApi.MODELS;
			String next = models.get((models.indexOf(model) + 1) % models.size());
			ClientPlayNetworking.send(new Payloads.SetModel(next));
		}).bounds(x + w - 118, 23, 110, 14).build();
		addRenderableWidget(modelButton);
		setInitialFocus(input);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && !waiting && !input.getValue().isBlank()) {
			String prompt = input.getValue().strip();
			input.setValue("");
			add("> " + prompt, 0xFFFFFFFF);
			waiting = true;
			ClientPlayNetworking.send(new Payloads.Prompt(prompt));
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		int w = panelWidth();
		int x0 = (width - w) / 2;
		g.fill(x0 - 2, 18, x0 + w + 2, height - 12, ORANGE);
		g.fill(x0, 20, x0 + w, height - 14, PANEL);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int w = panelWidth();
		int x0 = (width - w) / 2;
		g.drawString(font, "* AI COMPUTER", x0 + 8, 26, ORANGE);

		// Wrap history and draw it bottom-up so the newest lines sit just above the input box.
		List<FormattedCharSequence> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		for (Line line : HISTORY) {
			for (FormattedCharSequence seq : font.split(Component.literal(line.text()), w - 16)) {
				lines.add(seq);
				colors.add(line.color());
			}
		}
		if (waiting) {
			lines.add(Component.literal("Claude is coding your mod" + ".".repeat((int) (Util.getMillis() / 400 % 4))).getVisualOrderText());
			colors.add(0xFF9A9A9A);
		}
		int y = height - 56;
		for (int i = lines.size() - 1; i >= 0 && y > 40; i--) {
			g.drawString(font, lines.get(i), x0 + 8, y, colors.get(i));
			y -= 10;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
