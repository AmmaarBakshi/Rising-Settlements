package com.risingsettlements.repair;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementRuntime;
import com.risingsettlements.work.BuildJob;
import com.risingsettlements.work.JobRunner;
import com.risingsettlements.work.Placement;
import com.risingsettlements.work.Workers;
import com.risingsettlements.world.MultiBlocks;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns damage into repair work.
 *
 * <p>Once a block has been missing for {@code repairDelayTicks} (and no raid is going on),
 * it is added to a repair job. Repairs are done bottom-up, so walls go back up before
 * roofs and torches, and closest to the bell first.
 */
public final class RepairPlanner {
	/** Blocks per repair job. After a job, the next one is planned with fresh damage info. */
	private static final int MAX_BATCH = 48;

	private RepairPlanner() {
	}

	public static void tick(ServerLevel level, Settlement settlement, long time) {
		RSConfig config = RSConfig.get();
		if (!config.repairsEnabled || !settlement.isSurveyed()) {
			return;
		}
		SettlementRuntime runtime = settlement.runtime();
		runtime.damage.tick(level, settlement, config.damageScanBlocksPerTick, time);

		// Villagers hide during raids; they rebuild once it is over.
		if (level.isRaided(settlement.bell())) {
			return;
		}

		if (runtime.repairJob == null) {
			if (time % 20 == 0) {
				runtime.repairJob = plan(settlement, time, config.repairDelayTicks);
			}
			return;
		}

		BuildJob job = runtime.repairJob;
		if (JobRunner.tick(level, settlement, job, time, config.repairIntervalTicks) == JobRunner.Status.FINISHED) {
			Workers.release(level, job.workerId());
			settlement.addBlocksRepaired(job.done());
			runtime.repairJob = null;
		}
	}

	private static @Nullable BuildJob plan(Settlement settlement, long time, int delay) {
		Long2LongMap missing = settlement.runtime().damage.missingSince();
		if (missing.isEmpty()) {
			return null;
		}

		List<BlockPos> ready = new ArrayList<>();
		for (Long2LongMap.Entry entry : missing.long2LongEntrySet()) {
			if (time - entry.getLongValue() < delay) {
				continue;
			}
			BlockState state = settlement.blueprint().get(entry.getLongKey());
			// Doors and beds are rebuilt from their lower half / foot, which places both halves.
			if (state != null && MultiBlocks.isPrimaryPart(state)) {
				ready.add(BlockPos.of(entry.getLongKey()));
			}
		}
		if (ready.isEmpty()) {
			return null;
		}

		BlockPos bell = settlement.bell();
		ready.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(pos -> pos.distSqr(bell)));

		List<Placement> placements = new ArrayList<>();
		for (BlockPos pos : ready.subList(0, Math.min(MAX_BATCH, ready.size()))) {
			BlockState state = settlement.blueprint().get(pos.asLong());
			if (state != null) {
				placements.add(new Placement(pos, state, Placement.Mode.FILL));
				missing.remove(pos.asLong());
			}
		}
		return placements.isEmpty() ? null : new BuildJob(BuildJob.Kind.REPAIR, placements);
	}

	/** Makes all known damage ready to repair right away (used by {@code /settlement repair}). */
	public static int hurry(Settlement settlement) {
		Long2LongMap missing = settlement.runtime().damage.missingSince();
		for (Long2LongMap.Entry entry : missing.long2LongEntrySet()) {
			entry.setValue(Long.MIN_VALUE / 2);
		}
		return missing.size();
	}
}
