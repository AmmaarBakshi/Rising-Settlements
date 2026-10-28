package com.risingsettlements.village;

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
}
