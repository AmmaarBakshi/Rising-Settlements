package com.risingsettlements.util;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.risingsettlements.RisingSettlements;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Translatable chat text that still reads well on clients without the mod.
 *
 * <p>Every message has a key in {@code assets/rising_settlements/lang/en_us.json}. Clients
 * with the mod translate the key into their own language. The server also reads the
 * English file itself and sends it as the fallback, so a player on a vanilla client (or a
 * dedicated server without resource packs) sees English instead of a raw key.
 */
public final class Texts {
	private static final String LANG_FILE = "/assets/" + RisingSettlements.MOD_ID + "/lang/en_us.json";

	private static Map<String, String> english = Map.of();

	private Texts() {
	}

	/** Reads the bundled English language file. Called once when the mod loads. */
	public static void load() {
		try (InputStream in = Texts.class.getResourceAsStream(LANG_FILE)) {
			if (in == null) {
				RisingSettlements.LOGGER.warn("Language file {} is missing", LANG_FILE);
				return;
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				Map<String, String> loaded = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {
				}.getType());
				if (loaded != null) {
					english = Map.copyOf(loaded);
				}
			}
		} catch (IOException | JsonParseException e) {
			RisingSettlements.LOGGER.error("Could not read {}: {}", LANG_FILE, e.getMessage());
		}
	}

	/** A message from the language file, with {@code %s} placeholders filled in by {@code args}. */
	public static MutableComponent tr(String key, Object... args) {
		String fullKey = RisingSettlements.MOD_ID + "." + key;
		return Component.translatableWithFallback(fullKey, english.getOrDefault(fullKey, fullKey), args);
	}
}
