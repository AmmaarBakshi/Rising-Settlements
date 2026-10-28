package com.risingsettlements.village;

import com.risingsettlements.repair.DamageScanner;
import com.risingsettlements.work.BuildJob;
import org.jspecify.annotations.Nullable;

/**
 * Work-in-progress state of a settlement that does not need to be saved.
 *
 * <p>If the server restarts, anything in here is simply started again: the survey picks up
 * where the blueprint left off, and damage is found again by the next scan.
 */
public final class SettlementRuntime {
	/** Recording of the original village blocks, while it is running. */
	@Nullable VillageSurvey survey;

	/** Looks for missing village blocks. */
	public final DamageScanner damage = new DamageScanner();

	/** The current repair job, if villagers are repairing. Takes priority over building. */
	public @Nullable BuildJob repairJob;
}
