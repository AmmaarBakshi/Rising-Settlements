package com.risingsettlements.village;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.growth.GrowthPlanner;
import com.risingsettlements.repair.RepairPlanner;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

/**
 * Drives all village work from the server tick.
 *
 * <p>Work only happens for settlements whose bell chunk is loaded, so villages far from
 * every player stay frozen and cost nothing. Heavier jobs are spread out: each settlement
 * gets its turn on a different tick.
 */
public final class SettlementTicker {
	private SettlementTicker() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(SettlementTicker::tickLevel);
	}

	private static void tickLevel(ServerLevel level) {
		SettlementManager manager = SettlementManager.get(level);
		VillageDiscovery.tick(level, manager);

		RSConfig config = RSConfig.get();
		long time = level.getGameTime();
		for (Settlement settlement : manager.all()) {
			if (!level.isLoaded(settlement.bell())) {
				continue;
			}
			VillageSurvey.tickSettlement(level, settlement, config.surveyBlocksPerTick);
			RepairPlanner.tick(level, settlement, time);
			GrowthPlanner.tick(level, settlement, time);
			if (isTurn(settlement, time, config.censusIntervalTicks)) {
				VillageCensus.run(level, settlement);
			}
		}
	}

	/** True once every {@code interval} ticks, at a different tick for each settlement. */
	public static boolean isTurn(Settlement settlement, long time, int interval) {
		long offset = Math.floorMod(settlement.id().getLeastSignificantBits(), interval);
		return (time + offset) % interval == 0;
	}
}
