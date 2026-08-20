package net.fire.emf.client.resource;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.mixin.DialogScreenAccessor;
import net.fire.emf.client.overlay.editor.OverlayEditorUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CollectionScanner {
	private static final Pattern COLLECTION = Pattern.compile("(?i)Collection:\\s*([0-9][0-9.,]*)");
	private static final Pattern GOAL_LINE = Pattern.compile(
			"(?i)([0-9][0-9.,]*)\\s*->\\s*(Freigeschaltet|Nicht erreicht)");
	private static final int WAIT_TICKS = 40;
	private static final String RESOURCEBAG_BUTTON = "resourcebag";
	private static final String OPEN_RESOURCEBAG_COMMAND = "trigger t_menu set 2003";

	private enum Phase {
		IDLE,
		WAIT_RESOURCEBAG
	}

	private static Phase phase = Phase.IDLE;
	private static int waitTicks;
	private static int forceCloseTicks;
	private static long nextScanAtMs;
	private static boolean hidingScreen;
	private static String lastGoalParse;
	private static String lastBagParse;

	private CollectionScanner() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(CollectionScanner::tick);
		ClientTickEvents.START_CLIENT_TICK.register(CollectionScannerMovement::tickCombatPassthrough);
	}

	public static boolean shouldHideScreen(Screen screen) {
		return hidingScreen && screen instanceof DialogScreen<?>;
	}

	public static boolean isScanInProgress() {
		return phase != Phase.IDLE || forceCloseTicks > 0;
	}

	public static boolean shouldAllowMovement() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || !hidingScreen) {
			return false;
		}
		if (!(client.screen instanceof DialogScreen<?> dialog)) {
			return false;
		}
		return shouldHideScreen(dialog);
	}

	public static boolean shouldPreserveInput() {
		return shouldAllowMovement();
	}

	public static void onForeignScreenOpened(Screen screen) {
		if (screen == null || screen instanceof DialogScreen<?>) {
			return;
		}
		if (!isScanInProgress() && !hidingScreen) {
			return;
		}
		cancelScanWithoutClosing();
	}

	public static boolean onInputWhileScanning() {
		if (phase == Phase.IDLE && forceCloseTicks <= 0) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || !(client.screen instanceof DialogScreen<?>)) {
			return false;
		}
		DialogScreen<?> dialog = (DialogScreen<?>) client.screen;
		if (isElementsMenu(dialog)) {
			cancelScanWithoutClosing();
			return false;
		}
		// Unsichtbarer Scan: Eingaben schlucken, damit das versteckte Dialogfenster sie nicht bekommt.
		if (hidingScreen) {
			return true;
		}
		abortAndClose(client);
		return true;
	}

	private static void tick(Minecraft client) {
		if (client == null || client.getWindow() == null) {
			resetState(false);
			return;
		}

		if ((isScanInProgress() || hidingScreen) && client.screen != null && !(client.screen instanceof DialogScreen<?>)) {
			cancelScanWithoutClosing();
			return;
		}

		if (forceCloseTicks > 0) {
			if (client.screen instanceof DialogScreen<?>) {
				closeDialogChain(client);
				hidingScreen = true;
				forceCloseTicks--;
				return;
			}
			forceCloseTicks--;
			if (forceCloseTicks <= 0) {
				hidingScreen = false;
			}
		}

		if (phase == Phase.IDLE && forceCloseTicks <= 0 && !(client.screen instanceof DialogScreen<?>)) {
			hidingScreen = false;
		}

		parseCollectionDetail(client);
		parseResourcebagIfOpen(client);

		if (client.player == null || client.level == null
				|| !EmfConfig.HANDLER.instance().collectionScannerEnabled
				|| OverlayEditorUtility.isOverlayEditorOpen()) {
			if (phase != Phase.IDLE) {
				abortAndClose(client);
			}
			return;
		}

		if (phase == Phase.IDLE) {
			if (client.screen != null || System.currentTimeMillis() < nextScanAtMs) {
				return;
			}
			if (!openResourcebag(client)) {
				cooldown();
				return;
			}
			phase = Phase.WAIT_RESOURCEBAG;
			waitTicks = WAIT_TICKS;
			return;
		}

		waitTicks--;
		if (waitTicks < 0) {
			abortAndClose(client);
			return;
		}

		if (phase == Phase.WAIT_RESOURCEBAG) {
			DialogScreen<?> dialogScreen = dialogScreen(client.screen);
			if (dialogScreen == null) {
				return;
			}
			if (!isResourcebag(dialogScreen)) {
				cancelScanWithoutClosing();
				return;
			}
			Map<String, Long> values = readCollections(dialogScreen);
			saveResourcebag(values);
			abortAndClose(client);
		}
	}

	private static void cancelScanWithoutClosing() {
		phase = Phase.IDLE;
		waitTicks = 0;
		forceCloseTicks = 0;
		hidingScreen = false;
		cooldown();
	}

	private static boolean openResourcebag(Minecraft client) {
		if (client.player == null || client.player.connection == null) {
			return false;
		}
		hidingScreen = true;
		client.player.connection.sendCommand(OPEN_RESOURCEBAG_COMMAND);
		return true;
	}

	private static Map<String, Long> readCollections(DialogScreen<?> dialogScreen) {
		Map<String, Long> values = new LinkedHashMap<>();
		Dialog dialog = ((DialogScreenAccessor) dialogScreen).emf$getDialog();
		if (dialog == null) {
			return values;
		}
		for (DialogBody body : dialog.common().body()) {
			if (!(body instanceof ItemBody itemBody)) {
				continue;
			}
			String itemId;
			try {
				ItemStack stack = itemBody.item().create();
				if (stack == null || stack.isEmpty()) {
					continue;
				}
				itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			} catch (RuntimeException ignored) {
				continue;
			}
			if ("minecraft:barrier".equals(itemId)) {
				continue;
			}
			String name = ResourceNameMapping.nameFor(itemId);
			if (name == null) {
				continue;
			}
			String hover = hoverText(itemBody);
			Long collection = parseCollection(hover);
			if (collection == null) {
				continue;
			}
			values.put(name, collection);
		}
		return values;
	}

	private static void parseCollectionDetail(Minecraft client) {
		DialogScreen<?> dialogScreen = dialogScreen(client.screen);
		if (dialogScreen == null) {
			lastGoalParse = null;
			return;
		}
		Dialog dialog = ((DialogScreenAccessor) dialogScreen).emf$getDialog();
		if (dialog == null) {
			return;
		}
		Long current = null;
		boolean collectionHeading = false;
		for (DialogBody body : dialog.common().body()) {
			if (!(body instanceof PlainMessage message)) {
				continue;
			}
			String contents = text(message.contents());
			if (!normalize(contents).contains("collection:")) {
				continue;
			}
			collectionHeading = true;
			current = parseCollection(contents);
		}
		if (!collectionHeading) {
			return;
		}

		String resourceName = null;
		List<Long> goals = new ArrayList<>();
		for (DialogBody body : dialog.common().body()) {
			if (!(body instanceof ItemBody itemBody)) {
				continue;
			}
			String name = mappedItemName(itemBody);
			if (name == null) {
				continue;
			}
			resourceName = name;
			String description = itemBody.description().isEmpty() ? "" : text(itemBody.description().get().contents());
			Matcher matcher = GOAL_LINE.matcher(description);
			while (matcher.find()) {
				Long goal = parseNumber(matcher.group(1));
				if (goal != null) {
					goals.add(goal);
				}
			}
		}
		if (resourceName == null) {
			return;
		}

		String signature = resourceName + "|" + (current == null ? "-" : current) + "|" + goals;
		if (signature.equals(lastGoalParse)) {
			return;
		}
		lastGoalParse = signature;
		if (current != null) {
			CollectionStore.mergeAndSave(Map.of(resourceName, current));
		}
		CollectionGoalStore.mergeAndSave(resourceName, goals);
		if (EmfConfig.debugDialogScreen()) {
			ElementsMoreFeatures.LOGGER.info("[Collections] Ziel für {} gespeichert: {} (aktuell {})",
					resourceName, goals, current);
		}
	}

	private static void parseResourcebagIfOpen(Minecraft client) {
		if (hidingScreen || phase != Phase.IDLE || forceCloseTicks > 0) {
			return;
		}
		DialogScreen<?> dialogScreen = dialogScreen(client.screen);
		if (dialogScreen == null || !isResourcebag(dialogScreen)) {
			lastBagParse = null;
			return;
		}
		saveResourcebag(readCollections(dialogScreen));
	}

	private static void saveResourcebag(Map<String, Long> values) {
		if (values == null || values.isEmpty()) {
			return;
		}
		String signature = values.toString();
		if (signature.equals(lastBagParse)) {
			return;
		}
		lastBagParse = signature;
		CollectionStore.mergeAndSave(values);
		if (EmfConfig.debugDialogScreen()) {
			ElementsMoreFeatures.LOGGER.info("[Collections] {} Einträge gespeichert in {}", values.size(), CollectionStore.file());
		}
	}

	private static String mappedItemName(ItemBody itemBody) {
		try {
			ItemStack stack = itemBody.item().create();
			if (stack == null || stack.isEmpty()) {
				return null;
			}
			String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			if ("minecraft:barrier".equals(itemId)) {
				return null;
			}
			return ResourceNameMapping.nameFor(itemId);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static Long parseNumber(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return Long.parseLong(raw.replace(".", "").replace(",", ""));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String hoverText(ItemBody itemBody) {
		if (itemBody.description().isEmpty()) {
			return "";
		}
		StringBuilder text = new StringBuilder();
		itemBody.description().get().contents().visit((style, ignored) -> {
			HoverEvent hover = style.getHoverEvent();
			if (hover instanceof HoverEvent.ShowText showText) {
				String value = showText.value().getString();
				if (!value.isBlank()) {
					if (!text.isEmpty()) {
						text.append('\n');
					}
					text.append(value);
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		return text.toString();
	}

	private static Long parseCollection(String hover) {
		if (hover == null || hover.isBlank()) {
			return null;
		}
		Matcher matcher = COLLECTION.matcher(hover);
		if (!matcher.find()) {
			return null;
		}
		try {
			return parseNumber(matcher.group(1));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static boolean isElementsMenu(DialogScreen<?> dialogScreen) {
		String title = text(dialogScreen.getTitle());
		Dialog dialog = ((DialogScreenAccessor) dialogScreen).emf$getDialog();
		if (dialog != null) {
			title = title + " " + text(dialog.common().title());
		}
		String normalized = normalize(title);
		boolean hasElements = normalized.contains("element") || normalized.contains("elemts");
		boolean hasMenu = normalized.contains("menü") || normalized.contains("menu") || normalized.contains("menue");
		return hasElements && hasMenu;
	}

	private static boolean isResourcebag(DialogScreen<?> dialogScreen) {
		Dialog dialog = ((DialogScreenAccessor) dialogScreen).emf$getDialog();
		if (dialog == null) {
			return false;
		}
		if (normalize(text(dialog.common().title())).contains(RESOURCEBAG_BUTTON)
				|| normalize(text(dialogScreen.getTitle())).contains(RESOURCEBAG_BUTTON)) {
			return true;
		}
		for (DialogBody body : dialog.common().body()) {
			if (body instanceof PlainMessage message && normalize(text(message.contents())).contains(RESOURCEBAG_BUTTON)) {
				return true;
			}
		}
		return false;
	}

	private static DialogScreen<?> dialogScreen(Screen screen) {
		return screen instanceof DialogScreen<?> dialogScreen ? dialogScreen : null;
	}

	private static void abortAndClose(Minecraft client) {
		hidingScreen = true;
		forceCloseTicks = WAIT_TICKS;
		closeDialogChain(client);
		resetState(true);
	}

	private static void closeDialogChain(Minecraft client) {
		if (client == null) {
			return;
		}
		int safety = 8;
		while (safety-- > 0 && client.screen instanceof DialogScreen<?>) {
			Screen current = client.screen;
			current.onClose();
			if (client.screen == current) {
				client.setScreen(null);
				break;
			}
		}
		if (client.screen instanceof DialogScreen<?>) {
			client.setScreen(null);
		}
	}

	private static void resetState(boolean keepHiding) {
		phase = Phase.IDLE;
		waitTicks = 0;
		if (!keepHiding && forceCloseTicks <= 0) {
			hidingScreen = false;
		}
		cooldown();
	}

	private static void cooldown() {
		int seconds = Math.max(5, EmfConfig.HANDLER.instance().collectionScannerIntervalSeconds);
		nextScanAtMs = System.currentTimeMillis() + seconds * 1000L;
	}

	private static String text(Component component) {
		return component == null ? "" : component.getString();
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
	}
}
