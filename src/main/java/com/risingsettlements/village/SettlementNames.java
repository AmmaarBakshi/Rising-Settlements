package com.risingsettlements.village;

import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Makes up friendly village names such as "Willowbrook" or "Ambermere", so players can
 * tell their villages apart in commands and on the mayor screen.
 */
public final class SettlementNames {
	private static final List<String> FIRST = List.of(
			"Oak", "Stone", "Willow", "Amber", "Briar", "Clay", "Elder", "Fern", "Glen", "Hazel",
			"Iron", "Juniper", "Kettle", "Lark", "Maple", "North", "Oat", "Pebble", "Quill", "Rush",
			"Sand", "Thorn", "Umber", "Vale", "Wheat", "Yarrow", "Ash", "Birch", "Copper", "Dusk",
			"Ember", "Flint", "Gold", "Honey", "Moss", "Pine", "River", "Sun", "Winter", "Rose");

	private static final List<String> SECOND = List.of(
			"brook", "field", "ford", "haven", "hollow", "mere", "stead", "ton", "wick", "wood",
			"bury", "dale", "gate", "holm", "moor", "well", "ridge", "crest", "fall", "shire");

	private SettlementNames() {
	}

	public static String generate(RandomSource random) {
		return FIRST.get(random.nextInt(FIRST.size())) + SECOND.get(random.nextInt(SECOND.size()));
	}
}
