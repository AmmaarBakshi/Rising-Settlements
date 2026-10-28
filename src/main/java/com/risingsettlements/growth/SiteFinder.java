package com.risingsettlements.growth;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Building;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.VillageStyle;
import com.risingsettlements.world.BlockRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Looks for a free spot of land for a new building.
 *
 * <p>A spot is only used when all of this is true:
 * <ul>
 *     <li>it is inside the village's reach and every chunk around it is loaded;</li>
 *     <li>the ground is natural (grass, dirt, sand, snow, stone) and almost flat;</li>
 *     <li>the space above it is empty apart from plants and snow;</li>
 *     <li>there are no player blocks or village blocks on it or in a margin around it.</li>
 * </ul>
 * The margin keeps new buildings from squeezing against old houses or player builds.
 */
public final class SiteFinder {
	/** Returned by {@link #groundLevel} when the land can't be used. */
	public static final int NO_SITE = Integer.MIN_VALUE;

	/** Keep this far away from the bell, so the meeting place stays open. */
	private static final int BELL_CLEARANCE = 4;

	private SiteFinder() {
	}

	/**
	 * A spot for a template.
	 *
	 * @param origin   where the template's corner goes ({@code y} is the ground level)
	 * @param rotation how the template is turned so its door faces a road
	 * @param box      the space the building takes up
	 * @param entrance the ground spot in front of the door
	 */
	public record Site(BlockPos origin, Rotation rotation, BoundingBox box, BlockPos entrance) {
	}

	/**
	 * Tries a few random spots next to the village's roads, with the door facing the road.
	 *
	 * @param tolerance how uneven the ground may be (in blocks)
	 * @param margin    free border to keep around the building
	 */
	public static @Nullable Site findForTemplate(ServerLevel level, Settlement settlement, TemplateShape shape,
			List<BlockPos> roads, RandomSource random, int attempts, int tolerance, int margin) {
		BlockPos entranceLocal = shape.entrance();
		for (int i = 0; i < attempts; i++) {
			BlockPos road = pickRoad(settlement, roads, random);
			Direction away = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockPos entrance = road.relative(away, 2 + random.nextInt(3));
			Rotation rotation = facing(away.getOpposite());

			BlockPos origin = entrance.subtract(TemplateShape.rotate(entranceLocal, rotation)).atY(0);
			BoundingBox box = shape.box(origin, rotation);
			int ground = groundLevel(level, settlement, box, shape.size().getY() - 1, tolerance, margin);
			if (ground == NO_SITE) {
				continue;
			}
			// The doorstep must be at about the same height as the floor.
			int step = Terrain.surface(level, entrance.getX(), entrance.getZ());
			if (step == Terrain.UNKNOWN || Math.abs(step - ground) > 1) {
				continue;
			}
			return new Site(origin.atY(ground), rotation, box.moved(0, ground, 0), entrance.atY(step));
		}
		return null;
	}

	/** Picks a road block to build next to, preferring ones close to the bell. */
	private static BlockPos pickRoad(Settlement settlement, List<BlockPos> roads, RandomSource random) {
		if (roads.isEmpty()) {
			return settlement.bell();
		}
		BlockPos a = roads.get(random.nextInt(roads.size()));
		BlockPos b = roads.get(random.nextInt(roads.size()));
		BlockPos bell = settlement.bell();
		return a.distSqr(bell) <= b.distSqr(bell) ? a : b;
	}

	/** The rotation that turns a north-facing template so its front faces {@code front}. */
	public static Rotation facing(Direction front) {
		for (Rotation rotation : Rotation.values()) {
			if (rotation.rotate(Direction.NORTH) == front) {
				return rotation;
			}
		}
		return Rotation.NONE;
	}

