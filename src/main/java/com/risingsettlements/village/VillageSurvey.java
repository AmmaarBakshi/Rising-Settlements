package com.risingsettlements.village;

import com.risingsettlements.RisingSettlements;
import com.risingsettlements.world.BlockRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Records the blocks of a newly found village into its {@link Blueprint}.
 *
 * <p>Only the bounding boxes of the village's structure pieces (houses, streets, farms,
 * the meeting point) are examined, so terrain around the village is never mistaken for
 * village blocks. The work is spread over many ticks, and a piece is only examined once
 * all chunks it touches are loaded; until then it waits.
 */
public final class VillageSurvey {
	private final ArrayDeque<BoundingBox> pending = new ArrayDeque<>();
	private final List<BoundingBox> waiting = new ArrayList<>();
	private BoundingBox current;
	private int x;
	private int y;
	private int z;

	private VillageSurvey(List<BoundingBox> boxes) {
		pending.addAll(boxes);
	}

	/** Starts a survey for a settlement, using the village structure at its bell. */
	static VillageSurvey start(ServerLevel level, Settlement settlement) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(settlement.bell(), StructureTags.VILLAGE);
		List<BoundingBox> boxes = start.isValid()
				? start.getPieces().stream().map(StructurePiece::getBoundingBox).toList()
				: List.of();
		return new VillageSurvey(boxes);
	}

	/**
	 * Examines up to {@code budget} positions.
	 *
	 * @return true when the whole village has been recorded
	 */
	boolean tick(ServerLevel level, Settlement settlement, int budget) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (budget > 0) {
			if (current == null && !nextBox(level)) {
				// Everything left is in unloaded chunks: try those pieces again later.
				pending.addAll(waiting);
				waiting.clear();
				return pending.isEmpty();
			}

			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (BlockRules.isVillageBlock(state) && !settlement.isPlayerBlock(pos)) {
				settlement.blueprint().put(pos, state);
			}
			budget--;
			advance();
		}
		return false;
	}

	/** Picks the next piece whose chunks are all loaded. */
	private boolean nextBox(ServerLevel level) {
		while (!pending.isEmpty()) {
			BoundingBox box = pending.poll();
			if (isLoaded(level, box)) {
				current = box;
				x = box.minX();
				y = box.minY();
				z = box.minZ();
				return true;
			}
			waiting.add(box);
		}
		return false;
	}

	private static boolean isLoaded(ServerLevel level, BoundingBox box) {
		return box.intersectingChunks().allMatch((ChunkPos chunk) -> level.hasChunk(chunk.x(), chunk.z()));
	}

	/** Moves to the next position in the current box: x, then z, then y. */
	private void advance() {
		if (++x > current.maxX()) {
			x = current.minX();
			if (++z > current.maxZ()) {
				z = current.minZ();
				if (++y > current.maxY()) {
					current = null;
				}
			}
		}
	}

	/** Runs the survey for a settlement and marks it as surveyed when done. */
	public static void tickSettlement(ServerLevel level, Settlement settlement, int budget) {
		if (settlement.isSurveyed()) {
			return;
		}
		SettlementRuntime runtime = settlement.runtime();
		if (runtime.survey == null) {
			runtime.survey = start(level, settlement);
		}
		if (runtime.survey.tick(level, settlement, budget)) {
			runtime.survey = null;
			settlement.setSurveyed(true);
			RisingSettlements.LOGGER.info("Surveyed {}: recorded {} village blocks", settlement.name(), settlement.blueprint().size());
		} else {
			settlement.markDirty();
		}
	}
}
