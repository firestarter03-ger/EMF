package net.fire.emf.client.itempool;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Prüft, ob ein ItemStack zu einer Special-Item-Regel passt.
 */
public final class SpecialItemMatcher {
	private SpecialItemMatcher() {
	}

	public static boolean matches(SpecialItemMatch match, ItemStack stack, String itemName, String hoverText, String registryId) {
		if (match == null) {
			return false;
		}
		boolean anyRule = false;

		if (match.itemName != null && !match.itemName.isBlank()) {
			anyRule = true;
			if (itemName == null || !itemName.equalsIgnoreCase(match.itemName.trim())) {
				return false;
			}
		}
		if (match.registryId != null && !match.registryId.isBlank()) {
			anyRule = true;
			if (registryId == null || !registryId.equalsIgnoreCase(match.registryId.trim())) {
				return false;
			}
		}
		if (match.hoverContainsAny != null && !match.hoverContainsAny.isEmpty()) {
			anyRule = true;
			if (!containsAny(hoverText, match.hoverContainsAny)) {
				return false;
			}
		}
		if (match.hoverContainsAll != null && !match.hoverContainsAll.isEmpty()) {
			anyRule = true;
			if (!containsAll(hoverText, match.hoverContainsAll)) {
				return false;
			}
		}
		if (match.hoverContains != null && !match.hoverContains.isEmpty()) {
			anyRule = true;
			if (!containsAll(hoverText, match.hoverContains)) {
				return false;
			}
		}
		// itemNameRegex: Struktur vorhanden, Matching erst in einer späteren Version

		return anyRule;
	}

	private static boolean containsAny(String hover, List<String> needles) {
		if (hover == null) {
			return false;
		}
		String lower = hover.toLowerCase(Locale.ROOT);
		for (String needle : needles) {
			if (needle != null && !needle.isBlank() && lower.contains(needle.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsAll(String hover, List<String> needles) {
		if (hover == null) {
			return false;
		}
		String lower = hover.toLowerCase(Locale.ROOT);
		for (String needle : needles) {
			if (needle == null || needle.isBlank()) {
				continue;
			}
			if (!lower.contains(needle.toLowerCase(Locale.ROOT))) {
				return false;
			}
		}
		return true;
	}
}
