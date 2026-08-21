package net.fire.emf.client.title;

import net.fire.emf.client.config.EmfConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class LootRarityAlerts {
	private static final Pattern COLOR_CODES = Pattern.compile("§x(?:§[0-9a-fA-F]){6}|§[0-9a-fk-orA-FK-OR]");
	private static final String LOOT_PREFIX = "Loot:";
	public static final LootAlertSound DEFAULT_SOUND = LootAlertSound.CHALLENGE_COMPLETE;
	public static final float DEFAULT_PITCH = 1.059463f;

	private static int bellsLeft;
	private static float bellVolume;
	private static float bellPitch;
	private static SoundEvent bellSound;

	private LootRarityAlerts() {
	}

	public static void onChat(Component message) {
		if (message == null || !EmfConfig.HANDLER.instance().lootRarityAlertsEnabled) {
			return;
		}
		String text = clean(message.getString());
		if (!startsWithLoot(text)) {
			return;
		}
		String itemName = text.substring(LOOT_PREFIX.length()).trim();
		if (itemName.isBlank()) {
			return;
		}
		LootRarity rarity = LootRarity.highestIn(hoverText(message));
		if (rarity == null || !rarity.meetsMinimum(EmfConfig.HANDLER.instance().lootRarityMinimum)) {
			return;
		}
		showTitle(itemName, rarity);
		startSound();
	}

	static void tick(Minecraft client) {
		if (bellsLeft <= 0 || client == null || client.getSoundManager() == null || bellSound == null) {
			return;
		}
		client.getSoundManager().play(SimpleSoundInstance.forUI(bellSound, bellPitch, bellVolume));
		bellsLeft--;
	}

	private static void showTitle(String itemName, LootRarity rarity) {
		if (!EmfConfig.HANDLER.instance().lootRarityTitleEnabled) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.gui == null) {
			return;
		}
		client.gui.setTimes(0, 60, 20);
		client.gui.setTitle(Component.literal(rarity.displayName()).withStyle(rarity.color(), ChatFormatting.BOLD));
		client.gui.setSubtitle(Component.literal(itemName));
	}

	private static void startSound() {
		EmfConfig config = EmfConfig.HANDLER.instance();
		float volume = Math.max(0, Math.min(100, config.lootRaritySoundVolume)) / 100.0f;
		if (volume <= 0.0f) {
			return;
		}
		LootAlertSound sound = config.lootRaritySound == null ? DEFAULT_SOUND : config.lootRaritySound;
		bellSound = sound.soundEvent();
		bellPitch = sound.usesPitch() ? clampPitch(config.lootRaritySoundPitch) : 1.0f;
		bellVolume = volume;
		bellsLeft = sound.playCount();
	}

	private static float clampPitch(float pitch) {
		if (Float.isNaN(pitch) || pitch <= 0.0f) {
			return DEFAULT_PITCH;
		}
		return Math.max(0.5f, Math.min(2.0f, pitch));
	}

	private static boolean startsWithLoot(String text) {
		return text.regionMatches(true, 0, LOOT_PREFIX, 0, LOOT_PREFIX.length());
	}

	private static String hoverText(Component message) {
		return hoverTextFor(message);
	}

	public static String hoverTextFor(Component message) {
		StringBuilder text = new StringBuilder();
		for (Component line : hoverComponentsFor(message)) {
			appendLine(text, clean(line.getString()));
		}
		return text.toString();
	}

	public static List<Component> hoverComponentsFor(Component message) {
		List<Component> lines = new ArrayList<>();
		if (message == null) {
			return lines;
		}
		// Auf dem Mainserver hängt derselbe Hover oft an mehreren Chat-Segmenten —
		// nur den ersten verwenden, sonst doppelter Tooltip-Inhalt.
		HoverEvent firstHover = findFirstHover(message);
		if (firstHover != null) {
			appendHoverComponents(lines, firstHover);
		}
		return stripInteractions(lines);
	}

	private static HoverEvent findFirstHover(Component message) {
		AtomicReference<HoverEvent> found = new AtomicReference<>();
		message.visit((style, ignored) -> {
			if (found.get() != null || style == null) {
				return Optional.empty();
			}
			HoverEvent hover = style.getHoverEvent();
			if (hover != null) {
				found.set(hover);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return found.get();
	}

	public static Component lootNameComponent(Component message) {
		if (message == null) {
			return Component.empty();
		}
		MutableComponent result = Component.empty();
		StringBuilder consumed = new StringBuilder();
		AtomicBoolean pastPrefix = new AtomicBoolean(false);
		message.visit((style, text) -> {
			if (text == null || text.isEmpty()) {
				return Optional.empty();
			}
			Style cleanStyle = withoutInteractions(style);
			if (pastPrefix.get()) {
				if (result.getString().isEmpty() && text.isBlank()) {
					return Optional.empty();
				}
				result.append(Component.literal(text).withStyle(cleanStyle));
				return Optional.empty();
			}
			consumed.append(text);
			String soFar = consumed.toString();
			int index = indexOfLootPrefix(soFar);
			if (index < 0) {
				return Optional.empty();
			}
			pastPrefix.set(true);
			String after = soFar.substring(index + LOOT_PREFIX.length()).replaceFirst("^\\s+", "");
			if (!after.isEmpty()) {
				result.append(Component.literal(after).withStyle(cleanStyle));
			}
			return Optional.empty();
		}, Style.EMPTY);
		if (!result.getString().isBlank()) {
			return result;
		}
		String plain = clean(message.getString());
		if (startsWithLoot(plain)) {
			return Component.literal(plain.substring(LOOT_PREFIX.length()).trim());
		}
		return Component.literal(plain);
	}

	private static int indexOfLootPrefix(String text) {
		if (text == null) {
			return -1;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		return lower.indexOf(LOOT_PREFIX.toLowerCase(Locale.ROOT));
	}

	private static void appendHoverComponents(List<Component> lines, HoverEvent hover) {
		if (hover instanceof HoverEvent.ShowText showText) {
			splitStyledLines(lines, showText.value());
			return;
		}
		if (!(hover instanceof HoverEvent.ShowItem showItem)) {
			return;
		}
		try {
			ItemStack stack = showItem.item().create();
			if (stack == null || stack.isEmpty()) {
				return;
			}
			Minecraft client = Minecraft.getInstance();
			if (client != null && client.level != null) {
				List<Component> tooltip = stack.getTooltipLines(
						Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
				for (Component line : tooltip) {
					lines.add(line.copy());
				}
				return;
			}
			lines.add(stack.getHoverName().copy());
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore != null) {
				for (Component line : lore.lines()) {
					lines.add(line.copy());
				}
			}
		} catch (RuntimeException ignored) {
		}
	}

	private static void splitStyledLines(List<Component> lines, Component source) {
		if (source == null) {
			return;
		}
		AtomicReference<MutableComponent> current = new AtomicReference<>(Component.empty());
		source.visit((style, text) -> {
			if (text == null || text.isEmpty()) {
				return Optional.empty();
			}
			Style cleanStyle = withoutInteractions(style);
			String[] parts = text.split("\n", -1);
			for (int i = 0; i < parts.length; i++) {
				if (i > 0) {
					MutableComponent finished = current.get();
					if (finished.getString().isEmpty()) {
						lines.add(Component.literal(" "));
					} else {
						lines.add(finished);
					}
					current.set(Component.empty());
				}
				if (!parts[i].isEmpty()) {
					current.get().append(Component.literal(parts[i]).withStyle(cleanStyle));
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		MutableComponent finished = current.get();
		if (!finished.getString().isEmpty()) {
			lines.add(finished);
		}
	}

	private static List<Component> stripInteractions(List<Component> lines) {
		List<Component> cleaned = new ArrayList<>(lines.size());
		for (Component line : lines) {
			cleaned.add(copyWithoutInteractions(line));
		}
		return cleaned;
	}

	private static Component copyWithoutInteractions(Component component) {
		if (component == null) {
			return Component.empty();
		}
		MutableComponent result = Component.empty();
		component.visit((style, text) -> {
			if (text == null || text.isEmpty()) {
				return Optional.empty();
			}
			result.append(Component.literal(text).withStyle(withoutInteractions(style)));
			return Optional.empty();
		}, Style.EMPTY);
		return result.getString().isEmpty() ? Component.literal(" ") : result;
	}

	private static Style withoutInteractions(Style style) {
		if (style == null) {
			return Style.EMPTY;
		}
		Style cleaned = style;
		if (style.getHoverEvent() != null) {
			cleaned = cleaned.withHoverEvent(null);
		}
		if (style.getClickEvent() != null) {
			cleaned = cleaned.withClickEvent(null);
		}
		return cleaned;
	}

	private static void appendLine(StringBuilder text, String line) {
		if (line == null) {
			return;
		}
		if (!text.isEmpty()) {
			text.append('\n');
		}
		text.append(line);
	}

	private static String clean(String value) {
		return value == null ? "" : COLOR_CODES.matcher(value).replaceAll("").trim();
	}
}
