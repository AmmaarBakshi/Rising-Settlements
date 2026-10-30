package com.risingsettlements.village;

import java.util.Locale;

/** What a village builds next. {@link #AUTO} lets the village decide from its census. */
public enum Project {
	AUTO,
	HOUSE,
	FARM,
	ROAD;

	/** Lower-case name used in save files, commands and network packets. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Project byId(String id) {
		for (Project project : values()) {
			if (project.id().equals(id)) {
				return project;
			}
		}
		return AUTO;
	}
}
