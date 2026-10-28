# Rising Settlements

[![build](https://github.com/AmmaarBakshi/Rising-Settlements/actions/workflows/build.yml/badge.svg)](https://github.com/AmmaarBakshi/Rising-Settlements/actions/workflows/build.yml)
![Minecraft 26.3](https://img.shields.io/badge/Minecraft-26.3-62B47A)
![Fabric](https://img.shields.io/badge/loader-Fabric-DBD0B4)
![License: MIT](https://img.shields.io/badge/license-MIT-blue)

**Villages that grow up.** Rising Settlements is a Fabric mod for Minecraft Java Edition.
It makes villages change slowly over time instead of staying the same forever.

Villagers slowly:

- 🏠 **build new houses** that match the village's biome
- 🛤️ **extend their roads** to connect new buildings
- 🌾 **plant more farms** next to the ones they already have
- 🔨 **repair damage** after raids, creeper explosions and fires

Help a village enough and you can claim its bell to become its **mayor**. As mayor,
you choose what the village builds next.

> **Status:** early development. The project setup is done, and gameplay features are
> being added phase by phase. See the [roadmap](#roadmap) for what works today.

---

## Contents

- [Features](#features)
- [Commands](#commands)
- [How it works](#how-it-works)
- [Design rules](#design-rules)
- [Installation (players)](#installation-players)
- [Building from source (developers)](#building-from-source-developers)
- [Project structure](#project-structure)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

## Features

| Feature | What it does | Status |
|---|---|---|
| Village tracking | Finds villages by their bell and remembers their borders, villagers, biome style and blocks. Protects every block a player places. | ✅ 0.2.0 |
| Repairs | Villagers rebuild blocks lost to raids, explosions and fire, one block at a time, with particles and sound. | ✅ 0.3.0 |
| Farms | Farmers widen fields and start new ones nearby. | Planned |
| Roads | Paths grow to reach new buildings. | Planned |
| Houses | New biome-styled houses go up a few blocks at a time. | Planned |
| Mayor | Earn reputation, claim the bell, choose the next project. | Planned |
| Config | Growth speed, max village size, each feature on or off. | Planned |

## Commands

| Command | Who | What it does |
|---|---|---|
| `/settlement info` | Everyone | Shows the nearest village (within 256 blocks): name, style, bell, border, villagers, beds and how many blocks are protected. |
| `/settlement list` | Operators | Lists every village the mod knows in the current dimension. |
| `/settlement scan` | Operators | Looks for villages around you right now, instead of waiting for the automatic scan. |
| `/settlement repair` | Operators | Repairs the nearest village's damage right away, skipping the usual one-minute wait. |

## How it works

### Finding villages

Every few seconds the mod looks for village bells in loaded chunks around each player.
When it finds a bell inside a real village structure, it gives the village a name, works
out its biome style and draws a border around it. Then it records every block of the
village (the *blueprint*), a few thousand blocks per tick so the server never stutters.

### Protecting player builds

Whenever a player places a block anywhere near a village, the mod remembers that spot.
Those spots are left alone forever: they are never repaired, replaced or built over. If a
player breaks a village block, the mod treats that as the player's choice and doesn't put
it back.

### Repairs

The mod slowly compares the village with its blueprint. When a village block has been
missing for about a minute (because of a creeper, a raid, fire or anything else that isn't
a player), a nearby villager walks over and puts it back. Masons are picked first. Each
block appears with a puff of particles and its placing sound, one block every two seconds.
Repairs wait until a raid is over.

## Design rules

The mod follows these rules everywhere:

1. **It never touches player builds.** It only changes blocks that were part of the
   original village or that it built itself. Blocks a player places are never broken,
   replaced or built over.
2. **No lag.** Work is spread over many ticks and only happens in loaded chunks. A
   village places at most a few blocks per second.
3. **Slow, believable growth.** Villages grow over many in-game days, not minutes. Every
   speed can be changed in the config.
4. **Multiplayer safe.** All village logic runs on the server. The client only draws
   screens and effects.
5. **Saved with the world.** Village data lives in the world save and survives restarts.
6. **Matches the biome.** New buildings use the village's style (plains, desert,
   savanna, taiga or snowy).
7. **Vanilla blocks only.** The mod uses vanilla blocks such as the bell, so removing it
   does not break your world.

## Installation (players)

### What you need

| | Version |
|---|---|
| Minecraft Java Edition | **26.3** |
| [Fabric Loader](https://fabricmc.net/use/installer/) | **0.20.1** or newer |
| [Fabric API](https://modrinth.com/mod/fabric-api) | **0.165.0+26.3** or newer (any `+26.3` build) |
| Java | 25 (the Minecraft launcher ships its own) |

### Step 1: Install Fabric Loader

**Official Fabric installer (recommended, works with TLauncher and the official launcher)**

1. Download the installer from <https://fabricmc.net/use/installer/> (pick the Windows `.exe`
   or the universal `.jar`).
2. Run it and open the **Client** tab.
3. Set **Minecraft Version** to `26.3` and **Loader Version** to `0.20.1` (or newer).
4. Make sure **Launcher Location** points to your `.minecraft` folder,
   for example `C:\Users\<you>\AppData\Roaming\.minecraft`.
5. Click **Install**.

**TLauncher users:** after installing, open TLauncher and choose
`fabric-loader-0.20.1-26.3` from the version list. If it does not show up, open
TLauncher's settings and enable showing all installed/other versions, then restart TLauncher.

TLauncher also has its own **Fabric 26.3** entries in the version list. They usually work
too, but they may install an older Fabric Loader. This mod needs Fabric Loader 0.20.1 or newer.

### Step 2: Add the mods

Put these two files in your `mods` folder
(on Windows: `%APPDATA%\.minecraft\mods`, create the folder if it does not exist):

- `fabric-api-0.165.0+26.3.jar` (or a newer `+26.3` build), from
  [Modrinth](https://modrinth.com/mod/fabric-api/versions?g=26.3) or
  [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
- `rising-settlements-<version>.jar` from the
  [releases page](https://github.com/AmmaarBakshi/Rising-Settlements/releases), or one you
  [built yourself](#building-from-source-developers). Use the plain jar, **not** the
  `-sources` jar.

### Step 3: Play

Start the Fabric version from your launcher. To check the mod loaded, look for this line
in `.minecraft/logs/latest.log`:

```
[rising_settlements] Rising Settlements is loading. Villages will start to grow.
```

### Multiplayer

Install the mod and Fabric API on the **server and on every client**. The server runs
all village logic. Clients need the mod for the mayor screen and visual effects.

## Building from source (developers)

You need **JDK 25** and **Git**. Gradle is included through the wrapper.

```bash
git clone https://github.com/AmmaarBakshi/Rising-Settlements.git
cd Rising-Settlements

# Build the mod jar -> build/libs/rising-settlements-<version>.jar
./gradlew build          # Windows: gradlew.bat build

# Start a development client with the mod loaded
./gradlew runClient

# Start a development dedicated server
./gradlew runServer

# Decompile Minecraft so you can browse its source in your IDE
./gradlew genSources
```

The first build downloads Gradle, Minecraft and Fabric, so it takes a few minutes.

Since Minecraft 26.1 the game ships **unobfuscated**, so the code uses Mojang's own class
and method names (`net.minecraft.world.level.Level`, `ServerLevel`, and so on). No
mappings such as Yarn are involved. The build uses the `net.fabricmc.fabric-loom` Gradle
plugin, as in the official [Fabric example mod](https://github.com/FabricMC/fabric-example-mod).

### Versions

All versions are kept in [`gradle.properties`](gradle.properties):

| Property | Value |
|---|---|
| `minecraft_version` | 26.3 |
| `loader_version` | 0.20.1 |
| `fabric_api_version` | 0.165.0+26.3 |
| `loom_version` | 1.19-SNAPSHOT |

To update, look up new versions at <https://fabricmc.net/develop>.

## Project structure

```
src/
├── main/                     Common + server code (runs on the logical server)
│   ├── java/com/risingsettlements/
│   │   └── RisingSettlements.java       Mod entry point
│   └── resources/
│       └── fabric.mod.json              Mod metadata for Fabric Loader
└── client/                   Client-only code (screens, visuals)
    └── java/com/risingsettlements/client/
        └── RisingSettlementsClient.java Client entry point
```

The `main` and `client` source sets are kept apart on purpose (Loom's
`splitEnvironmentSourceSets`). Code in `main` cannot use client classes by accident, so the
mod stays safe on dedicated servers.

## Roadmap

- [x] **Phase 0: Setup.** Fabric project, Gradle wrapper, CI build.
- [x] **Phase 1: Village tracking.** Find villages by their bell, save village data, track player-placed blocks, `/settlement info`.
- [x] **Phase 2: Repairs.** Blueprint each village; villagers rebuild missing blocks with particles and sound.
- [ ] **Phase 3: Growth.** Farms, roads and biome-styled houses built a few blocks at a time.
- [ ] **Phase 4: Mayor system.** Reputation, claiming the bell, choosing projects, pausing growth.
- [ ] **Phase 5: Polish.** Config file, villagers holding tools, language file, icon, safe removal.

## Contributing

Bug reports, ideas and pull requests are welcome.

1. Open an [issue](https://github.com/AmmaarBakshi/Rising-Settlements/issues) to describe the bug or idea.
2. Fork the repo and create a branch.
3. Make sure `./gradlew build` passes, and test your change in game with `./gradlew runClient`.
4. Open a pull request that explains what changed and how you tested it.

Please follow the [design rules](#design-rules), especially: never modify player-placed
blocks, and keep all world logic on the server.

All code in this project is original. Please do not copy code from other village mods.

## License

Rising Settlements is released under the [MIT License](LICENSE).

Minecraft is a trademark of Mojang AB. This project is not affiliated with Mojang or Microsoft.
