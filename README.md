# PromptCraft

A Minecraft mod with an in-game computer that connects to Claude. Type what you want, like "a sword that shoots fireballs" or "an infinity gauntlet", and it shows up in your inventory seconds later. It gets a name, pixel-art texture or 3D model, stats, abilities and a crafting recipe, with no restart.

**Minecraft 1.21.1 · Fabric**

## What it can make
- **Items**: weapons, tools, food and gadgets with abilities like projectiles, launching and cooldowns, plus anything vanilla commands can do.
- **Blocks**: glowing, bouncy, or reacting when you step on, right-click or break them.
- **Armor**: helmets, chestplates, leggings and boots that show when worn and can give effects.
- **3D models** for held items and blocks.
- **Builds**: "build a castle in front of me" places it block by block.

Textures are recolored from vanilla art when the shape fits. Otherwise Claude draws them, renders a preview, and fixes its own mistakes before you get the item.

## Install
1. Install [Fabric Loader](https://fabricmc.net/use/) for 1.21.1 and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Put `promptcraft-1.0.0.jar` in your `mods` folder.
3. Connect Claude. Either option works:
   - **Claude Code** (uses your Claude subscription): install [Claude Code](https://claude.com/claude-code) and log in. The mod finds it automatically.
   - **API key**: put your Anthropic API key in `config/promptcraft.json` under `api_key`.
4. In a creative world, find the **AI Computer** in the PromptCraft tab, place it and right-click it.

## Config (`config/promptcraft.json`)
| Key | Default | What it does |
| --- | --- | --- |
| `backend` | `auto` | `auto` uses the API key if one is set, otherwise Claude Code. You can force `api` or `claude_code`. |
| `api_key` | `""` | Anthropic API key. The `ANTHROPIC_API_KEY` environment variable also works. |
| `claude_path` | `""` | Path to the `claude` executable. Leave it blank to detect it automatically. |
| `model` | `claude-sonnet-5-5` | The model to use. |
| `max_commands` | `3000` | Cap on commands per build. |
| `allow_everyone` | `false` | By default, only the singleplayer host and operators can use the computer. |

## Safety
Commands run at permission level 2. Server-admin commands like `op`, `stop`, `ban` and `whitelist` are blocked, including ones hidden inside `execute ... run`.

## Building
Gradle needs JDK 25+ to run. The mod itself targets Java 21.

```
./gradlew build
```

The jar lands in `build/libs/`.

Not affiliated with Anthropic or Mojang.
