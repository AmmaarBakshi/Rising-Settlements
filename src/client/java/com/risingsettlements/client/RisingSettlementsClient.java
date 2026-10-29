package com.risingsettlements.client;

import com.risingsettlements.RisingSettlements;
import com.risingsettlements.network.MayorScreenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client entry point. Only screens, particles and other visuals belong here;
 * all village logic stays on the server.
 */
public class RisingSettlementsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(MayorScreenPayload.TYPE,
				(payload, context) -> MayorScreen.receive(context.client(), payload));
		RisingSettlements.LOGGER.info("Rising Settlements client initialised.");
	}
}
