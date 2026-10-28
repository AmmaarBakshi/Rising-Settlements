package com.risingsettlements.village;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The blocks that belong to a village: everything that was part of the original village
 * when it was found, plus everything the mod has built since.
 *
 * <p>This is the only set of blocks the mod is ever allowed to repair. Positions are
 * stored as packed longs ({@link BlockPos#asLong()}) to keep memory use low, and saved
 * with a small palette so a village with thousands of blocks stays compact on disk.
 */
public final class Blueprint {
	private final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();

	public void put(BlockPos pos, BlockState state) {
		blocks.put(pos.asLong(), state);
	}

	public @Nullable BlockState get(long pos) {
		return blocks.get(pos);
	}

	public boolean contains(long pos) {
		return blocks.containsKey(pos);
	}

	public boolean contains(BlockPos pos) {
		return blocks.containsKey(pos.asLong());
	}

	public void remove(long pos) {
		blocks.remove(pos);
	}

	public int size() {
		return blocks.size();
	}

	/** A snapshot of all positions, safe to iterate over several ticks while the map changes. */
	public long[] snapshotPositions() {
		return blocks.keySet().toLongArray();
	}

	/** Counts blueprint blocks that match a test, e.g. all farmland. */
	public int count(Predicate<BlockState> test) {
		int count = 0;
		for (BlockState state : blocks.values()) {
			if (test.test(state)) {
				count++;
			}
		}
		return count;
	}

	/** All positions whose state matches a test. */
	public List<BlockPos> find(Predicate<BlockState> test) {
		List<BlockPos> result = new ArrayList<>();
		for (Long2ObjectMap.Entry<BlockState> entry : blocks.long2ObjectEntrySet()) {
			if (test.test(entry.getValue())) {
				result.add(BlockPos.of(entry.getLongKey()));
			}
		}
		return result;
	}

	// ------------------------------------------------------------------ saving

	public CompoundTag save() {
		Object2IntOpenHashMap<BlockState> paletteIndex = new Object2IntOpenHashMap<>();
		ListTag palette = new ListTag();
		long[] positions = new long[blocks.size()];
		int[] states = new int[blocks.size()];

		int i = 0;
		for (Long2ObjectMap.Entry<BlockState> entry : blocks.long2ObjectEntrySet()) {
			BlockState state = entry.getValue();
			int index = paletteIndex.computeIfAbsent(state, s -> {
				palette.add(NbtUtils.writeBlockState((BlockState) s));
				return palette.size() - 1;
			});
			positions[i] = entry.getLongKey();
			states[i] = index;
			i++;
		}

		CompoundTag tag = new CompoundTag();
		tag.put("palette", palette);
		tag.putLongArray("positions", positions);
		tag.putIntArray("states", states);
		return tag;
	}

	public static Blueprint load(CompoundTag tag) {
		Blueprint blueprint = new Blueprint();
		ListTag paletteTag = tag.getListOrEmpty("palette");
		List<BlockState> palette = new ArrayList<>(paletteTag.size());
		for (Tag entry : paletteTag) {
			if (entry instanceof CompoundTag compound) {
				palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, compound));
			}
		}

		long[] positions = tag.getLongArray("positions").orElse(new long[0]);
		int[] states = tag.getIntArray("states").orElse(new int[0]);
		int count = Math.min(positions.length, states.length);
		for (int i = 0; i < count; i++) {
			int index = states[i];
			if (index >= 0 && index < palette.size()) {
				blueprint.blocks.put(positions[i], palette.get(index));
			}
		}
		return blueprint;
	}
}
