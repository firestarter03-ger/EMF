package net.fire.emf.client.title;

import net.minecraft.ChatFormatting;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum LootRarity {
	COMMON("Common", ChatFormatting.GRAY),
	UNCOMMON("Uncommon", ChatFormatting.GREEN),
	RARE("Rare", ChatFormatting.BLUE),
	EPIC("Epic", ChatFormatting.LIGHT_PURPLE),
	LEGENDARY("Legendary", ChatFormatting.GOLD),
	UNIQUE("Unique", ChatFormatting.RED);

	private static final Pattern TOKEN = Pattern.compile(
			"\\b(Unique|Legendary|Epic|Uncommon|Rare|Common)\\b",
			Pattern.CASE_INSENSITIVE);

	private final String displayName;
	private final ChatFormatting color;

	LootRarity(String displayName, ChatFormatting color) {
		this.displayName = displayName;
		this.color = color;
	}

	public String displayName() {
		return displayName;
	}

	public ChatFormatting color() {
		return color;
	}

	public boolean meetsMinimum(LootRarity minimum) {
		return minimum == null || ordinal() >= minimum.ordinal();
	}

	public static LootRarity highestIn(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		Matcher matcher = TOKEN.matcher(text);
		LootRarity highest = null;
		while (matcher.find()) {
			LootRarity rarity = fromToken(matcher.group(1));
			if (rarity != null && (highest == null || rarity.ordinal() > highest.ordinal())) {
				highest = rarity;
			}
		}
		return highest;
	}

	private static LootRarity fromToken(String token) {
		if (token == null) {
			return null;
		}
		return switch (token.toLowerCase(Locale.ROOT)) {
			case "common" -> COMMON;
			case "uncommon" -> UNCOMMON;
			case "rare" -> RARE;
			case "epic" -> EPIC;
			case "legendary" -> LEGENDARY;
			case "unique" -> UNIQUE;
			default -> null;
		};
	}
}
