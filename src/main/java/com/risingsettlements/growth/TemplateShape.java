package com.risingsettlements.growth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;

/**
 * A structure template reduced to what the mod needs: its size and its blocks.
 *
 * <p>Template conventions (see {@code tools/TemplateGenerator.java}): {@code y = 0} is the
 * floor and sits at ground level, and the front door is on the {@code z = 0} side. So an
 * unrotated template faces north, and the spot right in front of the door is
 * {@code (doorX, 0, -1)}.
 *
 * @param id     template id, e.g. {@code rising_settlements:house/plains/small}
 * @param size   template size before rotation
 * @param blocks every stored block, including air (air marks space that must be cleared)
 */
public record TemplateShape(Identifier id, Vec3i size, List<Block> blocks) {
	/** One block of the template, at a position relative to the template's corner. */
	public record Block(BlockPos pos, BlockState state) {
	}

	/** The ground spot in front of the door, relative to the template. */
	public BlockPos entrance() {
		int doorX = size.getX() / 2;
		int doorZ = 0;
		for (Block block : blocks) {
			BlockState state = block.state();
			if (state.getBlock() instanceof DoorBlock
					&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
					&& block.pos().getZ() <= doorZ) {
				doorX = block.pos().getX();
				doorZ = block.pos().getZ();
			}
		}
		return new BlockPos(doorX, 0, doorZ - 1);
	}

	/** Where a template position ends up after rotating around the template's corner. */
	public static BlockPos rotate(BlockPos local, Rotation rotation) {
		return StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO);
	}

	/** The space the template takes up when placed at {@code origin} with {@code rotation}. */
	public BoundingBox box(BlockPos origin, Rotation rotation) {
		BlockPos far = new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
		return BoundingBox.fromCorners(origin.offset(rotate(BlockPos.ZERO, rotation)), origin.offset(rotate(far, rotation)));
	}
}
