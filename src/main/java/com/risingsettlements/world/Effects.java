package com.risingsettlements.world;

import com.risingsettlements.config.RSConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/** Particles and sounds that show players a village is working. Sent from the server. */
public final class Effects {
	private Effects() {
	}

	/** A block was built or repaired by the village. */
	public static void blockPlaced(ServerLevel level, BlockPos pos, BlockState state) {
		if (!RSConfig.get().effects) {
			return;
		}
		SoundType sound = state.getSoundType();
		level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 4.0F, sound.getPitch() * 0.8F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0.0);
	}
}
