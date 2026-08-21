package net.fire.emf.client.itempool;

import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Zentrale Ignore-Liste: ignorierte Items werden weder lokal noch per Sync erfasst
 * und später auch nicht für Mob-Loot verwendet.
 */
public final class ItemIgnoreManager {
	private static final Pattern COLOR_CODES = Pattern.compile("§x(?:§[0-9a-fA-F]){6}|§[0-9a-fk-orA-FK-OR]");

	/** Exakte Itemnamen (ohne Farbcodes), case-insensitive. Wird später erweitert. */
	private static final Set<String> IGNORED_NAMES = ConcurrentHashMap.newKeySet();

	private ItemIgnoreManager() {
	}

	public static boolean shouldIgnoreItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return true;
		}
		String name = cleanName(stack.getHoverName().getString());
		return shouldIgnoreName(name);
	}

	public static boolean shouldIgnoreName(String itemName) {
		if (itemName == null || itemName.isBlank()) {
			return true;
		}
		return IGNORED_NAMES.contains(itemName.toLowerCase(Locale.ROOT));
	}

	public static void addIgnoredName(String itemName) {
		if (itemName == null || itemName.isBlank()) {
			return;
		}
		IGNORED_NAMES.add(cleanName(itemName).toLowerCase(Locale.ROOT));
	}

	public static String cleanName(String value) {
		return value == null ? "" : COLOR_CODES.matcher(value).replaceAll("").trim();
	}
}
