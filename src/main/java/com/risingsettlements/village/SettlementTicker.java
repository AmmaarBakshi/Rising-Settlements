package com.risingsettlements.village;

import com.risingsettlements.build.ConstructionWorker;
import com.risingsettlements.build.GrowthPlanner;
import com.risingsettlements.config.RSConfig;
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
		for (Settlement settlement : manager.all()) {
			if (!level.isLoaded(settlement.bell())) {
				continue;
			}
			VillageSurvey.tickSettlement(level, settlement, config.surveyBlocksPerTick);
			if ((level.getGameTime() + settlement.id().hashCode()) % config.censusIntervalTicks == 0) {
				Census.run(level, settlement);
			}
			settlement.runtime().repairs.tick(level, settlement);
			GrowthPlanner.tick(level, settlement);
			ConstructionWorker.tick(level, settlement);
			WorkerVillagers.tick(level, settlement);
		}
	}
}
