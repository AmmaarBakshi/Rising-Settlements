package com.risingsettlements;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.tracking.PlayerBlockTracker;
import com.risingsettlements.village.SettlementTicker;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point of Rising Settlements.
 *
 * <p>Everything that changes the world (tracking villages, repairs, growth, the mayor
 * system) is registered from here and runs on the logical server only. The client
 * entry point ({@code RisingSettlementsClient}) only deals with screens and visuals.
 */
public class RisingSettlements implements ModInitializer {
	public static final String MOD_ID = "rising_settlements";

	/** Logger named after the mod ID, so log lines are easy to find. */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Rising Settlements is loading. Villages will start to grow.");
		RSConfig.load();
		SettlementTicker.register();
		PlayerBlockTracker.register();
	}

	/** Builds an identifier in the mod's namespace, e.g. {@code rising_settlements:village}. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
