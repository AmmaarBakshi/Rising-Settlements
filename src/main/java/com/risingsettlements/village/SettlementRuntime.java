package com.risingsettlements.village;

import com.risingsettlements.repair.RepairWorker;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Work-in-progress state of a settlement that does not need to be saved.
 *
 * <p>If the server restarts, anything in here is simply started again: the survey picks up
 * where the blueprint left off, and damage is found again by the next scan.
 */
public final class SettlementRuntime {
	/** Recording of the original village blocks, while it is running. */
	@Nullable VillageSurvey survey;
	/** Finds and rebuilds missing village blocks. */
	public final RepairWorker repairs = new RepairWorker();
	/** The villager currently shown doing the work, and when it may stop. */
	@Nullable UUID worker;
	long workerUntil;
}
