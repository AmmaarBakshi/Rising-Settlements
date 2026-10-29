# Changelog

All notable changes to Rising Settlements. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## 1.0.0 - 2026-10-29

### Added
- Working villagers hold a tool that fits the job (pickaxe for repairs, walls and wells,
  axe for houses, hoe for farms, shovel for roads, lantern for lamp posts). The tool never
  drops and is put away when the work stops or the server shuts down.
- `/settlement reload` reads the config file again without a restart.
- Mod icon.
- README sections on configuration, removing the mod, FAQ and known limitations.
- `CONTRIBUTING.md` and this changelog.

## 0.5.0 - 2026-10-29

### Added
- Reputation per player and village: trading (+2), killing raiders (+5) and other monsters
  (+1) in the village, and donating at the bell. Killing villagers (−50) or village iron
  golems (−25) costs reputation.
- Donations: sneak and use the bell while holding building materials, ingots, gems or food.
  They become village resources.
- Claiming the bell: with 50 reputation, sneak and use it with an empty hand to become
  mayor. One mayor per village. Using the bell normally shows who the mayor is.
- Mayor screen: village stats, current project, choice of the next project, pause and
  resume, and stepping down.
- `/settlement mayor ...` commands that do the same in chat, for players without the mod
  on their client, and `/settlement reputation` and `/settlement mayor remove` for
  operators.
- A mayor whose reputation drops below half of the claim threshold loses the office.

## 0.4.0 - 2026-10-29

### Added
- Village growth: resources from villagers, and projects chosen by what the village needs.
- Houses (three designs per biome style), farms, roads, wells, lamp posts and walls,
  built one block at a time by a villager who walks to each spot.
- Sites only on natural, nearly flat ground with no player or village blocks nearby.
- `/settlement grow`, `pause`, `resume` and `cancel` for operators.

### Fixed
- Long jobs no longer stall when the worker can't reach a spot: another villager takes over.

## 0.3.0 - 2026-10-29

### Added
- Village blueprints: every block of the original village is recorded.
- Repairs: blocks lost to raids, explosions and fire are rebuilt by a villager, with
  particles and sound, after a short delay and never during a raid.

## 0.2.0 - 2026-10-29

### Added
- Villages are found by their bell, named, given a biome style and a border.
- Blocks placed by players near villages are tracked and protected.
- Village data is saved with the world.
- `/settlement info`, `list` and `scan`.

## 0.1.0 - 2026-10-29

### Added
- Fabric project setup for Minecraft 26.3, Gradle wrapper, CI build, README and MIT license.
