package com.risingsettlements.build;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Designs a small house in the village's style, next to one of its streets.
 *
 * <p>Houses are a few blocks wide and deep, with log (or sandstone) corner posts, a
 * window in each side, a pitched or flat roof, a bed and a job site block inside, and a
 * front door facing the street. A path is laid from the door to the street.
 *
 * <p>The design is worked out in a local frame, where the front wall is at {@code z = 0},
 * the door faces "north" (towards negative z) and {@code x} runs along the front. It is
 * then turned to face the street.
 */
public final class HousePlanner {
	/** How many street blocks to try building next to. */
	private static final int ANCHORS = 24;
	private static final int WALL_HEIGHT = 3;

	private HousePlanner() {
	}

	static @Nullable BuildPlan plan(ServerLevel level, Settlement settlement, List<BlockPos> roads, RandomSource random) {
		StylePalette palette = StylePalette.of(settlement.style());
		int width = 5 + random.nextInt(3);
		int depth = 5 + random.nextInt(2);
		int roofHeight = palette.hasFlatRoof() ? 1 : depth / 2 + 1;

		boolean nearRoad = !roads.isEmpty();
		for (BlockPos anchor : RoadPlanner.anchors(settlement, roads, ANCHORS, random)) {
			for (Direction side : Util.shuffledCopy(Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new), random)) {
				for (int gap : nearRoad ? new int[]{2, 3} : new int[]{6, 9, 12}) {
					for (int shift : new int[]{0, -2, 2}) {
						BlockPos door = anchor.relative(side, gap).relative(side.getClockWise(), shift);
						Frame frame = new Frame(door, side.getOpposite(), width);
						BoundingBox box = frame.footprint(width, depth);
						SiteFinder.Site site = SiteFinder.check(level, settlement, box.minX(), box.minZ(), box.getXSpan(),
								box.getZSpan(), 1, WALL_HEIGHT + roofHeight + 1);
						if (site != null) {
							return build(level, settlement, frame, site, palette, width, depth, roofHeight, roads, random);
						}
					}
				}
			}
		}
		return null;
	}

	private static BuildPlan build(ServerLevel level, Settlement settlement, Frame f, SiteFinder.Site site, StylePalette palette,
								   int width, int depth, int roofHeight, List<BlockPos> roads, RandomSource random) {
		BuildPlan plan = new BuildPlan(BuildPlan.Kind.HOUSE);
		int floorY = site.maxGround() + 1;
		int roofY = floorY + WALL_HEIGHT;
		int doorX = width / 2;

		// Clear plants and snow from the whole volume.
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				for (int y = floorY; y <= roofY + roofHeight; y++) {
					plan.clear(f.at(x, y, z));
				}
			}
		}

		// Foundation: fill any dip under the house, then a ring of foundation blocks around the floor.
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				BlockPos column = f.at(x, 0, z);
				int ground = SiteFinder.groundY(level, column.getX(), column.getZ());
				for (int y = ground + 1; y < floorY - 1; y++) {
					plan.add(f.at(x, y, z), palette.foundation().defaultBlockState());
				}
				Block block = isEdge(x, z, width, depth) ? palette.foundation() : palette.floor();
				plan.add(f.at(x, floorY - 1, z), block.defaultBlockState(), BuildPlan.REPLACE_GROUND);
			}
		}

		// Walls, with gaps for the door and windows.
		BlockState corner = upright(palette.corner());
		for (int y = floorY; y < roofY; y++) {
			for (int x = 0; x < width; x++) {
				for (int z = 0; z < depth; z++) {
					if (!isEdge(x, z, width, depth) || (x == doorX && z == 0 && y < floorY + 2)
							|| isWindow(x, y - floorY, z, width, depth)) {
						continue;
					}
					boolean isCorner = (x == 0 || x == width - 1) && (z == 0 || z == depth - 1);
					plan.add(f.at(x, y, z), isCorner ? corner : palette.wall().defaultBlockState());
				}
			}
		}

		addRoof(plan, f, palette, width, depth, roofY);

		// Windows go in after the walls, so the panes can connect to them.
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				if (isWindow(x, 1, z, width, depth)) {
					plan.add(f.at(x, floorY + 1, z), palette.window().defaultBlockState());
				}
			}
		}

		// Furniture: a bed against the back wall, a job site block and a torch.
		Direction back = f.dir(Direction.SOUTH);
		BlockState bed = palette.bed().defaultBlockState().setValue(BedBlock.FACING, back);
		plan.add(f.at(1, floorY, depth - 3), bed.setValue(BedBlock.PART, BedPart.FOOT));
		plan.add(f.at(1, floorY, depth - 2), bed.setValue(BedBlock.PART, BedPart.HEAD));
		Block workstation = StylePalette.WORKSTATIONS.get(random.nextInt(StylePalette.WORKSTATIONS.size()));
		plan.add(f.at(width - 2, floorY, depth - 2), workstation.defaultBlockState());
		plan.add(f.at(width - 2, floorY, 1), Blocks.TORCH.defaultBlockState());

		// The door, both halves one after the other.
		BlockState door = palette.door().defaultBlockState().setValue(DoorBlock.FACING, back);
		plan.add(f.at(doorX, floorY, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		plan.add(f.at(doorX, floorY + 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

		if (RSConfig.get().enableRoads) {
			BoundingBox house = f.footprint(width, depth);
			RoadPlanner.connect(plan, level, settlement, f.at(doorX, 0, -1), roads, house, 24);
		}
		return plan;
	}

	private static void addRoof(BuildPlan plan, Frame f, StylePalette palette, int width, int depth, int roofY) {
		if (palette.hasFlatRoof()) {
			for (int x = 0; x < width; x++) {
				for (int z = 0; z < depth; z++) {
					plan.add(f.at(x, roofY, z), palette.flatRoof().defaultBlockState());
				}
			}
			return;
		}
		// A pitched roof whose ridge runs parallel to the front. Each layer steps in by one
		// block from the front and the back; the gable ends are filled with wall blocks.
		BlockState front = palette.stairs().defaultBlockState().setValue(StairBlock.FACING, f.dir(Direction.SOUTH));
		BlockState rear = palette.stairs().defaultBlockState().setValue(StairBlock.FACING, f.dir(Direction.NORTH));
		for (int layer = 0; ; layer++) {
			int zFront = layer;
			int zBack = depth - 1 - layer;
			int y = roofY + layer;
			if (zFront > zBack) {
				break;
			}
			for (int x = 0; x < width; x++) {
				if (zFront == zBack) {
					plan.add(f.at(x, y, zFront), palette.slab().defaultBlockState());
				} else {
					plan.add(f.at(x, y, zFront), front);
					plan.add(f.at(x, y, zBack), rear);
				}
			}
			for (int z = zFront + 1; z < zBack; z++) {
				plan.add(f.at(0, y, z), palette.wall().defaultBlockState());
				plan.add(f.at(width - 1, y, z), palette.wall().defaultBlockState());
			}
		}
	}

	private static boolean isEdge(int x, int z, int width, int depth) {
		return x == 0 || z == 0 || x == width - 1 || z == depth - 1;
	}

	/** One window in the middle of each side wall and of the back wall, at eye height. */
	private static boolean isWindow(int x, int height, int z, int width, int depth) {
		if (height != 1) {
			return false;
		}
		boolean sideWall = (x == 0 || x == width - 1) && z == depth / 2;
		boolean backWall = z == depth - 1 && x == width / 2;
		return sideWall || backWall;
	}

	private static BlockState upright(Block block) {
		BlockState state = block.defaultBlockState();
		return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y) : state;
	}

	/**
	 * Turns local house coordinates into world positions. Local north (negative z) is the
	 * direction the front of the house faces; local east (positive x) is to its right.
	 */
	private record Frame(BlockPos origin, Direction front, Direction right, Direction back) {
		Frame(BlockPos door, Direction front, int width) {
			this(door.relative(front.getClockWise(), -(width / 2)), front, front.getClockWise(), front.getOpposite());
		}

		BlockPos at(int x, int y, int z) {
			BlockPos pos = origin.relative(right, x).relative(back, z);
			return new BlockPos(pos.getX(), y, pos.getZ());
		}

		/** Turns a direction in the local frame into a world direction. */
		Direction dir(Direction local) {
			return switch (local) {
				case NORTH -> front;
				case SOUTH -> back;
				case EAST -> right;
				case WEST -> right.getOpposite();
				default -> local;
			};
		}

		BoundingBox footprint(int width, int depth) {
			return BoundingBox.fromCorners(at(0, 0, 0), at(width - 1, 0, depth - 1));
		}
	}
}
