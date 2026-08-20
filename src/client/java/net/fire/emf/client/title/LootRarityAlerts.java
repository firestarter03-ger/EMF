package net.fire.emf.client.title;

import net.fire.emf.client.config.EmfConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;

import java.util.Optional;
import java.util.regex.Pattern;

public final class LootRarityAlerts {
	private static final Pattern COLOR_CODES = Pattern.compile("§x(?:§[0-9a-fA-F]){6}|§[0-9a-fk-orA-FK-OR]");
	private static final String LOOT_PREFIX = "Loot:";
	public static final LootAlertSound DEFAULT_SOUND = LootAlertSound.BELL;
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
		StringBuilder text = new StringBuilder();
		message.visit((style, ignored) -> {
			appendHover(text, style.getHoverEvent());
			return Optional.empty();
		}, Style.EMPTY);
		return text.toString();
	}

	private static void appendHover(StringBuilder text, HoverEvent hover) {
		if (hover instanceof HoverEvent.ShowText showText) {
			appendLine(text, clean(showText.value().getString()));
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
			appendLine(text, clean(stack.getHoverName().getString()));
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore != null) {
				for (Component line : lore.lines()) {
					appendLine(text, clean(line.getString()));
				}
			}
			Minecraft client = Minecraft.getInstance();
			if (client != null && client.level != null) {
				for (Component line : stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL)) {
					appendLine(text, clean(line.getString()));
				}
			}
		} catch (RuntimeException ignored) {
		}
	}

	private static void appendLine(StringBuilder text, String line) {
		if (line == null || line.isBlank()) {
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
