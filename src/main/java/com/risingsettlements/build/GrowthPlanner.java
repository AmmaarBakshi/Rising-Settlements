package com.risingsettlements.build;

import com.risingsettlements.RisingSettlements;
import com.risingsettlements.config.RSConfig;
import com.risingsettlements.util.Texts;
import com.risingsettlements.village.Project;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.world.BlockRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides when a village starts its next project and what it builds.
 *
 * <p>A village grows once per growth interval (one in-game day by default), and only
 * while it has enough villagers and is not paused. If a mayor picked a project, that is
 * what gets built. Otherwise the village looks at its census: short on beds means a
 * house, short on farmland means a field, and a village with everything it needs lays out
 * a new street for future houses.
 */
public final class GrowthPlanner {
	/** How often each settlement checks whether it is time to grow. */
	private static final int CHECK_INTERVAL = 200;
	/** When nothing could be planned, try again after this long instead of a full interval. */
	private static final int RETRY_TICKS = 2400;

	private GrowthPlanner() {
	}

	public static void tick(ServerLevel level, Settlement settlement) {
		if (settlement.activePlan() != null || (level.getGameTime() + settlement.id().hashCode()) % CHECK_INTERVAL != 0) {
			return;
		}
		RSConfig config = RSConfig.get();
		if (!settlement.isSurveyed() || settlement.isGrowthPaused() || settlement.villagers() < config.minVillagersToGrow) {
			return;
		}
		if (level.getGameTime() - settlement.lastGrowthAt() >= config.growthIntervalTicks) {
			start(level, settlement);
		}
	}

	/**
	 * Plans and starts the next project right away.
	 *
	 * @return the new plan, or null if nothing can be built at the moment
	 */
	public static @Nullable BuildPlan start(ServerLevel level, Settlement settlement) {
		RSConfig config = RSConfig.get();
		List<BlockPos> roads = settlement.blueprint().find(BlockRules::isRoad);
		for (Project project : choices(settlement, config)) {
			BuildPlan plan = switch (project) {
				case HOUSE -> HousePlanner.plan(level, settlement, roads, level.getRandom());
				case FARM -> FarmPlanner.plan(level, settlement, roads, level.getRandom());
				case ROAD -> RoadPlanner.extend(level, settlement, roads, level.getRandom());
				case AUTO -> null;
			};
			if (plan != null && plan.blockCount() > 0) {
				settlement.setActivePlan(plan);
				settlement.setLastGrowthAt(level.getGameTime());
				RisingSettlements.LOGGER.info("{} started a new {} at {} ({} blocks)", settlement.name(), plan.kind().id(),
						plan.area().getCenter().toShortString(), plan.blockCount());
				tellMayor(level, settlement, Texts.tr("growth.started", settlement.name(), Texts.tr("project." + plan.kind().id())));
				return plan;
			}
		}
		// Nowhere to build right now (chunks unloaded, no flat ground...). Try again a bit later.
		long retryAt = level.getGameTime() + Math.min(RETRY_TICKS, config.growthIntervalTicks);
		settlement.setLastGrowthAt(retryAt - config.growthIntervalTicks);
		return null;
	}

	/** Projects to try, best first. */
	static List<Project> choices(Settlement settlement, RSConfig config) {
		boolean houses = config.enableHouses && settlement.housesBuilt() < config.maxHousesPerVillage;
		List<Project> order = new ArrayList<>(3);
		if (settlement.project() != Project.AUTO) {
			order.add(settlement.project());
		} else {
			int farmland = settlement.blueprint().count(state -> state.is(Blocks.FARMLAND));
			boolean needBeds = settlement.beds() < settlement.villagers() + 2;
			boolean needFood = farmland < settlement.villagers() * config.farmlandPerVillager;
			if (needBeds) {
				order.addAll(List.of(Project.HOUSE, Project.FARM, Project.ROAD));
			} else if (needFood) {
				order.addAll(List.of(Project.FARM, Project.HOUSE, Project.ROAD));
			} else {
				order.addAll(List.of(Project.ROAD, Project.HOUSE, Project.FARM));
			}
		}
		order.removeIf(project -> switch (project) {
			case HOUSE -> !houses;
			case FARM -> !config.enableFarms;
			case ROAD -> !config.enableRoads;
			case AUTO -> true;
		});
		return order;
	}

	/** Sends a message to the village's mayor, if they are online. */
	public static void tellMayor(ServerLevel level, Settlement settlement, Component message) {
		if (settlement.mayor() == null) {
			return;
		}
		ServerPlayer mayor = level.getServer().getPlayerList().getPlayer(settlement.mayor());
		if (mayor != null) {
			mayor.sendSystemMessage(message);
		}
	}
}
