package com.risingsettlements.growth;

import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.VillageStyle;
import com.risingsettlements.work.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans new fields: a 9 x 7 rectangle with a log border, a line of water down the middle
 * and farmland planted with the crops the village's biome usually grows. Every farmland
 * block is within two blocks of the water, so the whole field stays wet.
 *
 * <p>New fields are placed near existing farmland when there is some, so farms stay
 * together, and otherwise near the village's roads.
 */
public final class FarmPlanner {
	private static final int LONG_SIDE = 9;
	private static final int SHORT_SIDE = 7;
	/** How far from an existing field or road a new one may start. */
	private static final int SPREAD = 14;

	private FarmPlanner() {
	}

	/** A planned field and the area it covers. */
	public record Plan(List<Placement> placements, BoundingBox area) {
	}

	public static @Nullable Plan plan(ServerLevel level, Settlement settlement, List<BlockPos> roads, RandomSource random, int attempts) {
		List<BlockPos> seeds = settlement.blueprint().find(state -> state.is(Blocks.FARMLAND));
		if (seeds.isEmpty()) {
			seeds = roads;
		}
		if (seeds.isEmpty()) {
			seeds = List.of(settlement.bell());
		}

		for (int i = 0; i < attempts; i++) {
			BlockPos seed = seeds.get(random.nextInt(seeds.size()));
			boolean alongX = random.nextBoolean();
			int width = alongX ? LONG_SIDE : SHORT_SIDE;
			int depth = alongX ? SHORT_SIDE : LONG_SIDE;
			int minX = seed.getX() + random.nextIntBetweenInclusive(-SPREAD, SPREAD) - width / 2;
			int minZ = seed.getZ() + random.nextIntBetweenInclusive(-SPREAD, SPREAD) - depth / 2;
			BoundingBox area = new BoundingBox(minX, 0, minZ, minX + width - 1, 0, minZ + depth - 1);

			int ground = SiteFinder.groundLevel(level, settlement, area, 2, 1, 1);
			if (ground != SiteFinder.NO_SITE) {
				return new Plan(placements(level, settlement.style(), area, ground, alongX, random),
						new BoundingBox(area.minX(), ground, area.minZ(), area.maxX(), ground + 1, area.maxZ()));
			}
		}
		return null;
	}

	private static List<Placement> placements(ServerLevel level, VillageStyle style, BoundingBox area, int ground,
			boolean alongX, RandomSource random) {
		List<Placement> clear = new ArrayList<>();
		List<Placement> dig = new ArrayList<>();
		List<Placement> border = new ArrayList<>();
		List<Placement> soil = new ArrayList<>();
		List<Placement> water = new ArrayList<>();
		List<Placement> crops = new ArrayList<>();

		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE);
		List<Block> cropTypes = style.crops();
		int centerX = (area.minX() + area.maxX()) / 2;
		int centerZ = (area.minZ() + area.maxZ()) / 2;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = area.minX(); x <= area.maxX(); x++) {
			for (int z = area.minZ(); z <= area.maxZ(); z++) {
				int top = Terrain.surface(level, x, z);
				// Plants on top go first, then ground sticking up above the field level.
				pos.set(x, Math.max(top, ground) + 1, z);
				if (!level.getBlockState(pos).isAir()) {
					clear.add(new Placement(pos.immutable(), air, Placement.Mode.CLEAR));
				}
				if (top > ground) {
					dig.add(new Placement(new BlockPos(x, top, z), air, Placement.Mode.REPLACE_GROUND));
				}

				BlockPos field = new BlockPos(x, ground, z);
				boolean edgeX = x == area.minX() || x == area.maxX();
				boolean edgeZ = z == area.minZ() || z == area.maxZ();
				if (edgeX || edgeZ) {
					Direction.Axis axis = edgeX && edgeZ ? Direction.Axis.Y : edgeZ ? Direction.Axis.X : Direction.Axis.Z;
					border.add(new Placement(field, style.farmBorder(axis), Placement.Mode.REPLACE_GROUND));
				} else if (alongX ? z == centerZ : x == centerX) {
					water.add(new Placement(field, Blocks.WATER.defaultBlockState(), Placement.Mode.REPLACE_GROUND));
				} else {
					soil.add(new Placement(field, farmland, Placement.Mode.REPLACE_GROUND));
					Block crop = cropTypes.get(random.nextInt(cropTypes.size()));
					crops.add(new Placement(field.above(), crop.defaultBlockState(), Placement.Mode.FILL));
				}
			}
		}

		List<Placement> all = new ArrayList<>();
		all.addAll(clear);
		all.addAll(dig);
		all.addAll(border);
		all.addAll(soil);
		all.addAll(water);
		all.addAll(crops);
		return all;
	}
}
