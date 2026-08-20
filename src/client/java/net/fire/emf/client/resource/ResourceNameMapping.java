package net.fire.emf.client.resource;

import java.util.Map;

public final class ResourceNameMapping {
	private static final Map<String, String> NAMES = Map.ofEntries(
			Map.entry("minecraft:wheat", "Weizen"),
			Map.entry("minecraft:coal_ore", "Kohle"),
			Map.entry("minecraft:spruce_wood", "Fichtenholz"),
			Map.entry("minecraft:pumpkin", "Kürbis"),
			Map.entry("minecraft:sugar_cane", "Zuckerrohr"),
			Map.entry("minecraft:copper_ore", "Kupfer"),
			Map.entry("minecraft:beetroots", "Rote Beete"),
			Map.entry("minecraft:melon", "Melone"),
			Map.entry("minecraft:sunflower", "Sonnenblume"),
			Map.entry("minecraft:iron_ore", "Eisen"),
			Map.entry("minecraft:jungle_wood", "Dschungelholz"),
			Map.entry("minecraft:carrots", "Karotten"),
			Map.entry("minecraft:redstone_ore", "Redstone"),
			Map.entry("minecraft:cocoa", "Kakao"),
			Map.entry("minecraft:bamboo", "Bambus"),
			Map.entry("minecraft:potatoes", "Kartoffeln"));

	private ResourceNameMapping() {
	}

	public static String nameFor(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		return NAMES.get(id);
	}
}
