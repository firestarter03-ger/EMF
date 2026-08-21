package net.fire.emf.client.itempool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Normalisiert JSON so, dass Schlüsselreihenfolge die Identität nicht verändert.
 * Array-Reihenfolge bleibt erhalten.
 */
public final class JsonCanonicalizer {
	private JsonCanonicalizer() {
	}

	public static String canonicalize(String json) {
		if (json == null || json.isBlank()) {
			return "";
		}
		try {
			JsonElement element = JsonParser.parseString(json);
			return canonicalizeElement(element).toString();
		} catch (RuntimeException ignored) {
			return json.trim();
		}
	}

	public static List<String> canonicalizeList(List<String> jsonLines) {
		List<String> out = new ArrayList<>();
		if (jsonLines == null) {
			return out;
		}
		for (String line : jsonLines) {
			out.add(canonicalize(line == null ? "" : line));
		}
		return out;
	}

	private static JsonElement canonicalizeElement(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return element;
		}
		if (element.isJsonObject()) {
			JsonObject source = element.getAsJsonObject();
			TreeMap<String, JsonElement> sorted = new TreeMap<>();
			for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
				sorted.put(entry.getKey(), canonicalizeElement(entry.getValue()));
			}
			JsonObject result = new JsonObject();
			for (Map.Entry<String, JsonElement> entry : sorted.entrySet()) {
				result.add(entry.getKey(), entry.getValue());
			}
			return result;
		}
		if (element.isJsonArray()) {
			JsonArray source = element.getAsJsonArray();
			JsonArray result = new JsonArray();
			for (JsonElement child : source) {
				result.add(canonicalizeElement(child));
			}
			return result;
		}
		return element;
	}
}
