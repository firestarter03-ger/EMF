package net.fire.emf.client.function;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.overlay.editor.KeyCategories;
import net.fire.emf.client.title.LootAlertSound;
import net.fire.emf.client.title.LootRarityAlerts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OffhandSwapper {
	private static final Pattern COLOR_CODES = Pattern.compile("§x(?:§[0-9a-fA-F]){6}|§[0-9a-fk-orA-FK-OR]");
	private static final Pattern AUTOMINER = Pattern.compile("(?is)Passiv:\\s*Autominer");
	private static final Pattern COOLDOWN = Pattern.compile("(?i)Cooldown:\\s*([0-9]+(?:[.,][0-9]+)?)\\s*s");
	private static final String DEFAULT_HOTKEY = "key.keyboard.r";
	private static final long EXPIRE_FLASH_MS = 3000L;

	private static KeyMapping toggleKey;
	private static boolean hotkeySyncReady;
	private static long cooldownUntilMs;
	private static long storedCooldownMs;
	private static boolean cooldownActive;
	private static boolean expireNotified;
	private static long expireFlashUntilMs;
	private static int pendingSoundPlays;
	private static float pendingSoundPitch;
	private static float pendingSoundVolume;
	private static net.minecraft.sounds.SoundEvent pendingSound;

	private OffhandSwapper() {
	}

	public static void initialize() {
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.emf.offhand_swapper",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				KeyCategories.of("emf", "functions")
		));
		EmfConfig config = EmfConfig.HANDLER.instance();
		applyHotkey(config.offhandSwapperHotkey);
		syncOverlayFlags(config.offhandSwapperEnabled);
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			applyConfigHotkeyToKeyMapping(true);
			hotkeySyncReady = true;
		});
		ClientTickEvents.END_CLIENT_TICK.register(OffhandSwapper::onClientTick);
	}

	public static String currentHotkeyName() {
		if (toggleKey != null) {
			return toggleKey.saveString();
		}
		String configured = EmfConfig.HANDLER.instance().offhandSwapperHotkey;
		return configured == null || configured.isBlank() ? DEFAULT_HOTKEY : configured;
	}

	public static void setHotkeyFromConfig(String keyName) {
		String normalized = normalizeKeyName(keyName);
		EmfConfig.HANDLER.instance().offhandSwapperHotkey = normalized;
		applyHotkey(normalized);
		saveMinecraftOptions();
	}

	public static void applyConfigHotkeyToKeyMapping(boolean saveOptions) {
		applyHotkey(EmfConfig.HANDLER.instance().offhandSwapperHotkey);
		if (saveOptions) {
			saveMinecraftOptions();
		}
	}

	public static void setDetectionEnabled(boolean enabled, boolean announce) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		boolean changed = config.offhandSwapperEnabled != enabled
				|| config.showAutominerCooldownOverlay != enabled
				|| config.autominerCooldownOverlayEnabled != enabled;
		config.offhandSwapperEnabled = enabled;
		syncOverlayFlags(enabled);
		if (changed) {
			EmfConfig.HANDLER.save();
		}
		if (!enabled) {
			clearCooldownState();
			pendingSoundPlays = 0;
			pendingSound = null;
		}
		if (announce) {
			announceToggle(enabled);
		}
	}

	private static void syncOverlayFlags(boolean enabled) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		config.showAutominerCooldownOverlay = enabled;
		config.autominerCooldownOverlayEnabled = enabled;
	}

	private static void announceToggle(boolean enabled) {
		ChatFormatting stateColor = enabled ? ChatFormatting.GREEN : ChatFormatting.RED;
		String stateText = enabled ? "aktiviert." : "deaktiviert.";
		Component message = Component.empty()
				.append(Component.literal("[EMF]").withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" Autominer-Erkennung " + stateText).withStyle(stateColor));
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(message);
		} else if (client.gui != null) {
			client.gui.getChat().addClientSystemMessage(message);
		}
	}

	public static void applyHotkey(String keyName) {
		if (toggleKey == null) {
			return;
		}
		try {
			toggleKey.setKey(InputConstants.getKey(normalizeKeyName(keyName)));
			KeyMapping.resetMapping();
		} catch (RuntimeException ignored) {
			toggleKey.setKey(InputConstants.getKey(DEFAULT_HOTKEY));
			KeyMapping.resetMapping();
		}
	}

	public static void syncHotkeyToConfig() {
		syncKeyMappingToConfig(false);
	}

	private static void syncKeyMappingToConfig(boolean saveIfChanged) {
		if (!hotkeySyncReady || toggleKey == null) {
			return;
		}
		String bound = toggleKey.saveString();
		EmfConfig config = EmfConfig.HANDLER.instance();
		if (bound.equals(config.offhandSwapperHotkey)) {
			return;
		}
		config.offhandSwapperHotkey = bound;
		if (saveIfChanged) {
			EmfConfig.HANDLER.save();
		}
	}

	private static String normalizeKeyName(String keyName) {
		return keyName == null || keyName.isBlank() ? DEFAULT_HOTKEY : keyName;
	}

	private static void saveMinecraftOptions() {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.options != null) {
			client.options.save();
		}
	}

	public static KeyMapping getToggleKey() {
		return toggleKey;
	}

	public static boolean isDetectionEnabled() {
		return EmfConfig.HANDLER.instance().offhandSwapperEnabled;
	}

	public static boolean isCooldownActive() {
		return cooldownActive && System.currentTimeMillis() < cooldownUntilMs;
	}

	public static boolean isExpireFlashActive() {
		return System.currentTimeMillis() < expireFlashUntilMs;
	}

	public static long remainingCooldownMs() {
		if (!cooldownActive) {
			return 0L;
		}
		return Math.max(0L, cooldownUntilMs - System.currentTimeMillis());
	}

	public static long storedCooldownMs() {
		return storedCooldownMs;
	}

	public static boolean hasAutominerInMainHand(Minecraft client) {
		if (client == null || client.player == null) {
			return false;
		}
		return readAutominer(client, client.player.getMainHandItem()) != null;
	}

	public static String statusText() {
		if (!isDetectionEnabled()) {
			return "Aus";
		}
		if (isCooldownActive()) {
			return formatSeconds(remainingCooldownMs() / 1000.0);
		}
		if (isExpireFlashActive()) {
			return "Bereit";
		}
		Minecraft client = Minecraft.getInstance();
		if (hasAutominerInMainHand(client)) {
			return "Bereit";
		}
		return "Kein Autominer";
	}

	private static void onClientTick(Minecraft client) {
		if (client == null) {
			resetState();
			return;
		}

		syncKeyMappingToConfig(true);

		if (client.player == null) {
			resetState();
			return;
		}

		if (client.screen == null && toggleKey != null && toggleKey.consumeClick()) {
			toggleActive(client.player);
		}

		tickPendingSound(client);

		if (!isDetectionEnabled()) {
			clearCooldownState();
			return;
		}
		if (client.screen != null || client.player.isSpectator()) {
			return;
		}

		tickCooldown(client);
	}

	private static void toggleActive(LocalPlayer player) {
		setDetectionEnabled(!isDetectionEnabled(), true);
	}

	private static void tickCooldown(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		long now = System.currentTimeMillis();
		AutominerInfo info = readAutominer(client, player.getMainHandItem());
		if (info != null) {
			storedCooldownMs = info.cooldownMs();
		} else if (!cooldownActive) {
			storedCooldownMs = 0L;
		}

		if (cooldownActive) {
			if (now >= cooldownUntilMs) {
				cooldownActive = false;
				if (!expireNotified) {
					expireNotified = true;
					expireFlashUntilMs = now + EXPIRE_FLASH_MS;
					notifyCooldownExpired(client);
				}
			}
			return;
		}

		if (info == null || storedCooldownMs <= 0L) {
			return;
		}
		if (!client.options.keyAttack.isDown()) {
			return;
		}

		cooldownActive = true;
		expireNotified = false;
		expireFlashUntilMs = 0L;
		cooldownUntilMs = now + storedCooldownMs;
	}

	private static void notifyCooldownExpired(Minecraft client) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		if (config.autominerCooldownShowTitle && client.gui != null) {
			client.gui.setTimes(0, 60, 20);
			client.gui.setTitle(Component.literal("Autominer").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			client.gui.setSubtitle(Component.literal("Cooldown abgelaufen").withStyle(ChatFormatting.AQUA));
		}
		if (!config.autominerCooldownPlaySound || client.getSoundManager() == null) {
			return;
		}
		LootAlertSound sound = config.autominerCooldownSound == null ? LootAlertSound.BELL : config.autominerCooldownSound;
		pendingSound = sound.soundEvent();
		pendingSoundPitch = sound.usesPitch() ? clampPitch(config.autominerCooldownSoundPitch) : 1.0f;
		pendingSoundVolume = 1.0f;
		pendingSoundPlays = sound.playCount();
	}

	private static void tickPendingSound(Minecraft client) {
		if (pendingSoundPlays <= 0 || client == null || client.getSoundManager() == null || pendingSound == null) {
			return;
		}
		client.getSoundManager().play(SimpleSoundInstance.forUI(pendingSound, pendingSoundPitch, pendingSoundVolume));
		pendingSoundPlays--;
		if (pendingSoundPlays <= 0) {
			pendingSound = null;
		}
	}

	private static float clampPitch(float pitch) {
		if (Float.isNaN(pitch) || pitch <= 0.0f) {
			return LootRarityAlerts.DEFAULT_PITCH;
		}
		return Math.max(0.5f, Math.min(2.0f, pitch));
	}

	private static AutominerInfo readAutominer(Minecraft client, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		String loreText = loreText(stack);
		if (!AUTOMINER.matcher(loreText).find()) {
			loreText = tooltipText(client, stack);
			if (!AUTOMINER.matcher(loreText).find()) {
				return null;
			}
		}
		Matcher cooldown = COOLDOWN.matcher(loreText);
		if (!cooldown.find()) {
			loreText = tooltipText(client, stack);
			cooldown = COOLDOWN.matcher(loreText);
			if (!cooldown.find()) {
				return null;
			}
		}
		double seconds = parseSeconds(cooldown.group(1));
		if (seconds <= 0.0) {
			return null;
		}
		return new AutominerInfo(Math.max(50L, Math.round(seconds * 1000.0)));
	}

	private static String loreText(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null || lore.lines().isEmpty()) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		for (Component line : lore.lines()) {
			if (!builder.isEmpty()) {
				builder.append('\n');
			}
			builder.append(clean(line.getString()));
		}
		return builder.toString();
	}

	private static String tooltipText(Minecraft client, ItemStack stack) {
		StringBuilder builder = new StringBuilder();
		for (Component line : stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL)) {
			if (!builder.isEmpty()) {
				builder.append('\n');
			}
			builder.append(clean(line.getString()));
		}
		return builder.toString();
	}

	private static double parseSeconds(String raw) {
		try {
			return Double.parseDouble(raw.replace(',', '.'));
		} catch (NumberFormatException ignored) {
			return -1.0;
		}
	}

	private static String formatSeconds(double seconds) {
		if (seconds < 10.0) {
			return String.format(java.util.Locale.ROOT, "%.1fsek", seconds);
		}
		return String.format(java.util.Locale.ROOT, "%.0fsek", seconds);
	}

	private static String clean(String text) {
		if (text == null) {
			return "";
		}
		return COLOR_CODES.matcher(text).replaceAll("").trim();
	}

	private static void clearCooldownState() {
		cooldownActive = false;
		expireNotified = false;
		cooldownUntilMs = 0L;
		expireFlashUntilMs = 0L;
		storedCooldownMs = 0L;
	}

	private static void resetState() {
		clearCooldownState();
		pendingSoundPlays = 0;
		pendingSound = null;
	}

	private record AutominerInfo(long cooldownMs) {
	}
}
