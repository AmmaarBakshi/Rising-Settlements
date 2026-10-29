package com.risingsettlements.growth;

import com.risingsettlements.RisingSettlements;
import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Building;
import com.risingsettlements.village.GrowthState;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementRuntime;
import com.risingsettlements.village.SettlementTicker;
import com.risingsettlements.village.VillageStyle;
import com.risingsettlements.work.BuildJob;
import com.risingsettlements.work.JobRunner;
import com.risingsettlements.work.Placement;
import com.risingsettlements.work.Workers;
import com.risingsettlements.world.BlockRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides what a village builds next, and builds it.
 *
 * <p>Every in-game day each villager adds some resources. Once the project interval has
 * passed, the village picks the thing it needs most:
 * <ol>
 *     <li>a <b>farm</b> if there isn't enough farmland for everyone;</li>
 *     <li>a <b>house</b> if there are hardly any free beds;</li>
 *     <li>a <b>road</b> to a building that isn't connected yet;</li>
 *     <li><b>lamp posts</b>, a <b>well</b> and <b>longer roads</b> as the village grows;</li>
 *     <li>otherwise another <b>house</b>, up to the configured maximum.</li>
 * </ol>
 * If the mayor asked for something, that comes first. If the village can't afford the
 * project yet, it saves up. If there is no room for it, it tries the next one.
 */
public final class GrowthPlanner {
	/** A village wants a new house when it has fewer free beds than this. */
	private static final int SPARE_BEDS = 2;
	/** Spots to try per project before giving up for now. */
	private static final int SITE_ATTEMPTS = 32;
	/**
	 * When no spot was found, look again soon (a few times), since every search tries
	 * different random spots. After that, wait longer.
	 */
	private static final int QUICK_RETRIES = 10;
	private static final int QUICK_RETRY_TICKS = 200;
	private static final int MAX_LAMPS = 4;

	private GrowthPlanner() {
	}

	public enum Outcome {
		STARTED,
		/** The village wants this project but can't pay for it yet. */
		SAVING_UP,
		/** There was nowhere to build it. */
		NO_SPACE,
		/** The village has everything it wants. */
		FULLY_GROWN,
		/** Growth (or this kind of project) is switched off, or there are no villagers. */
		NOT_ALLOWED
	}

	public record Result(Outcome outcome, BuildJob.@Nullable Kind kind, int blocks) {
	}

	/** A planned project: the work, and the building it adds (if any). */
	private record Plan(BuildJob job, @Nullable Building building) {
	}

	public static void tick(ServerLevel level, Settlement settlement, long time) {
		RSConfig config = RSConfig.get();
		if (!config.growthEnabled || !settlement.isSurveyed()) {
			return;
		}
		GrowthState growth = settlement.growth();
		collectIncome(level, settlement, config);

		// Repairs come first, nobody builds during a raid, and the mayor can pause growth.
		if (growth.isPaused() || settlement.runtime().repairJob != null || level.isRaided(settlement.bell())) {
			return;
		}

		BuildJob project = growth.project();
		if (project != null) {
			if (JobRunner.tick(level, settlement, project, time, config.buildIntervalTicks) == JobRunner.Status.FINISHED) {
				finish(level, settlement, project, time);
			}
			return;
		}
		if (time >= growth.nextProjectTime() && SettlementTicker.isTurn(settlement, time, 20)) {
			Result result = startNext(level, settlement, time, null, false);
			SettlementRuntime runtime = settlement.runtime();
			if (result.outcome() == Outcome.STARTED) {
				runtime.failedSearches = 0;
			} else if (result.outcome() == Outcome.NO_SPACE && runtime.failedSearches++ < QUICK_RETRIES) {
				growth.setNextProjectTime(time + QUICK_RETRY_TICKS);
			} else {
				// Try again later; the village may have saved up or freed some space by then.
				runtime.failedSearches = 0;
				growth.setNextProjectTime(time + Math.max(QUICK_RETRY_TICKS, config.projectIntervalTicks / 4));
			}
		}
	}

	/** Villagers add resources once per in-game day (sleeping through the night counts). */
	private static void collectIncome(ServerLevel level, Settlement settlement, RSConfig config) {
		GrowthState growth = settlement.growth();
		long day = level.getOverworldClockTime() / 24000L;
		if (day == growth.lastIncomeDay()) {
			return;
		}
		if (growth.lastIncomeDay() >= 0) {
			growth.addResources(settlement.villagers() * config.resourcesPerVillagerPerDay, config.maxResources);
		}
		growth.setLastIncomeDay(day);
	}

