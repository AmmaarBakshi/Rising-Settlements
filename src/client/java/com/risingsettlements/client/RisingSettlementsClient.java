package com.risingsettlements.client;

import com.risingsettlements.RisingSettlements;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entry point. Only screens, particles and other visuals belong here;
 * all village logic stays on the server.
 */
public class RisingSettlementsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RisingSettlements.LOGGER.info("Rising Settlements client initialised.");
	}
}
