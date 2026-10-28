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
	}

	static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
