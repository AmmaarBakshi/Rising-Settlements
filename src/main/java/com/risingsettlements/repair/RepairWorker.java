package com.risingsettlements.repair;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.WorkerVillagers;
import com.risingsettlements.world.BlockRules;
import com.risingsettlements.world.Placement;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds missing village blocks and has villagers put them back.
 *
 * <p>Every so often the whole blueprint is checked against the world, a slice of it per
 * tick. A block counts as damaged only if its position is now empty or holds loose
 * natural stuff (air, fire, grass, snow): a position that holds some other block is left
 * alone, because the mod never replaces a block it did not place. Damaged blocks are then
 * rebuilt from the bottom up, one every few ticks, while the village has villagers and no
 * raid is going on.
 */
public final class RepairWorker {
	private long[] scan;
	private int scanIndex;
	private long nextScanAt;
	private final LongArrayList queue = new LongArrayList();

	/** Damaged blocks waiting to be rebuilt. */
	public int pending() {
		return queue.size();
	}

	/** Looks for damage again straight away. */
	public void requestScan() {
		nextScanAt = 0;
	}

	public void tick(ServerLevel level, Settlement settlement) {
		RSConfig config = RSConfig.get();
		if (!config.enableRepairs || !settlement.isSurveyed()) {
			return;
		}
		long time = level.getGameTime();
		if (scan == null && queue.isEmpty() && time >= nextScanAt) {
			scan = settlement.blueprint().snapshotPositions();
			scanIndex = 0;
		}
		if (scan != null) {
			scanStep(level, settlement, config.repairScanBlocksPerTick);
			if (scanIndex >= scan.length) {
				scan = null;
				nextScanAt = time + config.repairScanIntervalTicks;
				// Bottom first, so walls go back on top of their floors.
				queue.sort((a, b) -> Integer.compare(BlockPos.getY(a), BlockPos.getY(b)));
			}
			return;
		}
		if (!queue.isEmpty() && time % config.repairIntervalTicks == 0
				&& settlement.villagers() > 0 && !level.isRaided(settlement.bell())) {
			repairStep(level, settlement, config.repairBlocksPerStep);
		}
	}

	private void scanStep(ServerLevel level, Settlement settlement, int budget) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int end = Math.min(scan.length, scanIndex + budget);
		for (; scanIndex < end; scanIndex++) {
			long packed = scan[scanIndex];
			pos.set(packed);
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState wanted = settlement.blueprint().get(packed);
			if (wanted != null && !settlement.isPlayerBlock(packed) && needsRepair(level.getBlockState(pos), wanted)) {
				queue.add(packed);
			}
		}
	}

	private void repairStep(ServerLevel level, Settlement settlement, int budget) {
		while (budget > 0 && !queue.isEmpty()) {
			long packed = queue.removeLong(0);
			BlockPos pos = BlockPos.of(packed);
			BlockState wanted = settlement.blueprint().get(packed);
			// Check again: things may have changed since the scan.
			if (wanted == null || settlement.isPlayerBlock(packed) || !level.isLoaded(pos)
					|| !needsRepair(level.getBlockState(pos), wanted)) {
				continue;
			}
			if (Placement.blockedByEntity(level, pos, wanted)) {
				continue; // Someone is standing there. The next scan will find it again.
			}
			Placement.place(level, pos, wanted);
			WorkerVillagers.work(level, settlement, pos, Items.IRON_PICKAXE);
			budget--;
		}
	}

	static boolean needsRepair(BlockState current, BlockState wanted) {
		if (current == wanted) {
			return false;
		}
		return BlockRules.isClearable(current) || BlockRules.isWornDown(current, wanted);
	}
}
