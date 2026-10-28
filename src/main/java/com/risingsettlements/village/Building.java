package com.risingsettlements.village;

import com.risingsettlements.work.BuildJob;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Something the village built: a house, farm, well, lamp post or wall.
 *
 * @param kind      what it is
 * @param box       the space it takes up; later projects keep their distance
 * @param entrance  the ground spot in front of its door, where a road should end
 * @param connected true once a road leads to it
 */
public record Building(BuildJob.Kind kind, BoundingBox box, BlockPos entrance, boolean connected) {
	public Building connect() {
		return new Building(kind, box, entrance, true);
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("kind", kind.id());
		tag.store("box", BoundingBox.CODEC, box);
		tag.store("entrance", BlockPos.CODEC, entrance);
		tag.putBoolean("connected", connected);
		return tag;
	}

	static Building load(CompoundTag tag) {
		BlockPos entrance = tag.read("entrance", BlockPos.CODEC).orElse(BlockPos.ZERO);
		return new Building(
				BuildJob.Kind.byId(tag.getStringOr("kind", "house")),
				tag.read("box", BoundingBox.CODEC).orElseGet(() -> new BoundingBox(entrance)),
				entrance,
				tag.getBooleanOr("connected", false));
	}
}
