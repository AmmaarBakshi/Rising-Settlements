package com.risingsettlements.repair;

import com.risingsettlements.village.Settlement;
import com.risingsettlements.world.BlockRules;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds missing village blocks without causing lag.
 *
 * <p>Instead of checking the whole village at once, it walks through the blueprint a
 * small number of positions per tick and starts over when it reaches the end. A village
 * with a few thousand blocks is fully checked every few seconds. Unloaded chunks are skipped.
 *
 * <p>For every missing block it remembers <i>when</i> it was first seen missing, so
 * villagers can wait a little before they start repairing (it would look odd if a house
 * rebuilt itself the moment a creeper exploded).
 */
public final class DamageScanner {
	private long[] snapshot = new long[0];
	private int cursor;
	/** Packed position -> game time it was first seen missing. */
	private final Long2LongOpenHashMap missingSince = new Long2LongOpenHashMap();

	public void tick(ServerLevel level, Settlement settlement, int budget, long time) {
		if (cursor >= snapshot.length) {
			snapshot = settlement.blueprint().snapshotPositions();
			cursor = 0;
			// Forget positions that are no longer part of the village (e.g. a player broke them).
			missingSince.keySet().removeIf(pos -> !settlement.blueprint().contains(pos));
		}

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int end = Math.min(snapshot.length, cursor + budget);
		for (; cursor < end; cursor++) {
			long packed = snapshot[cursor];
			BlockState expected = settlement.blueprint().get(packed);
			if (expected == null) {
				continue;
			}
			pos.set(packed);
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (isMissing(level.getBlockState(pos), expected) && !settlement.isPlayerBlock(packed)) {
				missingSince.putIfAbsent(packed, time);
			} else {
				missingSince.remove(packed);
			}
		}
	}

	/** A block counts as missing when its spot is empty, burning, or overgrown with loose plants. */
	static boolean isMissing(BlockState current, BlockState expected) {
		return !current.is(expected.getBlock()) && BlockRules.isClearable(current);
	}

	public Long2LongMap missingSince() {
		return missingSince;
	}

	public int missingCount() {
		return missingSince.size();
	}
}
