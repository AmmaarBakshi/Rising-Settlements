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

	// --- Features ------------------------------------------------------------------------

	/** Villagers rebuild village blocks lost to raids, explosions and fire. */
	public boolean enableRepairs = true;
	/** Farmers widen fields and start new ones. */
	public boolean enableFarms = true;
	/** Paths grow to reach new buildings, and new streets are laid out. */
	public boolean enableRoads = true;
	/** New biome-styled houses are built. */
	public boolean enableHouses = true;
	/** Players can claim a village's bell and choose what it builds next. */
	public boolean enableMayor = true;
	/** Villagers walk to the work and hold a tool while blocks are placed. */
	public boolean villagersHoldTools = true;
	/** Show particles and play sounds when a block is built or repaired. */
	public boolean effects = true;

	// --- Repairs -------------------------------------------------------------------------

	/** How often to look through a village for missing blocks. */
	public int repairScanIntervalTicks = 1200;
	/** How many blueprint positions to check per tick while looking for damage. */
	public int repairScanBlocksPerTick = 2048;
	/** How often villagers repair a block. */
	public int repairIntervalTicks = 20;
	/** How many blocks are repaired each time. */
	public int repairBlocksPerStep = 1;

	// --- Growth --------------------------------------------------------------------------

	/** Time between the end of one growth project and the start of the next (1 day by default). */
	public int growthIntervalTicks = 24000;
	/** How often villagers place a block of the current project. */
	public int buildIntervalTicks = 20;
	/** How many blocks are placed each time. */
	public int buildBlocksPerStep = 1;
	/** A village needs at least this many villagers before it starts to grow. */
	public int minVillagersToGrow = 2;
	/** The most houses the mod will add to one village. */
	public int maxHousesPerVillage = 10;
	/** Farmland a village wants per villager before it prefers other projects. */
	public int farmlandPerVillager = 10;
	/** How many blocks an existing field may grow by in one project. */
	public int farmExtendBlocks = 12;

	// --- Mayor ---------------------------------------------------------------------------

	/**
	 * Total reputation (the vanilla gossip score summed over the village's villagers)
	 * needed to claim the bell. Trading, curing zombie villagers and defending the village
	 * all raise it.
	 */
	public int mayorReputation = 30;

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
		repairScanIntervalTicks = clamp(repairScanIntervalTicks, 200, 72000);
		repairScanBlocksPerTick = clamp(repairScanBlocksPerTick, 64, 65536);
		repairIntervalTicks = clamp(repairIntervalTicks, 1, 24000);
		repairBlocksPerStep = clamp(repairBlocksPerStep, 1, 16);
		growthIntervalTicks = clamp(growthIntervalTicks, 200, 24000 * 30);
		buildIntervalTicks = clamp(buildIntervalTicks, 1, 24000);
		buildBlocksPerStep = clamp(buildBlocksPerStep, 1, 16);
		minVillagersToGrow = clamp(minVillagersToGrow, 0, 100);
		maxHousesPerVillage = clamp(maxHousesPerVillage, 0, 200);
		farmlandPerVillager = clamp(farmlandPerVillager, 0, 200);
		farmExtendBlocks = clamp(farmExtendBlocks, 1, 64);
		mayorReputation = clamp(mayorReputation, 0, 10000);
	}

	static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
