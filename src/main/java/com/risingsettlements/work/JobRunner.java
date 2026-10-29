package com.risingsettlements.work;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.world.PlacementRules;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.jspecify.annotations.Nullable;

/**
 * Moves a {@link BuildJob} forward, one block at a time.
 *
 * <p>Each step: make sure a villager is working on it, walk them to the next spot, and
 * once they are close (or have tried for a while) place the block. Then wait the
 * configured interval before the next one. This keeps every village to a handful of
 * blocks per second at most.
 */
public final class JobRunner {
	/** Pick a new waypoint once a second. */
	private static final int GUIDE_INTERVAL = 20;
	/** How often to check that the worker is still heading for the job. */
	private static final int STEER_INTERVAL = 5;
	/**
	 * A villager that stops making progress is swapped for another one and left out of
	 * this job for a minute. Now and then a villager gets stuck on a corner or a plant.
	 */
	private static final int BENCH_TICKS = 1200;
	/** After this many swaps for one spot nobody can get there; it is placed anyway so the job never gets stuck. */
	private static final int MAX_SWAPS = 2;

	public enum Status {
		/** Work is going on (or waiting for the next step). */
		WORKING,
		/** No villager can work right now, e.g. at night. */
		WAITING_FOR_WORKER,
		/** Every block is done. */
		FINISHED
	}

	private JobRunner() {
	}

	/**
	 * @param interval ticks between two placed blocks
	 */
	public static Status tick(ServerLevel level, Settlement settlement, BuildJob job, long time, int interval) {
		Placement next = job.peek();
		if (next == null) {
			return Status.FINISHED;
		}

		Villager worker = Workers.resolve(level, settlement, job, next.pos(), professionFor(job.kind()));
		if (worker == null) {
			return Status.WAITING_FOR_WORKER;
		}
		if (job.walkStartedAt == 0) {
			job.walkStartedAt = time;
		}
		// The walk timeout only counts time without progress, so long walks are fine.
		double distance = Workers.distance(worker, next.pos());
		if (distance < job.closest - 1) {
			job.closest = distance;
			job.walkStartedAt = time;
		}
		long walking = time - job.walkStartedAt;
		if (job.walkTo == null || walking % GUIDE_INTERVAL == 0) {
			job.walkTo = Workers.walkTarget(level, worker, next.pos());
		}
		if (walking % STEER_INTERVAL == 0) {
			Workers.steer(worker, job.walkTo, next.pos());
		}
		if (time < job.nextActionTime) {
			return Status.WORKING;
		}

		boolean arrived = Workers.isNear(worker, next.pos());
		if (!arrived) {
			if (walking < RSConfig.get().workerWalkTimeoutTicks) {
				return Status.WORKING;
			}
			if (job.swaps < MAX_SWAPS) {
				// This villager can't get there (stuck, blocked path, fenced in): let someone else try.
				job.benched.put(worker.getUUID(), time + BENCH_TICKS);
				job.swaps++;
				Workers.release(level, job.worker);
				job.worker = null;
				job.walkTo = null;
				job.walkStartedAt = time;
				job.closest = Double.MAX_VALUE;
				return Status.WORKING;
			}
		}

		switch (PlacementRules.check(level, settlement, next)) {
			case OK -> {
				BlockPlacer.place(level, settlement, next, worker);
				job.completeHead();
				job.nextActionTime = time + interval;
				job.walkStartedAt = time;
				Placement following = job.peek();
				if (following != null) {
					job.walkTo = Workers.walkTarget(level, worker, following.pos());
					Workers.steer(worker, job.walkTo, following.pos());
				}
			}
			case SKIP -> job.skipHead();
			case LATER -> {
				// Try something else first. If nothing in the queue can be placed, give up on this one.
				if (job.deferrals > job.remaining() + 8) {
					job.skipHead();
					job.deferrals = 0;
				} else {
					job.deferHead();
				}
				job.nextActionTime = time + 5;
			}
		}
		return job.isFinished() ? Status.FINISHED : Status.WORKING;
	}

	/** The villager profession that best fits a kind of job. */
	static @Nullable ResourceKey<VillagerProfession> professionFor(BuildJob.Kind kind) {
		return switch (kind) {
			case FARM -> VillagerProfession.FARMER;
			case HOUSE, WALL, WELL, REPAIR -> VillagerProfession.MASON;
			case LAMPS -> VillagerProfession.TOOLSMITH;
			case ROAD -> VillagerProfession.CARTOGRAPHER;
		};
	}
}
