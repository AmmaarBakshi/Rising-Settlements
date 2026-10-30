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

> **Status:** all planned features are in (version 0.1.0). See the [roadmap](#roadmap).

---

## Contents

- [Features](#features)
- [Design rules](#design-rules)
- [How to play](#how-to-play)
- [Commands](#commands)
- [Configuration](#configuration)
- [Installation (players)](#installation-players)
- [Building from source (developers)](#building-from-source-developers)
- [Project structure](#project-structure)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

## Features

| Feature | What it does | Status |
|---|---|---|
| Village tracking | Finds villages by their bell and remembers their borders, villagers, biome style and blocks. | Done |
| Repairs | Villagers rebuild blocks lost to raids, explosions and fire. | Done |
| Farms | Farmers widen fields and start new ones nearby. | Done |
| Roads | Paths grow to reach new buildings. | Done |
| Houses | New biome-styled houses go up a few blocks at a time. | Done |
| Mayor | Earn reputation, claim the bell, choose the next project. | Done |
| Config | Growth speed, max village size, each feature on or off. | Done |

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

## How to play

**Finding villages.** Walk into any generated village. Within a few seconds the mod finds its
bell, gives the village a name (such as *Willowbrook*) and quietly records every block of the
original village. Run `/settlement info` while standing in it to see how it is doing.

**Repairs.** About once a minute, villagers look for village blocks that have gone missing:
blown up by creepers, burned, smashed in a raid. They put them back from the bottom up, one
block every second, with a little sparkle and the block's placing sound. Trampled farmland
and paths are restored too. Repairs wait while a raid is going on and need at least one
villager.

**Growth.** Once per in-game day (by default) a village with at least two villagers starts a
new project, based on what it needs:

- **Houses** when there are not enough beds. A house is a small building in the village's
  style (plains, desert, savanna, taiga or snowy) with a bed, a job site block, a torch,
  windows and a door facing a street. A path is laid from the door to the street.
- **Farms** when there is not enough farmland. Farmers first widen an existing field next to
  water; if that is not possible, they lay out a new 9×9 field with a water source in the
  middle.
- **Roads** when the village has everything it needs, so that future houses have somewhere
  to go. New streets lead outwards from the edge of the village.

Projects are built a block at a time. The nearest villager walks over and holds a tool while
it works. The village border grows to include each finished project, up to a maximum radius
around the bell.

**Your builds are safe.** Every block a player places or breaks near a village is remembered,
and the mod never repairs, replaces or builds over those positions. Villages only build on
empty space, loose plants and natural ground.

**Becoming mayor.** Raise your reputation with a village the vanilla way: trade with its
villagers, cure zombie villagers, defend it in raids. When the total reputation is high
enough (30 by default, check it with `/settlement info`), **sneak and use the bell with an
empty hand** to claim it. As mayor, do the same to open the mayor screen, where you can:

- pick what the village builds next (house, farm, road, or let the village decide),
- pause and resume growth (repairs carry on while growth is paused),
- resign.

A village has one mayor at a time. Players without the mod on their client get the same
choices as clickable buttons in chat.

## Commands

| Command | Who | What it does |
|---|---|---|
| `/settlement info` | everyone | Status of the village you are in (or the nearest one): villagers, beds, projects, repairs, mayor, your reputation. |
| `/settlement list` | everyone | All known villages in this dimension. |
| `/settlement mayor claim` | everyone | Claim the bell, same as sneak-using it. |
| `/settlement menu` | mayor | Open the mayor screen (or chat menu). |
| `/settlement project <auto\|house\|farm\|road>` | mayor | Choose the next project. |
| `/settlement pause` / `resume` | mayor | Pause or resume growth. |
| `/settlement mayor resign` | mayor | Give up the bell. |
| `/settlement scan` | operator | Look for villages around you right now. |
| `/settlement grow` | operator | Start the next project immediately. |
| `/settlement repair` | operator | Look for damage right now. |
| `/settlement mayor clear` | operator | Remove the village's mayor. |
| `/settlement forget` | operator | Forget the village. Its blocks stay; it is found again as a new village. |

Operators may also use the mayor commands on any village.

## Configuration

Settings live in `config/rising_settlements.json` and are created with defaults the first
time the game starts. Times are in game ticks (20 ticks = 1 second, 24000 = one in-game
day). Restart the game or server after changing the file.

| Setting | Default | Meaning |
|---|---|---|
| `enableRepairs`, `enableFarms`, `enableRoads`, `enableHouses`, `enableMayor` | `true` | Turn each feature on or off. |
| `villagersHoldTools` | `true` | Villagers walk to the work and hold a tool. |
| `effects` | `true` | Particles and sounds when blocks are placed. |
| `growthIntervalTicks` | `24000` | Time between projects. |
| `buildIntervalTicks` / `buildBlocksPerStep` | `20` / `1` | Building speed: blocks placed per interval. |
| `minVillagersToGrow` | `2` | Villagers needed before a village grows. |
| `maxHousesPerVillage` | `10` | The most houses the mod adds to one village. |
| `farmlandPerVillager` | `10` | Farmland a village wants per villager. |
| `farmExtendBlocks` | `12` | How much an existing field may grow per project. |
| `maxVillageRadius` | `96` | How far from the bell a village may spread (and how far player blocks are tracked). |
| `repairScanIntervalTicks` | `1200` | How often villages look for damage. |
| `repairIntervalTicks` / `repairBlocksPerStep` | `20` / `1` | Repair speed. |
| `mayorReputation` | `30` | Reputation needed to claim a bell. |
| `discoveryRadius`, `discoveryIntervalTicks`, `villageMargin`, `surveyBlocksPerTick`, `repairScanBlocksPerTick`, `censusIntervalTicks` | | Fine tuning for performance. |

Values outside sensible ranges are clamped, so a typo cannot freeze the server.

## Removing the mod

Everything the mod builds is made of vanilla blocks, so removing the mod leaves every house,
field and road in place. Its saved data
(`<world>/dimensions/<dimension>/data/rising_settlements/settlements.dat`) is simply
ignored. Any villager caught holding a work tool at the moment the world was saved keeps it
as a harmless item in its hand.

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

Install the mod and Fabric API on the **server**; it runs all village logic. Installing it on
clients too is recommended: it adds the mayor screen and translated text. Players with a
vanilla client can still join, see villages grow, and manage a village through clickable
chat buttons instead of the screen.

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
├── main/                          Common + server code (runs on the logical server)
│   ├── java/com/risingsettlements/
│   │   ├── RisingSettlements.java     Mod entry point
│   │   ├── config/                    JSON config file
│   │   ├── village/                   Settlements, discovery, survey, census, saving, ticking
│   │   ├── repair/                    Finding and rebuilding missing blocks
│   │   ├── build/                     Growth: house, farm and road planners, build plans
│   │   ├── mayor/                     Reputation, claiming the bell, mayor choices
│   │   ├── network/                   Packets for the mayor screen
│   │   ├── command/                   /settlement
│   │   ├── world/                     Block rules, player-block tracking, effects
│   │   ├── mixin/                     Hook that reports player block placement
│   │   └── util/                      Translatable text
│   └── resources/
│       ├── fabric.mod.json            Mod metadata for Fabric Loader
│       ├── rising_settlements.mixins.json
│       └── assets/rising_settlements/ Icon and language file
└── client/                        Client-only code (screens, visuals)
    └── java/com/risingsettlements/client/
        ├── RisingSettlementsClient.java Client entry point
        └── MayorScreen.java             The mayor screen
```

### How it works

- **Discovery.** Bells are "meeting" points of interest, so the game already knows where they
  are. Every few seconds the mod looks through the points of interest in loaded chunks near
  players. A bell inside a generated village structure becomes a settlement.
- **Blueprint.** The blocks inside the village's structure pieces are recorded once, a slice
  per tick. The blueprint is the only set of blocks the mod ever repairs; everything the mod
  builds is added to it.
- **Player blocks.** A mixin on `BlockItem#place` and Fabric's block-break event record every
  position a player touches near a village. Those positions are never changed.
- **Build plans.** Planners turn a project into an ordered list of placements (foundation
  first, roof last). The list is saved with the world, so a half-built house is finished
  after a restart. Each placement is checked against the world just before it happens.
- **Performance.** All work is spread out: surveys, damage scans and builds handle a bounded
  number of blocks per tick, only in loaded chunks, and each village gets its turn on a
  different tick.

The `main` and `client` source sets are kept apart on purpose (Loom's
`splitEnvironmentSourceSets`). Code in `main` cannot use client classes by accident, so the
mod stays safe on dedicated servers.

## Roadmap

- [x] **Phase 0: Setup.** Fabric project, Gradle wrapper, CI build.
- [x] **Phase 1: Village tracking.** Find villages by their bell, save village data, track player-placed blocks, `/settlement info`.
- [x] **Phase 2: Repairs.** Blueprint each village; villagers rebuild missing blocks with particles and sound.
- [x] **Phase 3: Growth.** Farms, roads and biome-styled houses built a few blocks at a time.
- [x] **Phase 4: Mayor system.** Reputation, claiming the bell, choosing projects, pausing growth.
- [x] **Phase 5: Polish.** Config file, villagers holding tools, language file, icon, safe removal.

Ideas for later: more house designs per style, villagers decorating their streets with lamps
and benches, and trading discounts for the mayor.

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
