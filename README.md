# Blaze's Chaos

Production-ready Paper/Purpur 1.21+ survival chaos minigame.

## Requirements

- Java 21
- Paper or Purpur 1.21+
- Optional: PlaceholderAPI, Vault

## Build

```bash
mvn clean package
```

JAR output: `target/BlazesChaos-1.0.0.jar`

## Commands

Aliases: `/bc`, `/bchaos`, `/blazechaos`

| Command | Description |
|---------|-------------|
| `/bc join [arena]` | Join a game / open arena menu |
| `/bc leave` | Leave the current game |
| `/bc lobby` | Teleport to lobby |
| `/bc setlobby` | Set global lobby |
| `/bc list` | List arenas |
| `/bc deletearena <name>` | Delete an arena |
| `/bc setup` | Enter hotbar setup mode (chat-create if needed) |
| `/bc forcestart` | Force start |
| `/bc stop` | Stop the game |
| `/bc next` | Force next chaos event |
| `/bc reload` | Reload configs |
| `/bc debug` | Toggle debug |
| `/bc info` | Show game info |
| `/bc version` | Plugin version |

## Placeholders

- `%blazechaos_players%`
- `%blazechaos_alive%`
- `%blazechaos_event%`
- `%blazechaos_next_event%`
- `%blazechaos_time%`
- `%blazechaos_map%`
- `%blazechaos_state%`
- `%blazechaos_wins%`
- `%blazechaos_games%`

## Config Files

`config.yml`, `messages.yml`, `events.yml`, `arenas.yml`, `scoreboardconfig.yml`, `gui.yml`, `worldreset.yml`, `permissions.yml`

## Language

Set `language: en` or `language: ro` in `config.yml`.
Files: `lang/english.yml`, `lang/romanian.yml`.
