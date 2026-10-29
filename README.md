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

> **Status:** 1.0.0. Every planned feature is in: growth, repairs, reputation and the
> mayor. See the [changelog](CHANGELOG.md) for what changed in each version.

---

## Contents

- [Features](#features)
- [Commands](#commands)
- [How it works](#how-it-works)
- [Configuration](#configuration)
- [Design rules](#design-rules)
- [Installation (players)](#installation-players)
- [Removing the mod](#removing-the-mod)
- [FAQ](#faq)
- [Known limitations](#known-limitations)
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
| Houses | New houses in the village's biome style go up a few blocks at a time, each with a path to its door. | ✅ 0.4.0 |
| Farms | New fields with a water channel and the crops the biome's villages usually grow. | ✅ 0.4.0 |
| Roads | Paths reach new buildings and grow further out from the village. | ✅ 0.4.0 |
| Wells and lamp posts | Covered wells and lantern posts along the roads. | ✅ 0.4.0 |
| Walls | A wall along the village border, with gaps where roads cross it. Only built when asked for. | ✅ 0.4.0 |
| Reputation | Trading, defending the village and donating at the bell earn its trust. Hurting it costs trust. | ✅ 0.5.0 |
| Mayor | Claim the bell with enough reputation, then pick the next project and pause or resume growth from the mayor screen. | ✅ 0.5.0 |
| Working villagers | The villager doing a job walks there and holds a fitting tool: pickaxe, axe, hoe, shovel or lantern. | ✅ 1.0.0 |
| Config | Growth speed, max village size, costs, reputation, and each feature on or off. Reload without restarting. | ✅ 1.0.0 |

## Commands

| Command | Who | What it does |
|---|---|---|
| `/settlement info` | Everyone | Shows the nearest village (within 256 blocks): name, style, bell, border, villagers, beds and how many blocks are protected. |
| `/settlement list` | Operators | Lists every village the mod knows in the current dimension. |
| `/settlement scan` | Operators | Looks for villages around you right now, instead of waiting for the automatic scan. |
| `/settlement repair` | Operators | Repairs the nearest village's damage right away, skipping the usual one-minute wait. |
| `/settlement grow` | Operators | Starts the village's next project right now, for free. |
| `/settlement grow <project>` | Operators | Starts a specific project right now, for free: `house`, `farm`, `road`, `well`, `lamps` or `wall`. |
| `/settlement pause` / `resume` | Operators | Stops or restarts growth in the nearest village. Repairs carry on. |
| `/settlement cancel` | Operators | Stops the current project. Blocks already placed stay. |
| `/settlement reload` | Operators | Reads the config file again, so changes apply without a restart. |
| `/settlement mayor` | Everyone | Opens the mayor screen (if you are the mayor and near the bell), or says who the mayor is. |
| `/settlement mayor claim` | Everyone | Claims the bell, like sneak-using it. You must be near the bell. |
| `/settlement mayor request <project>` / `auto` | The mayor | Picks the next project, or lets the village choose again. |
| `/settlement mayor pause` / `resume` / `resign` | The mayor | Pauses or resumes growth, or steps down. |
| `/settlement mayor remove` | Operators | Removes the nearest village's mayor. |
| `/settlement reputation <players> <amount>` | Operators | Gives (or with a negative amount, takes) reputation in the nearest village. |

`/settlement info` also shows the mayor, your reputation, the village's resources, what it
is building and what it has built so far. The mayor commands do the same as the mayor
screen, for players who joined without the mod on their client.

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

### Growth

Every in-game day, each villager adds 10 resources to the village (start: 60). Roughly
once a day the village picks a project and pays for it:

| Project | Cost | When the village wants it |
|---|---|---|
| Farm | 40 | There are fewer than 6 farmland blocks per villager. |
| House | 120 | There are hardly any free beds, and at least 3 villagers live there. Up to 12 new houses. |
| Road | 20 | A new building has no path yet, or the village has grown and its roads should reach further. |
| Lamp posts | 30 | The roads have dark stretches (lamps keep 10 blocks apart from every light). |
| Well | 60 | One well for every few new houses. |
| Wall | 200 | Only when a mayor or an operator asks for it. |

If the village can't afford what it wants, it saves up. If there is no room, it tries the
next idea. Repairs always come first, and nothing is built during a raid or at night
(villagers are asleep).

**Where new buildings go.** A spot is only used if the ground is natural (grass, dirt,
sand, snow or stone), close to flat, and empty above, and if there are no player blocks or
village blocks on it or within two blocks of it. Houses stand beside a road with their door
facing it, or on open land with their door facing the bell. A path is laid from the door
to the nearest road. Roads are three blocks wide, climb at most one block per step and
never cross water.

**How it is built.** One villager does the work. Masons build houses, wells and walls,
farmers build fields, cartographers lay roads and toolsmiths put up lamps (any villager
helps out if those are missing). The worker walks to each spot and places one block at a
time, about one block per second, clearing plants and filling dips first. Everything the
village builds becomes part of its blueprint, so it is repaired too. The village border
grows to cover the new buildings.

**House designs.** Every style has three houses (small, medium and long, with 1 to 3
beds) and a well, made from vanilla blocks in the style's materials. They are normal
structure templates in `data/rising_settlements/structure/house/<style>/`, made by
[`tools/TemplateGenerator.java`](tools/TemplateGenerator.java). A data pack can add more
designs: any template in `data/<any namespace>/structure/house/<style>/` is used for
villages of that style. Put the floor at `y = 0` and the front door on the `z = 0` side.

### Reputation and mayors

Every village keeps track of how much it trusts each player:

| Action | Reputation |
|---|---|
| Trade with one of its villagers | +2 |
| Kill a raider in or near the village | +5 |
| Kill another monster inside the village | +1 |
| Donate at the bell | +1 for every 10 resources |
| Kill one of its villagers | −50 |
| Kill one of its iron golems (not one you built) | −25 |

You see each change in the action bar.

**Donating.** Sneak and use the bell while holding something the village can use. The
whole stack is given (as much as the storehouse has room for) and becomes resources for
new projects:

| Items | Resources each |
|---|---|
| Logs, planks, cobblestone, stone, sandstone, glass, wheat, carrots, potatoes, beetroots | 1 |
| Bricks, stone bricks, copper ingots | 2 |
| Bread | 3 |
| Iron and gold ingots | 4 |
| Hay bales | 9 |
| Emeralds | 10 |
| Diamonds | 20 |
| Blocks of copper / iron or gold / emerald / diamond | 18 / 36 / 90 / 180 |

**Becoming mayor.** With 50 reputation, sneak and use the bell with an empty hand to
claim it. Each village has one mayor at a time. Using the bell normally still rings it,
and shows everyone who the mayor is.

**The mayor screen.** As mayor, sneak and use the bell with an empty hand to open it. It
shows the village's villagers, beds, resources, current project and anything holding it
back. From there you can:

- pick the next project (house, farm, road, well, lamp posts or wall). The village builds
  it as soon as it is due and can pay for it. If there is no room for it, the pick is dropped.
- let the village choose its projects again.
- pause or resume growth. Repairs carry on while growth is paused.
- step down (two clicks).

A mayor whose reputation falls below half of what it takes to claim the bell (25) loses
the office, and so does a mayor who steps down. Their pick of next project goes with them.

## Configuration

The config file is `config/rising_settlements.json` (in `.minecraft` for single player,
in the server folder for a server). It is created with the defaults on first start. Edit
it, then run `/settlement reload` or restart. Values outside a sensible range are pulled
back into it.

Times are in game ticks: 20 ticks = 1 second, 1200 = 1 minute, 24000 = 1 in-game day.

**Finding villages**

| Option | Default | What it does |
|---|---|---|
| `discoveryRadius` | 96 | How far around each player (in blocks) to look for village bells. |
| `discoveryIntervalTicks` | 100 | How often to look. |
| `villageMargin` | 8 | Blocks added around the original village to form its border. |
| `maxVillageRadius` | 96 | How far from the bell a village may ever grow. Player blocks are tracked in this whole area. |
| `surveyBlocksPerTick` | 4096 | Positions checked per tick while recording a new village. |
| `censusIntervalTicks` | 600 | How often villagers and beds are counted. |

**Repairs**

| Option | Default | What it does |
|---|---|---|
| `repairsEnabled` | true | Switches repairs on or off. |
| `repairDelayTicks` | 1200 | How long a block must be missing before it is repaired. |
| `repairIntervalTicks` | 40 | Time between two repaired blocks. |
| `damageScanBlocksPerTick` | 128 | Village blocks checked for damage per tick. |

**Growth**

| Option | Default | What it does |
|---|---|---|
| `growthEnabled` | true | Switches all growth on or off. |
| `housesEnabled`, `farmsEnabled`, `roadsEnabled`, `wellsEnabled`, `lampsEnabled`, `wallsEnabled` | true | Switch each kind of project on or off. |
| `projectIntervalTicks` | 24000 | Minimum time between two projects. |
| `buildIntervalTicks` | 20 | Time between two placed blocks while building. |
| `maxNewHouses` | 12 | New houses per village, at most. |
| `minVillagersForHouse` | 3 | Villagers a village needs before it builds houses. |
| `farmlandPerVillager` | 6 | Farmland a village wants per villager. |
| `resourcesPerVillagerPerDay` | 10 | Daily income per villager. |
| `startingResources` | 60 | Resources a newly found village starts with. |
| `maxResources` | 2000 | Most resources a village can store. |
| `houseCost`, `farmCost`, `roadCost`, `wellCost`, `lampsCost`, `wallCost` | 120, 40, 20, 60, 30, 200 | What each project costs. |
| `maxWallBlocks` | 1200 | Most blocks in one wall. |

**Reputation and mayor**

| Option | Default | What it does |
|---|---|---|
| `mayorEnabled` | true | Switches reputation, claiming the bell and the mayor screen on or off. |
| `mayorReputation` | 50 | Reputation needed to claim the bell. |
| `reputationPerTrade` | 2 | Reputation per trade. |
| `reputationPerRaiderKill` | 5 | Reputation per raider killed in or near the village. |
| `reputationPerMonsterKill` | 1 | Reputation per other monster killed inside the village. |
| `villagerKillPenalty` | 50 | Reputation lost per villager killed. |
| `golemKillPenalty` | 25 | Reputation lost per village iron golem killed. |
| `donationsEnabled` | true | Switches donating at the bell on or off. |
| `resourcesPerReputation` | 10 | Resources donated per point of reputation. |

**Workers**

| Option | Default | What it does |
|---|---|---|
| `workerWalkTimeoutTicks` | 300 | If the worker gets no closer for this long, another villager takes over. After two swaps the block is placed anyway. |
| `workersHoldTools` | true | Working villagers hold a tool that fits the job. |

**Faster growth for testing:** set `projectIntervalTicks` to 1200 and
`buildIntervalTicks` to 5, then run `/settlement reload`.

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
all village logic; clients need the mod for the mayor screen. A player without the mod
on their client gets the mayor information in chat instead and can use the
`/settlement mayor` commands.

## Removing the mod

The mod only places vanilla blocks and keeps its own data in separate files, so a world
keeps working without it:

1. Close the game (or stop the server) normally, so working villagers put their tools
   away.
2. Delete `rising-settlements-<version>.jar` from the `mods` folder.

Everything the villagers built stays. The village data in
`<world>/dimensions/minecraft/*/data/rising_settlements/` is no longer used and can be
deleted. When the world loads you may see `Missing data pack rising_settlements` in the
log; that is harmless. If you install the mod again later, it finds the villages again.

## FAQ

**Will villagers break or build over my houses?**
No. Every block a player places near a village is remembered and never changed. New
buildings only go on natural ground (grass, dirt, sand, snow, stone) with nothing but
plants above it, so even builds made before you installed the mod are left alone.

**I broke a village house. Why isn't it rebuilt?**
Blocks a player breaks are treated as the player's choice and are not put back. Repairs
are for damage from creepers, raids, fire and other mobs.

**Nothing is happening in my village.**
Villages only work while their chunks are loaded, so stay nearby. Projects start about
once a day, nobody builds at night or during a raid, and repairs come first. Run
`/settlement info` near the bell: it shows the resources, the current project and what
the village is waiting for (for example "Saving up for: house").

**Does it work on servers?**
Yes. All village logic runs on the server. Install the mod on the server and on each
client (players without it can still use the `/settlement mayor` commands).

**Does it work with TLauncher?**
Yes. Install Fabric Loader for 26.3 as described above, pick the Fabric version in
TLauncher and put the jars in `.minecraft\mods`.

**Do villages built by players count?**
No. Only villages that the game generated (a bell inside a village structure) are tracked.

## Known limitations

- Only naturally generated villages grow; villages built by players are not tracked.
- Villages only change while players are nearby (their chunks must be loaded).
- Repairs bring back blocks, not what was inside them: a rebuilt chest is empty.
- On very hilly or wooded land a village may find no room to grow. It then tries other
  projects and keeps looking.
- The mayor screen needs the mod on the client.
- Trades with wandering traders don't earn reputation; they don't belong to a village.

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
│   │   ├── command/                   /settlement command
│   │   ├── config/                    Config file (config/rising_settlements.json)
│   │   ├── growth/                    Choosing projects, finding sites, planning houses,
│   │   │                              farms, roads, wells, lamps and walls
│   │   ├── mayor/                     Reputation, donations, the bell and the mayor's office
│   │   ├── mixin/                     Hooks that notice player-placed blocks and trades
│   │   ├── network/                   Mayor screen packets (data to the client, buttons back)
│   │   ├── repair/                    Noticing damage and planning repairs
│   │   ├── tracking/                  Player block tracking
│   │   ├── util/                      Translations
│   │   ├── village/                   Settlements, discovery, survey, census, saved data
│   │   ├── work/                      Build jobs, workers and block placement
│   │   └── world/                     Block rules, terrain and placement safety checks
│   └── resources/
│       ├── fabric.mod.json            Mod metadata for Fabric Loader
│       ├── assets/rising_settlements/ Language file and icon
│       └── data/rising_settlements/   House and well templates
├── client/                        Client-only code (screens, visuals)
│   └── java/com/risingsettlements/client/
│       ├── RisingSettlementsClient.java Client entry point
│       └── MayorScreen.java           The mayor screen
tools/
└── TemplateGenerator.java         Generates the house and well templates
```

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

## Contributing

Bug reports, ideas and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md)
for how to set up, test and submit a change, and [CHANGELOG.md](CHANGELOG.md) for what
changed in each version.

Please follow the [design rules](#design-rules), especially: never modify player-placed
blocks, and keep all world logic on the server. All code in this project is original;
please do not copy code from other village mods.

## License

Rising Settlements is released under the [MIT License](LICENSE).

Minecraft is a trademark of Mojang AB. This project is not affiliated with Mojang or Microsoft.
