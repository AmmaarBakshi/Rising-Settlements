package com.risingsettlements.build;

import com.risingsettlements.RisingSettlements;
import com.risingsettlements.config.RSConfig;
import com.risingsettlements.util.Texts;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.WorkerVillagers;
import com.risingsettlements.world.BlockRules;
import com.risingsettlements.world.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places the settlement's active {@link BuildPlan}, a few blocks at a time.
 *
 * <p>Every step is checked against the world right before it happens. A position that a
 * player has touched, or that now holds something other than empty space (or natural
 * ground, where the plan allows it), is skipped. If a chunk is unloaded or someone is
 * standing in the way, the work simply waits.
 */
public final class ConstructionWorker {
	/** Steps that place nothing (clearing air, blocks already there) are cheap, so several may run per tick. */
	private static final int MAX_STEPS_PER_TICK = 64;

	private ConstructionWorker() {
	}

	public static void tick(ServerLevel level, Settlement settlement) {
		BuildPlan plan = settlement.activePlan();
		RSConfig config = RSConfig.get();
		if (plan == null || level.getGameTime() % config.buildIntervalTicks != 0 || settlement.villagers() <= 0) {
			return;
		}

		int budget = config.buildBlocksPerStep;
		int steps = 0;
		while (budget > 0 && steps++ < MAX_STEPS_PER_TICK && plan.hasNext()) {
			long packed = plan.nextPos();
			BlockPos pos = BlockPos.of(packed);
			if (!level.isLoaded(pos)) {
				return;
			}
			BlockState wanted = plan.nextState();
			int flags = plan.nextFlags();
			BlockState current = level.getBlockState(pos);

			if (settlement.isPlayerBlock(packed)) {
				plan.advance();
				continue;
			}
			if (wanted.isAir()) {
				if (!current.isAir() && BlockRules.isClearable(current) && !settlement.blueprint().contains(packed)) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Placement.FLAGS);
				}
				plan.advance();
				continue;
			}
			if (current == wanted) {
				record(settlement, pos, wanted, flags);
				plan.advance();
				continue;
			}
			if (!canBuildOver(settlement, packed, current, flags)) {
				plan.advance();
				continue;
			}
			if (Placement.blockedByEntity(level, pos, wanted)) {
				return;
			}

			BlockState placed = connectsToNeighbours(wanted) ? Block.updateFromNeighbourShapes(wanted, level, pos) : wanted;
			Placement.place(level, pos, placed);
			record(settlement, pos, placed, flags);
			WorkerVillagers.work(level, settlement, pos, toolFor(plan.kind()));
			plan.advance();
			budget--;
		}
		settlement.markDirty();

		if (!plan.hasNext()) {
			finish(level, settlement, plan);
		}
	}

	private static boolean canBuildOver(Settlement settlement, long packed, BlockState current, int flags) {
		if (settlement.blueprint().contains(packed)) {
			return false; // Never replace another village block.
		}
		return BlockRules.isClearable(current)
				|| ((flags & BuildPlan.REPLACE_GROUND) != 0 && BlockRules.isGround(current));
	}

	private static void record(Settlement settlement, BlockPos pos, BlockState state, int flags) {
		if ((flags & BuildPlan.NO_RECORD) == 0) {
			settlement.blueprint().put(pos, state);
		}
	}

	/** Glass panes and fences need to know their neighbours to connect to them. */
	private static boolean connectsToNeighbours(BlockState state) {
		return state.getBlock() instanceof CrossCollisionBlock;
	}

	private static Item toolFor(BuildPlan.Kind kind) {
		return switch (kind) {
			case HOUSE -> Items.IRON_AXE;
			case FARM -> Items.IRON_HOE;
			case ROAD -> Items.IRON_SHOVEL;
		};
	}

	private static void finish(ServerLevel level, Settlement settlement, BuildPlan plan) {
		RSConfig config = RSConfig.get();
		settlement.expandToInclude(plan.area(), config.villageMargin, config.maxVillageRadius);
		settlement.recordBuilt(plan.kind());
		settlement.setActivePlan(null);
		settlement.setLastGrowthAt(level.getGameTime());
		RisingSettlements.LOGGER.info("{} finished a new {}", settlement.name(), plan.kind().id());
		GrowthPlanner.tellMayor(level, settlement, Texts.tr("growth.finished", settlement.name(), Texts.tr("project." + plan.kind().id())));
	}
}
