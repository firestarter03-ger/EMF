package net.fire.emf.client.title;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.mixin.GuiAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.NoteBlock;

import java.util.regex.Pattern;

public final class TitleAlerts {
	private static final Pattern COLOR_CODES = Pattern.compile("§x(?:§[0-9a-fA-F]){6}|§[0-9a-fk-orA-FK-OR]");
	private static final String CRAFTING_DONE_CHAT = "Crafting abgeschlossen.";
	private static final String RESOURCE_BAG_TITLE = "Resourcebag voll";
	private static final long RESOURCE_BAG_RELEASE_MS = 2000L;
	private static final int CHIME_NOTE_COUNT = 25;

	private static long resourceBagSinceMs;
	private static long lastResourceBagSetMs;
	private static int chimeNextNote = -1;
	private static float chimeVolume;

	private TitleAlerts() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TitleAlerts::tick);
	}

	public static void onSystemChat(Component message) {
		if (message == null) {
			return;
		}
		String text = clean(message.getString());
		if (text.equalsIgnoreCase(CRAFTING_DONE_CHAT) || text.equalsIgnoreCase("Crafting abgeschlossen")) {
			showCraftingDoneTitle();
		}
		LootRarityAlerts.onChat(message);
	}

	public static boolean shouldBlockTitle(Component text) {
		if (text == null || !EmfConfig.HANDLER.instance().resourceBagHideEnabled) {
			return false;
		}
		if (!isResourceBagFull(clean(text.getString()))) {
			return false;
		}

		long now = System.currentTimeMillis();
		lastResourceBagSetMs = now;
		if (resourceBagSinceMs == 0L) {
			resourceBagSinceMs = now;
		}
		return now - resourceBagSinceMs > EmfConfig.resourceBagHideAfterMs();
	}

	private static void tick(Minecraft client) {
		if (client == null) {
			return;
		}
		tickChime(client);
		LootRarityAlerts.tick(client);
		if (client.gui == null || !EmfConfig.HANDLER.instance().resourceBagHideEnabled) {
			return;
		}

		long now = System.currentTimeMillis();
		Gui gui = client.gui;
		Component title = ((GuiAccessor) gui).emf$getTitle();
		Component subtitle = ((GuiAccessor) gui).emf$getSubtitle();
		boolean showingResourceBag = isResourceBagComponent(title) || isResourceBagComponent(subtitle);

		if (showingResourceBag) {
			if (resourceBagSinceMs == 0L) {
				resourceBagSinceMs = now;
			}
			if (now - resourceBagSinceMs > EmfConfig.resourceBagHideAfterMs()) {
				gui.clearTitles();
			}
			return;
		}

		if (resourceBagSinceMs > 0L && now - lastResourceBagSetMs > RESOURCE_BAG_RELEASE_MS) {
			resourceBagSinceMs = 0L;
			lastResourceBagSetMs = 0L;
		}
	}

	private static void showCraftingDoneTitle() {
		EmfConfig config = EmfConfig.HANDLER.instance();
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}

		if (config.craftingTitleEnabled && client.gui != null) {
			client.gui.setTimes(0, 60, 20);
			client.gui.setTitle(Component.literal("Crafting Fertig").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
		}

		if (config.craftingTitleSoundEnabled) {
			playChimeSound(client, config.craftingTitleSoundVolume);
		}
	}

	private static void playChimeSound(Minecraft client, int volumePercent) {
		float volume = Math.max(0, Math.min(100, volumePercent)) / 100.0f;
		if (volume <= 0.0f || client.getSoundManager() == null) {
			return;
		}
		chimeVolume = volume;
		chimeNextNote = 0;
	}

	private static void tickChime(Minecraft client) {
		if (chimeNextNote < 0 || chimeNextNote >= CHIME_NOTE_COUNT || client.getSoundManager() == null) {
			return;
		}
		float pitch = NoteBlock.getPitchFromNote(chimeNextNote);
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), pitch, chimeVolume));
		chimeNextNote++;
		if (chimeNextNote >= CHIME_NOTE_COUNT) {
			chimeNextNote = -1;
		}
	}

	private static boolean isResourceBagComponent(Component text) {
		return text != null && isResourceBagFull(clean(text.getString()));
	}

	private static boolean isResourceBagFull(String text) {
		return text.equalsIgnoreCase(RESOURCE_BAG_TITLE);
	}

	private static String clean(String text) {
		if (text == null) {
			return "";
		}
		return COLOR_CODES.matcher(text).replaceAll("").trim();
	}
}
