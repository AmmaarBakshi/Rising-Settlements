package com.risingsettlements.build;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A growth project as an ordered list of block placements: a house, a field or a street.
 *
 * <p>Planners fill the list in building order (ground first, roof last), and
 * {@link ConstructionWorker} places it a block at a time. The plan is saved with the
 * settlement, so a half-built house is finished after a restart instead of being left
 * as a ruin.
 */
public final class BuildPlan {
	public enum Kind {
		HOUSE,
		FARM,
		ROAD;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Kind byId(String id) {
			for (Kind kind : values()) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}
			return HOUSE;
		}
	}

	/** The step may replace natural ground (for foundations, roads and fields), not just empty space. */
	public static final int REPLACE_GROUND = 1;
	/** The placed block is not added to the blueprint (crops, which grow and get harvested). */
	public static final int NO_RECORD = 2;

	private final Kind kind;
	private final LongArrayList positions = new LongArrayList();
	private final List<BlockState> states = new ArrayList<>();
	private final IntArrayList flags = new IntArrayList();
	private int next;
	private @Nullable BoundingBox area;

	public BuildPlan(Kind kind) {
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	/** Adds a placement. An air state clears loose plants from the position. */
	public void add(BlockPos pos, BlockState state, int stepFlags) {
		positions.add(pos.asLong());
		states.add(state);
		flags.add(stepFlags);
		area = null;
	}

	public void add(BlockPos pos, BlockState state) {
		add(pos, state, 0);
	}

	/** Clears grass, flowers or snow from a position so something can be built there. */
	public void clear(BlockPos pos) {
		add(pos, Blocks.AIR.defaultBlockState(), 0);
	}

	public int size() {
		return positions.size();
	}

	/** Number of solid blocks the plan places, for progress reports. */
	public int blockCount() {
		int count = 0;
		for (BlockState state : states) {
			if (!state.isAir()) {
				count++;
			}
		}
		return count;
	}

	public boolean hasNext() {
		return next < positions.size();
	}

	long nextPos() {
		return positions.getLong(next);
	}

	BlockState nextState() {
		return states.get(next);
	}

	int nextFlags() {
		return flags.getInt(next);
	}

	void advance() {
		next++;
	}

	/** How far along the plan is, from 0 to 100. */
	public int progressPercent() {
		return positions.isEmpty() ? 100 : next * 100 / positions.size();
	}

	/** The box around every position in the plan. */
	public BoundingBox area() {
		if (area == null) {
			BoundingBox box = new BoundingBox(BlockPos.of(positions.getLong(0)));
			for (int i = 1; i < positions.size(); i++) {
				box = BoundingBox.encapsulating(box, new BoundingBox(BlockPos.of(positions.getLong(i))));
			}
			area = box;
		}
		return area;
	}

	// ------------------------------------------------------------------ saving

	public CompoundTag save() {
		Object2IntOpenHashMap<BlockState> paletteIndex = new Object2IntOpenHashMap<>();
		ListTag palette = new ListTag();
		int[] stateIndices = new int[states.size()];
		for (int i = 0; i < states.size(); i++) {
			stateIndices[i] = paletteIndex.computeIfAbsent(states.get(i), s -> {
				palette.add(NbtUtils.writeBlockState((BlockState) s));
				return palette.size() - 1;
			});
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("kind", kind.id());
		tag.putInt("next", next);
		tag.put("palette", palette);
		tag.putLongArray("positions", positions.toLongArray());
		tag.putIntArray("states", stateIndices);
		tag.putIntArray("flags", flags.toIntArray());
		return tag;
	}

	public static @Nullable BuildPlan load(CompoundTag tag) {
		BuildPlan plan = new BuildPlan(Kind.byId(tag.getStringOr("kind", "house")));
		List<BlockState> palette = new ArrayList<>();
		for (Tag entry : tag.getListOrEmpty("palette")) {
			if (entry instanceof CompoundTag compound) {
				palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, compound));
			}
		}
		long[] positions = tag.getLongArray("positions").orElse(new long[0]);
		int[] stateIndices = tag.getIntArray("states").orElse(new int[0]);
		int[] stepFlags = tag.getIntArray("flags").orElse(new int[0]);
		int count = Math.min(positions.length, Math.min(stateIndices.length, stepFlags.length));
		for (int i = 0; i < count; i++) {
			int index = stateIndices[i];
			if (index >= 0 && index < palette.size()) {
				plan.positions.add(positions[i]);
				plan.states.add(palette.get(index));
				plan.flags.add(stepFlags[i]);
			}
		}
		if (plan.positions.isEmpty()) {
			return null;
		}
		plan.next = Math.clamp(tag.getIntOr("next", 0), 0, plan.positions.size());
		return plan;
	}
}