	/**
	 * Plans and starts the next project.
	 *
	 * @param forced build exactly this (used by {@code /settlement grow}), or null to let the village choose
	 * @param free   skip the cost (operators testing)
	 */
	public static Result startNext(ServerLevel level, Settlement settlement, long time, BuildJob.@Nullable Kind forced, boolean free) {
		RSConfig config = RSConfig.get();
		GrowthState growth = settlement.growth();
		if (!config.growthEnabled || settlement.villagers() == 0 && forced == null) {
			return note(growth, new Result(Outcome.NOT_ALLOWED, null, 0));
		}

		List<BlockPos> roads = roads(level, settlement);
		BuildJob.Kind requested = growth.requested();
		List<BuildJob.Kind> wishes = forced != null ? List.of(forced)
				: requested != null ? List.of(requested)
				: wishes(settlement, config);
		if (wishes.isEmpty()) {
			return note(growth, new Result(Outcome.FULLY_GROWN, null, 0));
		}

		Result result = new Result(Outcome.NOT_ALLOWED, null, 0);
		RandomSource random = level.getRandom();
		for (BuildJob.Kind kind : wishes) {
			if (!isEnabled(kind, config)) {
				continue;
			}
			int cost = cost(kind, config);
			if (!free && growth.resources() < cost) {
				return note(growth, new Result(Outcome.SAVING_UP, kind, 0));
			}
			Plan plan = plan(level, settlement, kind, roads, random);
			if (plan == null) {
				result = new Result(Outcome.NO_SPACE, kind, 0);
				continue;
			}

			if (!free) {
				growth.spend(cost);
			}
			if (plan.building() != null) {
				growth.addBuilding(plan.building());
			}
			// The border grows to cover new work, so villagers working there still count as home.
			BoundingBox.encapsulatingPositions(plan.job().positions()).ifPresent(area ->
					settlement.growBounds(area, config.villageMargin, config.maxVillageRadius));
			if (kind == requested) {
				growth.setRequested(null);
			}
			growth.setProject(plan.job());
			RisingSettlements.LOGGER.info("{} started a new {} project ({} blocks)", settlement.name(), kind.id(), plan.job().total());
			return note(growth, new Result(Outcome.STARTED, kind, plan.job().total()));
		}

		// A mayor's wish that can't be built anywhere is dropped, so the village doesn't get stuck.
		if (requested != null && forced == null && result.outcome() == Outcome.NO_SPACE) {
			growth.setRequested(null);
		}
		return note(growth, result);
	}

	/** Pauses or resumes growth. Pausing sends the current worker back to its normal life. */
	public static void setPaused(ServerLevel level, Settlement settlement, boolean paused) {
		settlement.growth().setPaused(paused);
		BuildJob project = settlement.growth().project();
		if (paused && project != null) {
			Workers.release(level, project.workerId());
		}
	}

	/** Stops the current project (blocks already placed stay). Returns false if there was none. */
	public static boolean cancel(ServerLevel level, Settlement settlement) {
		BuildJob project = settlement.growth().project();
		if (project == null) {
			return false;
		}
		Workers.release(level, project.workerId());
		settlement.growth().setProject(null);
		return true;
	}

	private static void finish(ServerLevel level, Settlement settlement, BuildJob project, long time) {
		Workers.release(level, project.workerId());
		GrowthState growth = settlement.growth();
		growth.addCompleted(project.kind());
		growth.setProject(null);
		growth.setNextProjectTime(time + RSConfig.get().projectIntervalTicks);
		RisingSettlements.LOGGER.info("{} finished its {} project", settlement.name(), project.kind().id());
	}

	/** What the village would like to build, most important first. */
	static List<BuildJob.Kind> wishes(Settlement settlement, RSConfig config) {
		GrowthState growth = settlement.growth();
		int villagers = settlement.villagers();
		int houses = growth.completed(BuildJob.Kind.HOUSE);
		int farmland = settlement.blueprint().count(state -> state.is(Blocks.FARMLAND));
		boolean canHouse = villagers >= config.minVillagersForHouse && houses < config.maxNewHouses;
		boolean unconnected = growth.buildings().stream().anyMatch(b -> !b.connected());

		List<BuildJob.Kind> wishes = new ArrayList<>();
		if (farmland < villagers * config.farmlandPerVillager) {
			wishes.add(BuildJob.Kind.FARM);
		}
		if (canHouse && settlement.beds() - villagers < SPARE_BEDS) {
			wishes.add(BuildJob.Kind.HOUSE);
		}
		if (unconnected || growth.completed(BuildJob.Kind.ROAD) < 1 + houses / 2) {
			wishes.add(BuildJob.Kind.ROAD);
		}
		if (growth.completed(BuildJob.Kind.LAMPS) < 1 + houses / 2) {
			wishes.add(BuildJob.Kind.LAMPS);
		}
		if (growth.completed(BuildJob.Kind.WELL) < (houses + 2) / 4) {
			wishes.add(BuildJob.Kind.WELL);
		}
		if (canHouse && !wishes.contains(BuildJob.Kind.HOUSE)) {
			wishes.add(BuildJob.Kind.HOUSE);
		}
		wishes.removeIf(kind -> !isEnabled(kind, config));
		return wishes;
	}

