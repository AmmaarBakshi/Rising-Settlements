package com.risingsettlements.growth;

import com.risingsettlements.work.Placement;
import com.risingsettlements.world.BlockRules;
import com.risingsettlements.world.MultiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns a template placed at a site into an ordered list of block placements, in the
 * order a builder would work:
 * <ol>
 *     <li>clear plants and dig away ground inside the building, top to bottom;</li>
 *     <li>fill dips under the floor with foundation blocks;</li>
 *     <li>lay the floor at ground level;</li>
 *     <li>build walls, doors, beds and the roof, bottom to top;</li>
 *     <li>finish with lanterns and water, which need the rest to be in place.</li>
 * </ol>
 */
public final class TemplatePlanner {
	/** How deep foundation blocks may go to fill a dip under the floor. */
	private static final int MAX_FOUNDATION_DEPTH = 4;

	private TemplatePlanner() {
	}

	public static List<Placement> plan(ServerLevel level, TemplateShape shape, SiteFinder.Site site, BlockState foundation) {
		List<Placement> dig = new ArrayList<>();
		List<Placement> base = new ArrayList<>();
		List<Placement> floor = new ArrayList<>();
		List<Placement> body = new ArrayList<>();
		List<Placement> finish = new ArrayList<>();
		BlockState air = Blocks.AIR.defaultBlockState();
		Rotation rotation = site.rotation();

		for (TemplateShape.Block block : shape.blocks()) {
			BlockPos pos = site.origin().offset(TemplateShape.rotate(block.pos(), rotation));
			BlockState state = block.state().rotate(rotation);

			if (block.pos().getY() == 0) {
				// Floor layer: replaces the ground, never digs holes in it.
				if (state.isAir()) {
					continue;
				}
				addFoundation(level, pos, foundation, base);
				(isFinishing(state) ? finish : floor).add(new Placement(pos, state, Placement.Mode.REPLACE_GROUND));
				continue;
			}

			// Above the floor: anything in the way that isn't a loose plant has to be dug out first.
			BlockState current = level.getBlockState(pos);
			if (!current.isAir() && (state.isAir() || !BlockRules.isClearable(current))) {
				dig.add(new Placement(pos, air, Placement.Mode.REPLACE_GROUND));
			}
			// Doors and beds are placed from one half, which places both.
			if (!state.isAir() && MultiBlocks.isPrimaryPart(state)) {
				(isFinishing(state) ? finish : body).add(new Placement(pos, state, Placement.Mode.FILL));
			}
		}

		Comparator<Placement> bottomUp = Comparator.comparingInt(p -> p.pos().getY());
		dig.sort(bottomUp.reversed());
		body.sort(bottomUp);

		List<Placement> all = new ArrayList<>(dig.size() + base.size() + floor.size() + body.size() + finish.size());
		all.addAll(dig);
		all.addAll(base);
		all.addAll(floor);
		all.addAll(body);
		all.addAll(finish);
		return all;
	}

	/** Fills empty space below a floor block, bottom-up, so the building never floats. */
	private static void addFoundation(ServerLevel level, BlockPos floorPos, BlockState foundation, List<Placement> out) {
		List<Placement> column = new ArrayList<>();
		BlockPos.MutableBlockPos pos = floorPos.mutable();
		for (int i = 0; i < MAX_FOUNDATION_DEPTH; i++) {
			pos.move(0, -1, 0);
			if (!BlockRules.isClearable(level.getBlockState(pos))) {
				break;
			}
			column.add(new Placement(pos.immutable(), foundation, Placement.Mode.FILL));
		}
		out.addAll(column.reversed());
	}

	/** Lanterns hang from the roof and water needs its walls, so they go last. */
	private static boolean isFinishing(BlockState state) {
		return state.getBlock() instanceof LanternBlock || !state.getFluidState().isEmpty();
	}
}
