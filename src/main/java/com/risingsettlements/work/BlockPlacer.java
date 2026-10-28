package com.risingsettlements.work;

import com.risingsettlements.village.Settlement;
import com.risingsettlements.world.MultiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/**
 * Actually changes the world for a {@link Placement}, with effects so players can see the
 * village at work: the villager swings its arm, the block appears with particles and
 * its normal placing sound, and a few green "happy villager" sparkles.
 *
 * <p>Particles and sounds are sent from the server, so every nearby player sees them
 * without any client code.
 */
public final class BlockPlacer {
	private BlockPlacer() {
	}

	public static void place(ServerLevel level, Settlement settlement, Placement placement, @Nullable Villager worker) {
		BlockPos pos = placement.pos();
		BlockState state = placement.state();

		if (placement.mode() == Placement.Mode.CLEAR || state.isAir()) {
			level.destroyBlock(pos, false);
			settlement.blueprint().remove(pos.asLong());
			swing(worker, pos);
			return;
		}

		BlockPos otherPos = MultiBlocks.otherPart(pos, state);
		if (otherPos != null) {
			// Doors and beds: place both halves together, exactly as recorded.
			BlockState otherState = otherHalf(state);
			level.setBlock(pos, state, Block.UPDATE_ALL);
			level.setBlock(otherPos, otherState, Block.UPDATE_ALL);
			settlement.blueprint().put(pos, state);
			settlement.blueprint().put(otherPos, otherState);
		} else {
			// Let fences, panes, walls and stairs connect to their new neighbours.
			BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
			if (shaped.isAir()) {
				shaped = state;
			}
			level.setBlock(pos, shaped, Block.UPDATE_ALL);
			settlement.blueprint().put(pos, shaped);
		}
		settlement.markDirty();
		effects(level, pos, state);
		swing(worker, pos);
	}

	private static BlockState otherHalf(BlockState state) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
			return state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, half == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
		}
		BedPart part = state.getValue(BlockStateProperties.BED_PART);
		return state.setValue(BlockStateProperties.BED_PART, part == BedPart.FOOT ? BedPart.HEAD : BedPart.FOOT);
	}

	private static void effects(ServerLevel level, BlockPos pos, BlockState state) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, 12, 0.3, 0.3, 0.3, 0.05);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.6, z, 3, 0.3, 0.2, 0.3, 0.0);
		SoundType sound = state.getSoundType();
		level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
	}

	private static void swing(@Nullable Villager worker, BlockPos pos) {
		if (worker != null) {
			worker.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			worker.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		}
	}
}
