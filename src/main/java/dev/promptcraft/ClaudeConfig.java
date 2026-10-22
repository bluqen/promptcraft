package dev.promptcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** config/promptcraft.json, created with defaults on first launch. */
public class ClaudeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static ClaudeConfig INSTANCE = new ClaudeConfig();

	/** "auto" uses api_key when set, otherwise the local Claude Code login. Or force "api" / "claude_code". */
	public String backend = "auto";
	public String api_key = "";
	/** Path to the claude executable; blank finds it automatically. */
	public String claude_path = "";
	public String model = "claude-sonnet-5-5";
	public int max_tokens = 16000;
	public int max_commands = 3000;
	/** Off by default: only the singleplayer host and operators may use the computer. */
	public boolean allow_everyone = false;

	/** The config key, or the ANTHROPIC_API_KEY environment variable when the config is blank. */
	public String apiKey() {
		if (api_key != null && !api_key.isBlank()) return api_key.strip();
		String env = System.getenv("ANTHROPIC_API_KEY");
		return env == null ? "" : env.strip();
	}

	public boolean useClaudeCode() {
		if ("claude_code".equals(backend)) return true;
		if ("api".equals(backend)) return false;
		return apiKey().isEmpty();
	}

	public String claudePath() {
		if (claude_path != null && !claude_path.isBlank()) return claude_path.strip();
		Path local = Path.of(System.getProperty("user.home"), ".local", "bin", "claude.exe");
		return Files.exists(local) ? local.toString() : "claude";
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("promptcraft.json");
	}

	static void load() {
		try {
			if (Files.exists(path())) {
				ClaudeConfig read = GSON.fromJson(Files.readString(path()), ClaudeConfig.class);
				if (read != null) INSTANCE = read;
			}
		} catch (Exception e) {
			PromptCraft.LOG.error("Could not read {}", path(), e);
		}
		save();
	}

	static void save() {
		try {
			Files.writeString(path(), GSON.toJson(INSTANCE));
		} catch (Exception e) {
			PromptCraft.LOG.error("Could not write {}", path(), e);
		}
	}
}
