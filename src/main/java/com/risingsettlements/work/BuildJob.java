package com.risingsettlements.work;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * An ordered list of block changes that one villager works through, a few blocks at a time.
 *
 * <p>Repairs and every growth project (houses, farms, roads, ...) are build jobs. Project
 * jobs are saved with the settlement, so a half-built house is finished after a restart.
 */
public final class BuildJob {
	/** What the job is for. Also decides which tool the villager holds. */
	public enum Kind {
		REPAIR, HOUSE, FARM, ROAD, WELL, LAMPS, WALL;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Kind byId(String id) {
			for (Kind kind : values()) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}
			return REPAIR;
		}
	}

	private final Kind kind;
	private final ArrayDeque<Placement> queue;
	private final int total;
	private int done;

	// Runtime state, not saved: a new worker is picked after a restart.
	@Nullable UUID worker;
	long nextActionTime;
	long walkStartedAt;
	int deferrals;

	public BuildJob(Kind kind, Collection<Placement> placements) {
		this(kind, placements, placements.size(), 0);
	}

	private BuildJob(Kind kind, Collection<Placement> placements, int total, int done) {
		this.kind = kind;
		this.queue = new ArrayDeque<>(placements);
		this.total = total;
		this.done = done;
	}

	public Kind kind() {
		return kind;
	}

	/** The villager currently doing this job, if any. */
	public @Nullable UUID workerId() {
		return worker;
	}

	public boolean isFinished() {
		return queue.isEmpty();
	}

	public @Nullable Placement peek() {
		return queue.peek();
	}

	public int remaining() {
		return queue.size();
	}

	public int total() {
		return total;
	}

	public int done() {
		return done;
	}

	/** Progress from 0 to 100. */
	public int percent() {
		return total == 0 ? 100 : Math.min(100, (total - queue.size()) * 100 / total);
	}

	void completeHead() {
		queue.poll();
		done++;
		deferrals = 0;
	}

	void skipHead() {
		queue.poll();
	}

	/** Moves the next placement to the back of the queue, to try it again later. */
	void deferHead() {
		Placement head = queue.poll();
		if (head != null) {
			queue.add(head);
		}
		deferrals++;
	}

	// ------------------------------------------------------------------ saving

	public CompoundTag save() {
		Object2IntOpenHashMap<BlockState> paletteIndex = new Object2IntOpenHashMap<>();
		ListTag palette = new ListTag();
		long[] positions = new long[queue.size()];
		int[] states = new int[queue.size()];
		int[] modes = new int[queue.size()];

		int i = 0;
		for (Placement placement : queue) {
			states[i] = paletteIndex.computeIfAbsent(placement.state(), s -> {
				palette.add(NbtUtils.writeBlockState((BlockState) s));
				return palette.size() - 1;
			});
			positions[i] = placement.pos().asLong();
			modes[i] = placement.mode().ordinal();
			i++;
		}

		CompoundTag tag = new CompoundTag();
		tag.putString("kind", kind.id());
		tag.putInt("total", total);
		tag.putInt("done", done);
		tag.put("palette", palette);
		tag.putLongArray("positions", positions);
		tag.putIntArray("states", states);
		tag.putIntArray("modes", modes);
		return tag;
	}

	public static BuildJob load(CompoundTag tag) {
		List<BlockState> palette = new ArrayList<>();
		for (Tag entry : tag.getListOrEmpty("palette")) {
			if (entry instanceof CompoundTag compound) {
				palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, compound));
			}
		}
		long[] positions = tag.getLongArray("positions").orElse(new long[0]);
		int[] states = tag.getIntArray("states").orElse(new int[0]);
		int[] modes = tag.getIntArray("modes").orElse(new int[0]);
		Placement.Mode[] modeValues = Placement.Mode.values();

		List<Placement> placements = new ArrayList<>();
		int count = Math.min(positions.length, Math.min(states.length, modes.length));
		for (int i = 0; i < count; i++) {
			if (states[i] >= 0 && states[i] < palette.size() && modes[i] >= 0 && modes[i] < modeValues.length) {
				placements.add(new Placement(BlockPos.of(positions[i]), palette.get(states[i]), modeValues[modes[i]]));
			}
		}
		return new BuildJob(Kind.byId(tag.getStringOr("kind", "repair")), placements,
				tag.getIntOr("total", placements.size()), tag.getIntOr("done", 0));
	}
}