	private static @Nullable Plan plan(ServerLevel level, Settlement settlement, BuildJob.Kind kind, List<BlockPos> roads, RandomSource random) {
		VillageStyle style = settlement.style();
		return switch (kind) {
			case HOUSE -> planHouse(level, settlement, roads, random);
			case WELL -> {
				TemplateShape well = Templates.well(level, style);
				SiteFinder.Site site = well == null ? null
						: SiteFinder.findForTemplate(level, settlement, well, roads, random, SITE_ATTEMPTS, 1, 2);
				yield site == null ? null : new Plan(
						new BuildJob(kind, TemplatePlanner.plan(level, well, site, style.foundation())),
						new Building(kind, site.box(), site.entrance(), true));
			}
			case FARM -> {
				FarmPlanner.Plan farm = FarmPlanner.plan(level, settlement, roads, random, SITE_ATTEMPTS * 2);
				yield farm == null ? null : new Plan(new BuildJob(kind, farm.placements()),
						new Building(kind, farm.area(), farm.area().getCenter(), true));
			}
			case ROAD -> planRoad(level, settlement, roads, random);
			case LAMPS -> job(kind, LampPlanner.plan(level, settlement, roads, random, MAX_LAMPS));
			case WALL -> job(kind, WallPlanner.plan(level, settlement, RSConfig.get().maxWallBlocks));
			case REPAIR -> null;
		};
	}

	private static @Nullable Plan planHouse(ServerLevel level, Settlement settlement, List<BlockPos> roads, RandomSource random) {
		List<TemplateShape> houses = new ArrayList<>(Templates.houses(level, settlement.style()));
		if (houses.isEmpty()) {
			RisingSettlements.LOGGER.warn("No house templates found for style {}", settlement.style().id());
			return null;
		}
		Util.shuffle(houses, random);
		// Failed spots are cheap to check (most fail on the first column), so houses get extra tries.
		int attempts = Math.max(8, SITE_ATTEMPTS * 2 / houses.size());
		for (TemplateShape house : houses) {
			SiteFinder.Site site = SiteFinder.findForTemplate(level, settlement, house, roads, random, attempts, 2, 2);
			if (site == null) {
				continue;
			}
			List<Placement> placements = new ArrayList<>(TemplatePlanner.plan(level, house, site, settlement.style().foundation()));
			// The path to the front door is part of the house project.
			List<Placement> path = RoadPlanner.connect(level, settlement, site.entrance(), List.of(site.box()), false);
			if (path != null) {
				placements.addAll(path);
			}
			return new Plan(new BuildJob(BuildJob.Kind.HOUSE, placements),
					new Building(BuildJob.Kind.HOUSE, site.box(), site.entrance(), path != null));
		}
		return null;
	}

	/** Connects a building that has no road yet, or else makes the road network reach further. */
	private static @Nullable Plan planRoad(ServerLevel level, Settlement settlement, List<BlockPos> roads, RandomSource random) {
		GrowthState growth = settlement.growth();
		for (Building building : growth.buildings()) {
			if (building.connected()) {
				continue;
			}
			List<Placement> path = RoadPlanner.connect(level, settlement, building.entrance(), List.of(), true);
			if (path != null) {
				growth.replaceBuilding(building, building.connect());
				if (!path.isEmpty()) {
					return new Plan(new BuildJob(BuildJob.Kind.ROAD, path), null);
				}
			}
		}
		return job(BuildJob.Kind.ROAD, RoadPlanner.extend(level, settlement, roads, random, 6));
	}

	private static @Nullable Plan job(BuildJob.Kind kind, @Nullable List<Placement> placements) {
		return placements == null || placements.isEmpty() ? null : new Plan(new BuildJob(kind, placements), null);
	}

	/** The village's road blocks that are walkable and in loaded chunks. */
	private static List<BlockPos> roads(ServerLevel level, Settlement settlement) {
		VillageStyle style = settlement.style();
		List<BlockPos> roads = new ArrayList<>();
		for (BlockPos pos : settlement.blueprint().find(style::isRoad)) {
			if (level.isLoaded(pos) && BlockRules.isClearable(level.getBlockState(pos.above()))) {
				roads.add(pos);
			}
		}
		return roads;
	}

	public static boolean isEnabled(BuildJob.Kind kind, RSConfig config) {
		return switch (kind) {
			case HOUSE -> config.housesEnabled;
			case FARM -> config.farmsEnabled;
			case ROAD -> config.roadsEnabled;
			case WELL -> config.wellsEnabled;
			case LAMPS -> config.lampsEnabled;
			case WALL -> config.wallsEnabled;
			case REPAIR -> false;
		};
	}

	public static int cost(BuildJob.Kind kind, RSConfig config) {
		return switch (kind) {
			case HOUSE -> config.houseCost;
			case FARM -> config.farmCost;
			case ROAD -> config.roadCost;
			case WELL -> config.wellCost;
			case LAMPS -> config.lampsCost;
			case WALL -> config.wallCost;
			case REPAIR -> 0;
		};
	}

	/** Remembers the outcome so {@code /settlement info} and the mayor screen can explain it. */
	private static Result note(GrowthState growth, Result result) {
		String note = switch (result.outcome()) {
			case STARTED -> "";
			case SAVING_UP -> "saving_up:" + result.kind().id();
			case NO_SPACE -> result.kind() != null ? "no_space:" + result.kind().id() : "no_space";
			case FULLY_GROWN -> "fully_grown";
			case NOT_ALLOWED -> "not_allowed";
		};
		growth.setNote(note);
		return result;
	}
}
