package net.fire.emf.client.function;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.overlay.editor.KeyCategories;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
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
	private static final long RETURN_CONFIRM_MS = 200L;

	private static KeyMapping toggleKey;
	private static long cooldownUntilMs;
	private static long returnUntilMs;
	private static boolean waitingForCooldown;
	private static boolean waitingForReturn;
	private static long storedCooldownMs;

	private OffhandSwapper() {
	}

	public static void initialize() {
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.emf.offhand_swapper",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				KeyCategories.of("emf", "functions")
		));
		applyHotkey(EmfConfig.HANDLER.instance().offhandSwapperHotkey);
		ClientTickEvents.END_CLIENT_TICK.register(OffhandSwapper::onClientTick);
	}

	public static void applyHotkey(String keyName) {
		if (toggleKey == null) {
			return;
		}
		try {
			toggleKey.setKey(InputConstants.getKey(keyName == null || keyName.isBlank() ? DEFAULT_HOTKEY : keyName));
			KeyMapping.resetMapping();
		} catch (RuntimeException ignored) {
			toggleKey.setKey(InputConstants.getKey(DEFAULT_HOTKEY));
			KeyMapping.resetMapping();
		}
	}

	public static void syncHotkeyToConfig() {
		if (toggleKey != null) {
			EmfConfig.HANDLER.instance().offhandSwapperHotkey = toggleKey.saveString();
		}
	}

	public static KeyMapping getToggleKey() {
		return toggleKey;
	}

	private static void onClientTick(Minecraft client) {
		if (client == null || client.player == null) {
			resetCycle();
			return;
		}

		if (client.screen == null && toggleKey != null && toggleKey.consumeClick()) {
			toggleActive(client.player);
		}

		if (!EmfConfig.HANDLER.instance().offhandSwapperEnabled) {
			if (waitingForCooldown) {
				swapOffhand(client);
			}
			resetCycle();
			return;
		}
		if (client.screen != null || client.player.isSpectator()) {
			return;
		}

		tickSwapCycle(client);
	}

	private static void toggleActive(LocalPlayer player) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		config.offhandSwapperEnabled = !config.offhandSwapperEnabled;
		EmfConfig.HANDLER.save();
		boolean enabled = config.offhandSwapperEnabled;
		ChatFormatting stateColor = enabled ? ChatFormatting.GREEN : ChatFormatting.RED;
		String stateText = enabled ? "aktiviert." : "deaktiviert.";
		Minecraft.getInstance().gui.getChat().addClientSystemMessage(
				Component.empty()
						.append(Component.literal("[EMF]").withStyle(ChatFormatting.GOLD))
						.append(Component.literal(" Offhand Swapper " + stateText).withStyle(stateColor))
		);
		if (!enabled) {
			if (waitingForCooldown) {
				swapOffhand(Minecraft.getInstance());
			}
			resetCycle();
		}
	}

	private static void tickSwapCycle(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		if (waitingForCooldown) {
			if (System.currentTimeMillis() >= cooldownUntilMs) {
				swapOffhand(client);
				waitingForCooldown = false;
				waitingForReturn = true;
				returnUntilMs = System.currentTimeMillis() + RETURN_CONFIRM_MS;
			}
			return;
		}

		AutominerInfo info = readAutominer(client, player.getMainHandItem());
		if (info != null) {
			storedCooldownMs = info.cooldownMs;
			if (waitingForReturn) {
				if (System.currentTimeMillis() < returnUntilMs) {
					return;
				}
				waitingForReturn = false;
			}
		} else if (waitingForReturn) {
			returnUntilMs = System.currentTimeMillis() + RETURN_CONFIRM_MS;
			return;
		} else {
			storedCooldownMs = 0L;
			return;
		}

		if (!client.options.keyAttack.isDown()) {
			return;
		}

		swapOffhand(client);
		waitingForCooldown = true;
		cooldownUntilMs = System.currentTimeMillis() + storedCooldownMs;
	}

	private static void swapOffhand(Minecraft client) {
		if (client.player == null || client.player.isSpectator() || client.getConnection() == null) {
			return;
		}
		client.getConnection().send(new ServerboundPlayerActionPacket(
				ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
				BlockPos.ZERO,
				Direction.DOWN
		));
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

	private static String clean(String text) {
		if (text == null) {
			return "";
		}
		return COLOR_CODES.matcher(text).replaceAll("").trim();
	}

	private static void resetCycle() {
		waitingForCooldown = false;
		waitingForReturn = false;
		cooldownUntilMs = 0L;
		returnUntilMs = 0L;
	}

	private record AutominerInfo(long cooldownMs) {
	}
}
