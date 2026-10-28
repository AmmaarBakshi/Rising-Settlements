package com.risingsettlements.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.risingsettlements.RisingSettlements;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Translatable chat messages with an English fallback.
 *
 * <p>Messages are sent as translation keys, so clients with the mod installed see them in
 * their own language. The server also attaches the English text from the mod's own
 * {@code en_us.json} as a fallback, so players <i>without</i> the mod (it works on a server
 * with vanilla clients, apart from the mayor screen) still see readable text.
 */
public final class Lang {
	private static final String PATH = "/assets/" + RisingSettlements.MOD_ID + "/lang/en_us.json";
	private static final Map<String, String> ENGLISH = load();

	private Lang() {
	}

	/** A translatable message: {@code key} is relative to the mod ID, e.g. "command.info.header". */
	public static MutableComponent tr(String key, Object... args) {
		String fullKey = RisingSettlements.MOD_ID + "." + key;
		return Component.translatableWithFallback(fullKey, ENGLISH.get(fullKey), args);
	}

	private static Map<String, String> load() {
		try (InputStream in = Lang.class.getResourceAsStream(PATH)) {
			if (in == null) {
				return Map.of();
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				Map<String, String> map = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() { }.getType());
				return map != null ? map : Map.of();
			}
		} catch (Exception e) {
			RisingSettlements.LOGGER.warn("Could not load English fallback text: {}", e.getMessage());
			return Map.of();
		}
	}
}
