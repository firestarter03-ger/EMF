package net.fire.emf.client.session;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.ArrayList;
import java.util.List;

public final class SessionComponents {
	private SessionComponents() {
	}

	public static String toJson(Component component) {
		if (component == null) {
			return "";
		}
		return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component)
				.result()
				.map(JsonElement::toString)
				.orElse("");
	}

	public static Component fromJson(String json) {
		if (json == null || json.isBlank()) {
			return Component.empty();
		}
		try {
			JsonElement element = com.google.gson.JsonParser.parseString(json);
			return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element)
					.result()
					.orElse(Component.empty());
		} catch (RuntimeException ignored) {
			// Kein raw JSON als Literal anzeigen (sonst landet Hover-Müll im Overlay).
			return Component.empty();
		}
	}

	public static List<String> toJsonList(List<Component> components) {
		List<String> json = new ArrayList<>();
		if (components == null) {
			return json;
		}
		for (Component component : components) {
			if (component == null) {
				json.add(toJson(Component.literal(" ")));
				continue;
			}
			String encoded = toJson(component);
			if (encoded.isBlank()) {
				// Leere Zeilen (Absätze) behalten — sonst fehlen \n im Hover.
				json.add(toJson(Component.literal(" ")));
			} else {
				json.add(encoded);
			}
		}
		return json;
	}

	public static List<Component> fromJsonList(List<String> jsonLines) {
		List<Component> components = new ArrayList<>();
		if (jsonLines == null) {
			return components;
		}
		for (String line : jsonLines) {
			if (line == null || line.isBlank()) {
				components.add(Component.literal(" "));
				continue;
			}
			Component component = fromJson(line);
			if (component.getString().isBlank()) {
				components.add(Component.literal(" "));
			} else {
				components.add(component);
			}
		}
		return components;
	}
}
