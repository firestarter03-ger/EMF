package net.fire.emf.client.resource;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProfileDetector {
	private static final Pattern LEGACY_PREFIX = Pattern.compile(
			"(?i)(§x(?:§[0-9a-fA-F]){6}|§[0-9a-fk-orA-FK-OR])?(\\[(?:S|D|O)\\])");

	private static GameProfile current = GameProfile.UNKNOWN;

	private ProfileDetector() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ProfileDetector::tick);
	}

	public static GameProfile current() {
		return current;
	}

	private static void tick(Minecraft client) {
		if (client.player == null) {
			if (current != GameProfile.UNKNOWN) {
				setProfile(GameProfile.UNKNOWN);
			}
			return;
		}

		GameProfile detected = detect(client);
		if (detected != current) {
			setProfile(detected);
		}
	}

	private static void setProfile(GameProfile profile) {
		current = profile;
		CollectionStore.onProfileChanged();
		CollectionGoalStore.onProfileChanged();
	}

	private static GameProfile detect(Minecraft client) {
		Component tabName = tabListName(client);
		if (tabName != null) {
			GameProfile profile = detectFromComponent(tabName);
			if (profile != GameProfile.UNKNOWN) {
				return profile;
			}
		}

		GameProfile profile = detectFromComponent(client.player.getDisplayName());
		if (profile != GameProfile.UNKNOWN) {
			return profile;
		}

		return detectFromLegacyString(client.player.getDisplayName().getString());
	}

	private static Component tabListName(Minecraft client) {
		if (client.getConnection() == null || client.player == null) {
			return null;
		}
		for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
			if (info.getProfile().id().equals(client.player.getUUID())) {
				Component tabName = info.getTabListDisplayName();
				return tabName == null ? Component.literal(info.getProfile().name()) : tabName;
			}
		}
		return null;
	}

	private static GameProfile detectFromComponent(Component component) {
		if (component == null) {
			return GameProfile.UNKNOWN;
		}

		StringBuilder text = new StringBuilder();
		List<TextColor> colors = new ArrayList<>();
		component.visit((style, segment) -> {
			TextColor color = style.getColor();
			for (int i = 0; i < segment.length(); i++) {
				colors.add(color);
				text.append(segment.charAt(i));
			}
			return Optional.empty();
		}, Style.EMPTY);

		return matchPrefix(text.toString(), colors);
	}

	private static GameProfile detectFromLegacyString(String raw) {
		if (raw == null || raw.isBlank()) {
			return GameProfile.UNKNOWN;
		}
		Matcher matcher = LEGACY_PREFIX.matcher(raw);
		if (!matcher.find()) {
			return GameProfile.UNKNOWN;
		}
		String colorCode = matcher.group(1);
		return matchTag(matcher.group(2), legacyColor(colorCode));
	}

	private static GameProfile matchPrefix(String plain, List<TextColor> colors) {
		if (plain == null || plain.isBlank()) {
			return GameProfile.UNKNOWN;
		}

		int bestIndex = Integer.MAX_VALUE;
		GameProfile best = GameProfile.UNKNOWN;
		for (String tag : List.of("[S]", "[D]", "[O]")) {
			int index = indexOfIgnoreCase(plain, tag);
			if (index < 0 || index >= bestIndex) {
				continue;
			}
			TextColor color = index < colors.size() ? colors.get(index) : null;
			GameProfile profile = matchTag(tag, color);
			if (profile != GameProfile.UNKNOWN) {
				bestIndex = index;
				best = profile;
			}
		}
		return best;
	}

	private static int indexOfIgnoreCase(String text, String tag) {
		return text.toLowerCase(Locale.ROOT).indexOf(tag.toLowerCase(Locale.ROOT));
	}

	private static GameProfile matchTag(String tag, TextColor color) {
		if (tag == null) {
			return GameProfile.UNKNOWN;
		}
		return switch (tag.toUpperCase(Locale.ROOT)) {
			case "[S]" -> isGray(color) ? GameProfile.STONEBLOCK : isAqua(color) ? GameProfile.SKYBLOCK : GameProfile.UNKNOWN;
			case "[D]" -> isRed(color) ? GameProfile.DEMON : GameProfile.UNKNOWN;
			case "[O]" -> isBlue(color) ? GameProfile.OCEANBLOCK : GameProfile.UNKNOWN;
			default -> GameProfile.UNKNOWN;
		};
	}

	private static TextColor legacyColor(String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		if (code.startsWith("§x")) {
			String hex = code.replace("§", "");
			if (hex.length() >= 7) {
				try {
					return TextColor.fromRgb(Integer.parseInt(hex.substring(1), 16));
				} catch (NumberFormatException ignored) {
					return null;
				}
			}
			return null;
		}
		if (code.length() >= 2 && code.charAt(0) == '§') {
			ChatFormatting formatting = ChatFormatting.getByCode(code.charAt(1));
			if (formatting != null && formatting.getColor() != null) {
				return TextColor.fromLegacyFormat(formatting);
			}
		}
		return null;
	}

	private static boolean isAqua(TextColor color) {
		return matchesColor(color, ChatFormatting.AQUA, 0x55FFFF);
	}

	private static boolean isGray(TextColor color) {
		return matchesColor(color, ChatFormatting.GRAY, 0xAAAAAA)
				|| matchesColor(color, ChatFormatting.DARK_GRAY, 0x555555);
	}

	private static boolean isRed(TextColor color) {
		return matchesColor(color, ChatFormatting.RED, 0xFF5555)
				|| matchesColor(color, ChatFormatting.DARK_RED, 0xAA0000);
	}

	private static boolean isBlue(TextColor color) {
		return matchesColor(color, ChatFormatting.BLUE, 0x5555FF)
				|| matchesColor(color, ChatFormatting.DARK_BLUE, 0x0000AA);
	}

	private static boolean matchesColor(TextColor color, ChatFormatting formatting, int rgb) {
		if (color == null) {
			return false;
		}
		Integer value = color.getValue();
		if (value != null && (value & 0xFFFFFF) == (rgb & 0xFFFFFF)) {
			return true;
		}
		Integer legacy = formatting.getColor();
		return value != null && legacy != null && (legacy & 0xFFFFFF) == (value & 0xFFFFFF);
	}
}