	/**
	 * Checks a piece of land and picks the ground level to build at.
	 *
	 * @param area      the building's footprint (only x and z are used)
	 * @param height    how many blocks above the ground must be free
	 * @param tolerance how far the ground may be above or below the chosen level
	 * @param margin    free border to keep around the footprint
	 * @return the ground level, or {@link #NO_SITE}
	 */
	public static int groundLevel(ServerLevel level, Settlement settlement, BoundingBox area, int height, int tolerance, int margin) {
		BoundingBox outer = area.inflatedBy(margin, 0, margin);
		if (!isWithinReach(settlement, outer) || !Terrain.isLoaded(level, outer)) {
			return NO_SITE;
		}
		BlockPos bell = settlement.bell();
		if (outer.inflatedBy(BELL_CLEARANCE).intersects(bell.getX(), bell.getZ(), bell.getX(), bell.getZ())) {
			return NO_SITE;
		}
		for (Building building : settlement.growth().buildings()) {
			if (building.box().intersects(outer.minX(), outer.minZ(), outer.maxX(), outer.maxZ())) {
				return NO_SITE;
			}
		}

		// 1. The footprint: natural ground, not too uneven.
		int width = area.getXSpan();
		int depth = area.getZSpan();
		int[] surface = new int[width * depth];
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < depth; dz++) {
				int x = area.minX() + dx;
				int z = area.minZ() + dz;
				int y = Terrain.surface(level, x, z);
				if (y == Terrain.UNKNOWN) {
					return NO_SITE;
				}
				pos.set(x, y, z);
				if (!BlockRules.isGround(level.getBlockState(pos)) || isTaken(settlement, pos)) {
					return NO_SITE;
				}
				surface[dx * depth + dz] = y;
				min = Math.min(min, y);
				max = Math.max(max, y);
				if (max - min > tolerance) {
					return NO_SITE;
				}
			}
		}
		int ground = mostCommon(surface, min, max);

		// 2. The space above the ground level must be free (ground sticking up can be dug away).
		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < depth; dz++) {
				int x = area.minX() + dx;
				int z = area.minZ() + dz;
				int top = surface[dx * depth + dz];
				for (int y = Math.min(top, ground) + 1; y <= ground + height; y++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					boolean ok = y <= top ? BlockRules.isGround(state) : BlockRules.isClearable(state);
					if (!ok || isTaken(settlement, pos)) {
						return NO_SITE;
					}
				}
			}
		}

		// 3. The margin: nothing built by players or villagers, except roads at ground level.
		VillageStyle style = settlement.style();
		for (int x = outer.minX(); x <= outer.maxX(); x++) {
			for (int z = outer.minZ(); z <= outer.maxZ(); z++) {
				if (area.intersects(x, z, x, z)) {
					continue;
				}
				for (int y = ground - 1; y <= ground + height; y++) {
					pos.set(x, y, z);
					if (settlement.isPlayerBlock(pos)) {
						return NO_SITE;
					}
					BlockState state = level.getBlockState(pos);
					boolean road = style.isRoad(state) && y <= ground + 1;
					if (!road && !BlockRules.isNatural(state)) {
						return NO_SITE;
					}
				}
			}
		}
		return ground;
	}

	/** True if the whole box is close enough to the bell for player blocks to be tracked there. */
	private static boolean isWithinReach(Settlement settlement, BoundingBox box) {
		int reach = RSConfig.get().maxVillageRadius - 4;
		BlockPos bell = settlement.bell();
		int dx = Math.max(Math.abs(box.minX() - bell.getX()), Math.abs(box.maxX() - bell.getX()));
		int dz = Math.max(Math.abs(box.minZ() - bell.getZ()), Math.abs(box.maxZ() - bell.getZ()));
		return (long) dx * dx + (long) dz * dz <= (long) reach * reach;
	}

	private static boolean isTaken(Settlement settlement, BlockPos pos) {
		long key = pos.asLong();
		return settlement.isPlayerBlock(key) || settlement.blueprint().contains(key);
	}

	/** The height most of the footprint already has, so the least digging and filling is needed. */
	private static int mostCommon(int[] heights, int min, int max) {
		int[] counts = new int[max - min + 1];
		for (int height : heights) {
			counts[height - min]++;
		}
		int best = 0;
		for (int i = 1; i < counts.length; i++) {
			if (counts[i] > counts[best]) {
				best = i;
			}
		}
		return min + best;
	}
}
