package com.risingsettlements.village;

import com.risingsettlements.work.BuildJob;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * How a settlement is growing: its resources, the project it is working on, what it has
 * built so far, and whether growth is paused. Saved with the settlement.
 */
public final class GrowthState {
	private final Settlement owner;

	private int resources;
	/** The last in-game day for which villagers added resources. */
	private long lastIncomeDay = -1;
	/** Game time at which the next project may start. */
	private long nextProjectTime;
	private boolean paused;
	/** Project chosen by the mayor, built next instead of the village's own choice. */
	private BuildJob.@Nullable Kind requested;
	/** The project being built right now. */
	private @Nullable BuildJob project;
	private final List<Building> buildings = new ArrayList<>();
	private final Map<BuildJob.Kind, Integer> completed = new EnumMap<>(BuildJob.Kind.class);
	/** Why the last attempt to grow didn't work out, as a translation key (empty if none). */
	private String note = "";

	GrowthState(Settlement owner) {
		this.owner = owner;
	}

	public int resources() {
		return resources;
	}

	public void addResources(int amount, int max) {
		resources = Math.max(0, Math.min(max, resources + amount));
		owner.markDirty();
	}

	public boolean spend(int amount) {
		if (resources < amount) {
			return false;
		}
		resources -= amount;
		owner.markDirty();
		return true;
	}

	public long lastIncomeDay() {
		return lastIncomeDay;
	}

	public void setLastIncomeDay(long day) {
		lastIncomeDay = day;
		owner.markDirty();
	}

	public long nextProjectTime() {
		return nextProjectTime;
	}

	public void setNextProjectTime(long time) {
		nextProjectTime = time;
		owner.markDirty();
	}

	public boolean isPaused() {
		return paused;
	}

	public void setPaused(boolean paused) {
		this.paused = paused;
		owner.markDirty();
	}

	public BuildJob.@Nullable Kind requested() {
		return requested;
	}

	public void setRequested(BuildJob.@Nullable Kind requested) {
		this.requested = requested;
		owner.markDirty();
	}

	public @Nullable BuildJob project() {
		return project;
	}

	public void setProject(@Nullable BuildJob project) {
		this.project = project;
		owner.markDirty();
	}

	public List<Building> buildings() {
		return Collections.unmodifiableList(buildings);
	}

	public void addBuilding(Building building) {
		buildings.add(building);
		owner.markDirty();
	}

	public void replaceBuilding(Building old, Building updated) {
		int index = buildings.indexOf(old);
		if (index >= 0) {
			buildings.set(index, updated);
			owner.markDirty();
		}
	}

	public int completed(BuildJob.Kind kind) {
		return completed.getOrDefault(kind, 0);
	}

	public void addCompleted(BuildJob.Kind kind) {
		completed.merge(kind, 1, Integer::sum);
		owner.markDirty();
	}

	public String note() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
		owner.markDirty();
	}

	// ------------------------------------------------------------------ saving

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("resources", resources);
		tag.putLong("last_income_day", lastIncomeDay);
		tag.putLong("next_project_time", nextProjectTime);
		tag.putBoolean("paused", paused);
		if (requested != null) {
			tag.putString("requested", requested.id());
		}
		if (project != null) {
			tag.put("project", project.save());
		}
		ListTag buildingList = new ListTag();
		buildings.forEach(b -> buildingList.add(b.save()));
		tag.put("buildings", buildingList);
		CompoundTag completedTag = new CompoundTag();
		completed.forEach((kind, count) -> completedTag.putInt(kind.id(), count));
		tag.put("completed", completedTag);
		tag.putString("note", note);
		return tag;
	}

	void load(CompoundTag tag, int startingResources) {
		resources = tag.getIntOr("resources", startingResources);
		lastIncomeDay = tag.getLongOr("last_income_day", -1L);
		nextProjectTime = tag.getLongOr("next_project_time", 0L);
		paused = tag.getBooleanOr("paused", false);
		requested = tag.getString("requested").map(BuildJob.Kind::byId).orElse(null);
		project = tag.getCompound("project").map(BuildJob::load).orElse(null);
		for (Tag entry : tag.getListOrEmpty("buildings")) {
			if (entry instanceof CompoundTag compound) {
				buildings.add(Building.load(compound));
			}
		}
		CompoundTag completedTag = tag.getCompoundOrEmpty("completed");
		for (BuildJob.Kind kind : BuildJob.Kind.values()) {
			completedTag.getInt(kind.id()).ifPresent(count -> completed.put(kind, count));
		}
		note = tag.getStringOr("note", "");
	}

	void initNew(int startingResources) {
		resources = startingResources;
	}
}
