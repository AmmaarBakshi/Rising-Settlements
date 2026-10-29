package com.risingsettlements.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.risingsettlements.RisingSettlements;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side settings, stored in {@code config/rising_settlements.json}.
 *
 * <p>Every field has a safe default. Missing fields are filled in and written back to the
 * file on load, so updating the mod never wipes a player's settings. All tick values are
 * game ticks (20 ticks = 1 second, 24000 ticks = 1 in-game day).
 */
public final class RSConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = "rising_settlements.json";

	private static RSConfig instance = new RSConfig();

	// --- Village discovery ---------------------------------------------------------------

	/** How far (in blocks) around each player to look for village bells. */
	public int discoveryRadius = 96;
	/** How often to look for new villages around players. */
	public int discoveryIntervalTicks = 100;
	/** Extra blocks added around the original village structure to form its border. */
	public int villageMargin = 8;
	/**
	 * Maximum distance (in blocks, horizontally) from the bell that a village may cover.
	 * Player-placed blocks are tracked inside this whole area, so growth never surprises a build.
	 */
	public int maxVillageRadius = 96;
	/** How many block positions to examine per tick when recording a new village's blocks. */
	public int surveyBlocksPerTick = 4096;
	/** How often to recount villagers and beds. */
	public int censusIntervalTicks = 600;

	// --- Repairs -------------------------------------------------------------------------

	/** Villagers rebuild village blocks lost to raids, explosions and fire. */
	public boolean repairsEnabled = true;
	/** How long a block must be missing before villagers start to repair it (1200 = 1 minute). */
	public int repairDelayTicks = 1200;
	/** Time between two repaired blocks (40 = one block every 2 seconds). */
	public int repairIntervalTicks = 40;
	/** How many village blocks to check for damage per tick. */
	public int damageScanBlocksPerTick = 128;

	// --- Growth --------------------------------------------------------------------------

	/** Master switch for all growth (houses, farms, roads, wells, lamps, walls). */
	public boolean growthEnabled = true;
	public boolean housesEnabled = true;
	public boolean farmsEnabled = true;
	public boolean roadsEnabled = true;
	public boolean wellsEnabled = true;
	public boolean lampsEnabled = true;
	/** Walls are only built when a mayor asks for one. */
	public boolean wallsEnabled = true;
	/** Minimum time between two growth projects (24000 = one in-game day). */
	public int projectIntervalTicks = 24000;
	/** Time between two placed blocks while building (20 = one block per second). */
	public int buildIntervalTicks = 20;
	/** Maximum number of new houses per village. */
	public int maxNewHouses = 12;
	/** A village needs at least this many villagers before it builds a house. */
	public int minVillagersForHouse = 3;
	/** Villages want about this many farmland blocks per villager. */
	public int farmlandPerVillager = 6;
	/** Each villager adds this many resources per in-game day. Players can donate more. */
	public int resourcesPerVillagerPerDay = 10;
	public int startingResources = 60;
	public int maxResources = 2000;
	public int houseCost = 120;
	public int farmCost = 40;
	public int roadCost = 20;
	public int wellCost = 60;
	public int lampsCost = 30;
	public int wallCost = 200;
	/** Upper limit for the number of blocks in one wall project. */
	public int maxWallBlocks = 1200;

	// --- Reputation and mayor ------------------------------------------------------------

	/** Players can claim a village's bell and become its mayor. */
	public boolean mayorEnabled = true;
	/** Reputation needed to claim the bell. */
	public int mayorReputation = 50;
	/** Reputation for each trade with one of the village's villagers. */
	public int reputationPerTrade = 2;
	/** Reputation for killing a raider (pillager, vindicator, ravager...) near the village. */
	public int reputationPerRaiderKill = 5;
	/** Reputation for killing any other monster inside the village. */
	public int reputationPerMonsterKill = 1;
	/** Reputation lost for killing one of the village's villagers. */
	public int villagerKillPenalty = 50;
	/** Reputation lost for killing one of the village's iron golems. */
	public int golemKillPenalty = 25;
	/** Players can donate items at the bell (sneak + use while holding them). */
	public boolean donationsEnabled = true;
	/** Resources donated per point of reputation earned. */
	public int resourcesPerReputation = 10;

	// --- Workers -------------------------------------------------------------------------

	/**
	 * If the working villager gets no closer to a spot in this time, another villager takes
	 * over. After two swaps the block is placed anyway, so a job never gets stuck.
	 */
	public int workerWalkTimeoutTicks = 300;
	/** Working villagers hold a tool that fits the job (only for show; it never drops). */
	public boolean workersHoldTools = true;

	public static RSConfig get() {
		return instance;
	}

	/** Loads the config file (creating it with defaults if needed) and writes it back. */
	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		RSConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				loaded = GSON.fromJson(reader, RSConfig.class);
			} catch (IOException | JsonParseException e) {
				RisingSettlements.LOGGER.error("Could not read {}, using defaults: {}", file, e.getMessage());
			}
		}
		instance = loaded != null ? loaded : new RSConfig();
		instance.validate();

		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			RisingSettlements.LOGGER.error("Could not write {}: {}", file, e.getMessage());
		}
	}

	/** Clamps values to sensible ranges so a typo can't freeze the server. */
	private void validate() {
		discoveryRadius = clamp(discoveryRadius, 16, 256);
		discoveryIntervalTicks = clamp(discoveryIntervalTicks, 20, 24000);
		villageMargin = clamp(villageMargin, 0, 32);
		maxVillageRadius = clamp(maxVillageRadius, 32, 256);
		surveyBlocksPerTick = clamp(surveyBlocksPerTick, 256, 65536);
		censusIntervalTicks = clamp(censusIntervalTicks, 100, 24000);
		repairDelayTicks = clamp(repairDelayTicks, 0, 24000 * 7);
		// "At most a few blocks per second": never faster than 4 blocks per second.
		repairIntervalTicks = clamp(repairIntervalTicks, 5, 24000);
		damageScanBlocksPerTick = clamp(damageScanBlocksPerTick, 8, 4096);
		workerWalkTimeoutTicks = clamp(workerWalkTimeoutTicks, 40, 2400);
		projectIntervalTicks = clamp(projectIntervalTicks, 200, 24000 * 30);
		buildIntervalTicks = clamp(buildIntervalTicks, 5, 24000);
		maxNewHouses = clamp(maxNewHouses, 0, 100);
		minVillagersForHouse = clamp(minVillagersForHouse, 0, 100);
		farmlandPerVillager = clamp(farmlandPerVillager, 0, 100);
		resourcesPerVillagerPerDay = clamp(resourcesPerVillagerPerDay, 0, 10000);
		maxResources = clamp(maxResources, 0, 1_000_000);
		startingResources = clamp(startingResources, 0, maxResources);
		houseCost = clamp(houseCost, 0, maxResources);
		farmCost = clamp(farmCost, 0, maxResources);
		roadCost = clamp(roadCost, 0, maxResources);
		wellCost = clamp(wellCost, 0, maxResources);
		lampsCost = clamp(lampsCost, 0, maxResources);
		wallCost = clamp(wallCost, 0, maxResources);
		maxWallBlocks = clamp(maxWallBlocks, 16, 10000);
		mayorReputation = clamp(mayorReputation, 0, 10000);
		reputationPerTrade = clamp(reputationPerTrade, 0, 1000);
		reputationPerRaiderKill = clamp(reputationPerRaiderKill, 0, 1000);
		reputationPerMonsterKill = clamp(reputationPerMonsterKill, 0, 1000);
		villagerKillPenalty = clamp(villagerKillPenalty, 0, 10000);
		golemKillPenalty = clamp(golemKillPenalty, 0, 10000);
		resourcesPerReputation = clamp(resourcesPerReputation, 1, 10000);
	}

	static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
