<img width="1024" height="500" alt="cm_teamlifebind" src="https://github.com/user-attachments/assets/13d8c04d-9582-46cb-9576-8a81c67ee8f2" />

# TeamLifeBind

Minecraft 1.21.11 team game mode with shared lives and multi-team support.

## Rules

- Supports 2-32 teams (`team-count`).
- One player dies -> all players in the same team die together (team wipe).
- Team members respawn at team bed first, fallback to team spawn.
- Teams spawn at random team positions with inter-team distance control.
- PvP is enabled.
- Win condition: first team that kills the Ender Dragon wins.
- No-respawn lock (default enabled): dying in configured dimensions (default `minecraft:the_end`) forces spectator elimination.
- Team Bed recipe: 6 wool + 3 planks (planks in bottom row).
- Normal beds are disabled: they do not set personal spawn and explode on placement.
- Lobby + ready flow: by default players wait in lobby, use `/tlb ready`, then all-ready countdown auto-starts the match.
- Match reset:
  - Paper: creates a new match world each round and deletes previous round world after game end.
  - Fabric/Forge/NeoForge: uses dedicated dimension `teamlifebind:match_arena`; each round selects a new center area in that dimension and returns players to lobby when ended.

## Modules

- `common`: shared game engine (`TeamLifeBindEngine`).
- `paper`: Paper plugin implementation.
- `fabric`: Fabric mod implementation.
- `forge`: Forge mod implementation.
- `neoforge`: NeoForge mod implementation.

## Commands

All platforms:

- `/tlb help`
- `/tlb ready`
- `/tlb unready`
- `/tlb start`
- `/tlb stop`
- `/tlb status`
- `/tlb teams <2-32>`
- `/tlb health <ONE_HEART|HALF_ROW|ONE_ROW>`
- `/tlb norespawn [on|off|add|remove|clear] [dimension]`

Paper extra admin commands:

- `/tlb setspawn <teamId>`
- `/tlb clearspawns`
- `/tlb reload`

## Build

```bash
./gradlew :paper:build
```

## Local Test Run

Prepare and run local Paper server:

```bash
./gradlew runPaperServer
```

Run platform clients/servers:

```bash
./gradlew runFabricClient
./gradlew runFabricServer
./gradlew runForgeClient
./gradlew runForgeServer
./gradlew runNeoForgeClient
./gradlew runNeoForgeServer
```

Notes:
- `runPaperServer` prepares `run/paper-server`, downloads the Paper 1.21.11 jar automatically, accepts EULA for local testing, and copies plugin jar.
- `run*Client` tasks are configured to quick-connect to `127.0.0.1:25565`.
