package dev.promptcraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Runs the local Claude Code CLI in print mode, so requests use the player's Claude subscription
 * instead of an API key. Claude answers with structured output that mirrors the three tools, which
 * is then turned back into Messages-API-style content blocks.
 */
public final class ClaudeCodeBackend {
	private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");
	private static final String EXTRA = """

			OUTPUT FORMAT
			You have no game tools here (any web or file tools are only for research and viewing previews). Answer with the structured output: "message" is your one sentence,
			"create_mod" is a list of create_mod inputs, "run_commands" is a list of commands, and "delete_mod"
			is a list of creation names. Leave lists empty when unused.
			""";

	private ClaudeCodeBackend() {}

	public static CompletableFuture<JsonArray> ask(String system, String toolsJson, String userMessage, List<Path> images, boolean search) {
		return ask(ClaudeConfig.INSTANCE.claudePath(), ClaudeConfig.INSTANCE.model, system, toolsJson, userMessage, images, search);
	}

	/**
	 * search lets Claude use web search for reference; images are review renders it opens with Read.
	 * Only those tools are enabled, and only for this run.
	 */
	public static CompletableFuture<JsonArray> ask(String claudePath, String model, String system, String toolsJson,
			String userMessage, List<Path> images, boolean search) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				// A fresh empty folder keeps Claude Code from reading the game folder or its CLAUDE.md files.
				Path dir = Files.createTempDirectory("promptcraft");
				Path systemFile = dir.resolve("system.txt");
				Files.writeString(systemFile, system + EXTRA);
				String message = userMessage;
				if (!images.isEmpty()) {
					StringBuilder sb = new StringBuilder(userMessage).append("\n\nPreview images (open each with the Read tool):");
					for (Path image : images) sb.append("\n").append(image.toAbsolutePath());
					message = sb.toString();
				}
				String tools = search ? "WebSearch,WebFetch" : images.isEmpty() ? "" : "Read";

				List<String> cmd = new java.util.ArrayList<>(List.of(claudePath, "-p",
						"--output-format", "json",
						"--model", model,
						"--tools", tools.isEmpty() && WINDOWS ? "\"\"" : tools));
				if (!tools.isEmpty()) cmd.addAll(List.of("--allowedTools", tools));
				cmd.addAll(List.of(
						"--strict-mcp-config",
						"--no-session-persistence",
						"--system-prompt-file", systemFile.toString(),
						"--json-schema", escapeForWindows(schema(toolsJson).toString())));
				ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
				Process process = pb.start();
				try (OutputStream in = process.getOutputStream()) {
					in.write(message.getBytes(StandardCharsets.UTF_8));
				}
				String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				if (!process.waitFor(5, TimeUnit.MINUTES)) {
					process.destroyForcibly();
					throw new RuntimeException("timed out");
				}
				return toContent(out);
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
			}
		});
	}

	/** One object with a list per tool, built from the tools' own input schemas. */
	static JsonObject schema(String toolsJson) {
		JsonObject props = new JsonObject();
		JsonObject message = new JsonObject();
		message.addProperty("type", "string");
		props.add("message", message);
		for (JsonElement el : JsonParser.parseString(toolsJson).getAsJsonArray()) {
			JsonObject tool = el.getAsJsonObject();
			String name = tool.get("name").getAsString();
			JsonObject item = switch (name) {
				case "create_mod" -> tool.getAsJsonObject("input_schema");
				default -> stringType();
			};
			JsonObject list = new JsonObject();
			list.addProperty("type", "array");
			list.add("items", item);
			props.add(name, list);
		}
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		schema.add("properties", props);
		JsonArray required = new JsonArray();
		required.add("message");
		schema.add("required", required);
		stripDescriptions(schema);
		return schema;
	}

	private static JsonObject stringType() {
		JsonObject o = new JsonObject();
		o.addProperty("type", "string");
		return o;
	}

	/** Descriptions are the only part with spaces; dropping them keeps the command-line argument simple. */
	private static void stripDescriptions(JsonElement el) {
		if (el.isJsonObject()) {
			el.getAsJsonObject().remove("description");
			for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) stripDescriptions(e.getValue());
		} else if (el.isJsonArray()) {
			for (JsonElement child : el.getAsJsonArray()) stripDescriptions(child);
		}
	}

	/** Java doesn't escape inner quotes when it builds a Windows command line, so do it here. */
	private static String escapeForWindows(String arg) {
		return WINDOWS ? arg.replace("\"", "\\\"") : arg;
	}

	private static JsonArray toContent(String out) {
		JsonObject result;
		try {
			result = JsonParser.parseString(out.substring(out.indexOf('{'))).getAsJsonObject();
		} catch (Exception e) {
			throw new RuntimeException(out.isBlank() ? "no output (is Claude Code installed and logged in?)" : out.strip());
		}
		if (result.has("is_error") && result.get("is_error").getAsBoolean() || !result.has("structured_output")) {
			String why = result.has("result") ? result.get("result").getAsString() : result.toString();
			throw new RuntimeException(why);
		}
		JsonObject so = result.getAsJsonObject("structured_output");
		JsonArray content = new JsonArray();
		if (so.has("message")) content.add(block("text", "text", so.get("message")));
		if (so.has("create_mod")) {
			for (JsonElement mod : so.getAsJsonArray("create_mod")) content.add(tool("create_mod", mod.getAsJsonObject()));
		}
		if (so.has("run_commands") && !so.getAsJsonArray("run_commands").isEmpty()) {
			JsonObject input = new JsonObject();
			input.add("commands", so.getAsJsonArray("run_commands"));
			content.add(tool("run_commands", input));
		}
		if (so.has("delete_mod")) {
			for (JsonElement name : so.getAsJsonArray("delete_mod")) {
				JsonObject input = new JsonObject();
				input.add("name", name);
				content.add(tool("delete_mod", input));
			}
		}
		return content;
	}

	private static JsonObject block(String type, String key, JsonElement value) {
		JsonObject o = new JsonObject();
		o.addProperty("type", type);
		o.add(key, value);
		return o;
	}

	private static JsonObject tool(String name, JsonObject input) {
		JsonObject o = block("tool_use", "input", input);
		o.addProperty("name", name);
		return o;
	}
}
