package com.risingsettlements.client;

import com.risingsettlements.RisingSettlements;
import com.risingsettlements.network.MayorStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client entry point. Only screens, particles and other visuals belong here;
 * all village logic stays on the server.
 */
public class RisingSettlementsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(MayorStatePayload.TYPE,
				(payload, context) -> context.client().gui.setScreen(new MayorScreen(payload)));
		RisingSettlements.LOGGER.info("Rising Settlements client initialised.");
	}
}
