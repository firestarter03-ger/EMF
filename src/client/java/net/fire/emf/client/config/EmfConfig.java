package net.fire.emf.client.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumDropdownControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.function.OffhandSwapper;
import net.fire.emf.client.title.LootAlertSound;
import net.fire.emf.client.title.LootRarity;
import net.fire.emf.client.title.LootRarityAlerts;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EmfConfig {
	public static final ConfigClassHandler<EmfConfig> HANDLER = ConfigClassHandler.createBuilder(EmfConfig.class)
			.id(ElementsMoreFeatures.id("config"))
			.serializer(config -> GsonConfigSerializerBuilder.create(config)
					.setPath(EmfDataFolder.configFile())
					.setJson5(false)
					.build())
			.build();

	@SerialEntry
	public boolean debugSkillXp = false;

	@SerialEntry
	public boolean debugDialogScreen = false;

	@SerialEntry
	public boolean showSkillXpOverlay = true;

	@SerialEntry
	public boolean skillXpOverlayEnabled = true;

	@SerialEntry
	public int skillXpOverlayX = 8;

	@SerialEntry
	public int skillXpOverlayY = 8;

	@SerialEntry
	public float skillXpOverlayScale = 1.0f;

	@SerialEntry
	public boolean skillXpShowBackground = true;

	@SerialEntry
	public boolean skillXpAlwaysShow = false;

	@SerialEntry
	public boolean skillXpShowXpRate = true;

	@SerialEntry
	public boolean skillXpShowNextLevel = true;

	@SerialEntry
	public boolean skillXpShowResources = true;

	@SerialEntry
	public boolean skillXpShowResourceTarget = false;

	@SerialEntry
	public long skillXpResourceTarget = 0;

	@SerialEntry
	public boolean skillXpReachedBlink = false;

	@SerialEntry
	public boolean showLevelTrackerOverlay = true;

	@SerialEntry
	public boolean levelTrackerOverlayEnabled = true;

	@SerialEntry
	public int levelTrackerOverlayX = 8;

	@SerialEntry
	public int levelTrackerOverlayY = 70;

	@SerialEntry
	public float levelTrackerOverlayScale = 1.0f;

	@SerialEntry
	public boolean levelTrackerShowBackground = true;

	@SerialEntry
	public boolean levelTrackerAlwaysShow = false;

	@SerialEntry
	public boolean levelTrackerShowLevelRate = true;

	@SerialEntry
	public boolean levelTrackerShowTarget = false;

	@SerialEntry
	public long levelTrackerTarget = 0;

	@SerialEntry
	public boolean levelTrackerReachedBlink = false;

	@SerialEntry
	public boolean showSkillFruitOverlay = true;

	@SerialEntry
	public boolean skillFruitOverlayEnabled = true;

	@SerialEntry
	public int skillFruitOverlayX = 8;

	@SerialEntry
	public int skillFruitOverlayY = 132;

	@SerialEntry
	public float skillFruitOverlayScale = 1.0f;

	@SerialEntry
	public boolean skillFruitShowBackground = true;

	@SerialEntry
	public boolean skillFruitAlwaysShow = false;

	@SerialEntry
	public String skillFruitName = "";

	@SerialEntry
	public long skillFruitCooldownUntilMs = 0L;

	@SerialEntry
	public List<SkillFruitTimer> skillFruitTimers = new ArrayList<>();

	@SerialEntry
	public boolean showAutominerCooldownOverlay = true;

	@SerialEntry
	public boolean autominerCooldownOverlayEnabled = true;

	@SerialEntry
	public int autominerCooldownOverlayX = 8;

	@SerialEntry
	public int autominerCooldownOverlayY = 194;

	@SerialEntry
	public float autominerCooldownOverlayScale = 1.0f;

	@SerialEntry
	public boolean autominerCooldownShowBackground = true;

	@SerialEntry
	public boolean autominerCooldownShowTitle = false;

	@SerialEntry
	public boolean autominerCooldownPlaySound = true;

	@SerialEntry
	public LootAlertSound autominerCooldownSound = LootAlertSound.BELL;

	@SerialEntry
	public float autominerCooldownSoundPitch = LootRarityAlerts.DEFAULT_PITCH;

	@SerialEntry
	public boolean craftingTitleEnabled = true;

	@SerialEntry
	public boolean craftingTitleSoundEnabled = true;

	@SerialEntry
	public int craftingTitleSoundVolume = 100;

	@SerialEntry
	public boolean lootRarityAlertsEnabled = true;

	@SerialEntry
	public boolean lootRarityTitleEnabled = true;

	@SerialEntry
	public int lootRaritySoundVolume = 100;

	@SerialEntry
	public LootRarity lootRarityMinimum = LootRarity.EPIC;

	@SerialEntry
	public LootAlertSound lootRaritySound = LootAlertSound.CHALLENGE_COMPLETE;

	@SerialEntry
	public float lootRaritySoundPitch = LootRarityAlerts.DEFAULT_PITCH;

	@SerialEntry
	public boolean resourceBagHideEnabled = true;

	@SerialEntry
	public int resourceBagHideAfterSeconds = 3;

	@SerialEntry
	public boolean offhandSwapperEnabled = false;

	@SerialEntry
	public String offhandSwapperHotkey = "key.keyboard.r";

	@SerialEntry
	public boolean collectionScannerEnabled = true;

	@SerialEntry
	public int collectionScannerIntervalSeconds = 60;

	public static boolean debugSkillXp() {
		return HANDLER.instance().debugSkillXp;
	}

	public static boolean debugDialogScreen() {
		return HANDLER.instance().debugDialogScreen;
	}

	public static boolean skillXpOverlayVisible() {
		EmfConfig config = HANDLER.instance();
		return config.showSkillXpOverlay && config.skillXpOverlayEnabled;
	}

	public static boolean skillXpNeedsXpRate() {
		EmfConfig config = HANDLER.instance();
		return config.skillXpShowXpRate || config.skillXpShowNextLevel;
	}

	public static boolean skillXpNeedsResourceRate() {
		EmfConfig config = HANDLER.instance();
		return config.skillXpShowResources || config.skillXpShowResourceTarget;
	}

	public static boolean levelTrackerOverlayVisible() {
		EmfConfig config = HANDLER.instance();
		return config.showLevelTrackerOverlay && config.levelTrackerOverlayEnabled;
	}

	public static boolean levelTrackerNeedsXpRate() {
		EmfConfig config = HANDLER.instance();
		return config.levelTrackerShowLevelRate || config.levelTrackerShowTarget;
	}

	public static boolean skillFruitOverlayVisible() {
		EmfConfig config = HANDLER.instance();
		return config.showSkillFruitOverlay && config.skillFruitOverlayEnabled;
	}

	public static boolean autominerCooldownOverlayVisible() {
		return OffhandSwapper.isDetectionEnabled();
	}

	public static int resourceBagHideAfterMs() {
		return Math.max(1, HANDLER.instance().resourceBagHideAfterSeconds) * 1000;
	}

	public static Screen createScreen(Screen parent) {
		Option<Integer> resourceBagHideAfter = Option.<Integer>createBuilder()
				.name(Component.literal("Ausblenden nach"))
				.description(OptionDescription.of(Component.literal(
						"Sekunden, die der Titel „Resourcebag voll“ sichtbar bleiben darf, bevor er blockiert wird.")))
				.binding(3, () -> HANDLER.instance().resourceBagHideAfterSeconds, value -> HANDLER.instance().resourceBagHideAfterSeconds = Math.max(1, value))
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
						.min(1)
						.max(600)
						.valueFormatter(value -> Component.literal(value + "sek")))
				.available(HANDLER.instance().resourceBagHideEnabled)
				.build();

		Option<Boolean> resourceBagHide = Option.<Boolean>createBuilder()
				.name(Component.literal("Ausblenden an/aus"))
				.description(OptionDescription.of(Component.literal(
						"Blockiert den Titel „Resourcebag voll“, wenn er zu lange angezeigt wird.")))
				.binding(true, () -> HANDLER.instance().resourceBagHideEnabled, value -> HANDLER.instance().resourceBagHideEnabled = value)
				.controller(TickBoxControllerBuilder::create)
				.addListener((option, event) -> resourceBagHideAfter.setAvailable(option.pendingValue()))
				.build();

		Option<Integer> craftingTitleSoundVolume = Option.<Integer>createBuilder()
				.name(Component.literal("Sound Lautstärke"))
				.description(OptionDescription.of(Component.literal(
						"Lautstärke des „Crafting-Abgeschlossen“-Sounds in Prozent.")))
				.binding(100, () -> HANDLER.instance().craftingTitleSoundVolume, value -> HANDLER.instance().craftingTitleSoundVolume = value)
				.controller(opt -> IntegerSliderControllerBuilder.create(opt)
						.range(0, 100)
						.step(1)
						.valueFormatter(value -> Component.literal(value + "%")))
				.available(HANDLER.instance().craftingTitleSoundEnabled)
				.build();

		Option<Boolean> craftingTitleSound = Option.<Boolean>createBuilder()
				.name(Component.literal("Sound ein/aus"))
				.description(OptionDescription.of(Component.literal(
						"Spielt einen Sound, wenn Crafting abgeschlossen ist.")))
				.binding(true, () -> HANDLER.instance().craftingTitleSoundEnabled, value -> HANDLER.instance().craftingTitleSoundEnabled = value)
				.controller(TickBoxControllerBuilder::create)
				.addListener((option, event) -> craftingTitleSoundVolume.setAvailable(option.pendingValue()))
				.build();

		Option<Boolean> lootRarityTitle = Option.<Boolean>createBuilder()
				.name(Component.literal("Titel ein/aus"))
				.description(OptionDescription.of(Component.literal(
						"Zeigt die Rarity und den Itemnamen als Titel, wenn ein passendes Loot gedroppt wird.")))
				.binding(true, () -> HANDLER.instance().lootRarityTitleEnabled, value -> HANDLER.instance().lootRarityTitleEnabled = value)
				.controller(TickBoxControllerBuilder::create)
				.available(HANDLER.instance().lootRarityAlertsEnabled)
				.build();

		Option<Integer> lootRarityVolume = Option.<Integer>createBuilder()
				.name(Component.literal("Sound Lautstärke"))
				.description(OptionDescription.of(Component.literal(
						"Lautstärke des Loot-Rarity Sounds in Prozent.")))
				.binding(100, () -> HANDLER.instance().lootRaritySoundVolume, value -> HANDLER.instance().lootRaritySoundVolume = value)
				.controller(opt -> IntegerSliderControllerBuilder.create(opt)
						.range(0, 100)
						.step(1)
						.valueFormatter(value -> Component.literal(value + "%")))
				.available(HANDLER.instance().lootRarityAlertsEnabled)
				.build();

		Option<LootRarity> lootRarityMinimum = Option.<LootRarity>createBuilder()
				.name(Component.literal("Ab Rarity"))
				.description(OptionDescription.of(Component.literal(
						"Benachrichtigung kommt erst AB dieser Rarity und höher:\nCommon → Unique.")))
				.binding(LootRarity.EPIC, () -> HANDLER.instance().lootRarityMinimum, value -> HANDLER.instance().lootRarityMinimum = value)
				.controller(opt -> EnumDropdownControllerBuilder.create(opt)
						.formatValue(value -> Component.literal(value.displayName()).withStyle(value.color())))
				.available(HANDLER.instance().lootRarityAlertsEnabled)
				.build();

		Option<Float> lootRarityPitch = Option.<Float>createBuilder()
				.name(Component.literal("Pitch"))
				.description(OptionDescription.of(Component.literal(
						"Tonhöhe der Note-Block-Sounds. Bei Challenge Complete ohne Wirkung.")))
				.binding(LootRarityAlerts.DEFAULT_PITCH, () -> HANDLER.instance().lootRaritySoundPitch, value -> HANDLER.instance().lootRaritySoundPitch = value)
				.controller(opt -> FloatSliderControllerBuilder.create(opt)
						.range(0.5f, 2.0f)
						.step(0.001f)
						.formatValue(value -> Component.literal(String.format(Locale.US, "%.3f", value))))
				.available(HANDLER.instance().lootRarityAlertsEnabled
						&& (HANDLER.instance().lootRaritySound == null || HANDLER.instance().lootRaritySound.usesPitch()))
				.build();

		@SuppressWarnings("unchecked")
		final Option<Boolean>[] lootAlerts = new Option[1];

		Option<LootAlertSound> lootRaritySound = Option.<LootAlertSound>createBuilder()
				.name(Component.literal("Sound"))
				.description(OptionDescription.of(Component.literal(
						"Notenblock oder Challenge-Complete-Sound.\nStandard: Challenge Complete.")))
				.binding(LootAlertSound.CHALLENGE_COMPLETE, () -> HANDLER.instance().lootRaritySound, value -> HANDLER.instance().lootRaritySound = value)
				.controller(opt -> EnumDropdownControllerBuilder.create(opt)
						.formatValue(value -> Component.literal(value.displayName())))
				.available(HANDLER.instance().lootRarityAlertsEnabled)
				.addListener((option, event) -> lootRarityPitch.setAvailable(
						lootAlerts[0] != null && lootAlerts[0].pendingValue() && option.pendingValue().usesPitch()))
				.build();

		Option<Boolean> lootRarityAlerts = Option.<Boolean>createBuilder()
				.name(Component.literal("Benachrichtigungen an/aus"))
				.description(OptionDescription.of(Component.literal(
						"Erkennt Chat-Nachrichten „Loot: …“ und spielt je nach Rarity Sound/Titel.")))
				.binding(true, () -> HANDLER.instance().lootRarityAlertsEnabled, value -> HANDLER.instance().lootRarityAlertsEnabled = value)
				.controller(TickBoxControllerBuilder::create)
				.addListener((option, event) -> {
					boolean enabled = option.pendingValue();
					lootRarityTitle.setAvailable(enabled);
					lootRarityVolume.setAvailable(enabled);
					lootRarityMinimum.setAvailable(enabled);
					lootRaritySound.setAvailable(enabled);
					lootRarityPitch.setAvailable(enabled && lootRaritySound.pendingValue().usesPitch());
				})
				.build();
		lootAlerts[0] = lootRarityAlerts;

		Option<Float> autominerPitch = Option.<Float>createBuilder()
				.name(Component.literal("Sound Höhe"))
				.description(OptionDescription.of(Component.literal(
						"Tonhöhe der Note-Block-Sounds. Bei Challenge Complete ohne Wirkung.")))
				.binding(LootRarityAlerts.DEFAULT_PITCH, () -> HANDLER.instance().autominerCooldownSoundPitch, value -> HANDLER.instance().autominerCooldownSoundPitch = value)
				.controller(opt -> FloatSliderControllerBuilder.create(opt)
						.range(0.5f, 2.0f)
						.step(0.001f)
						.formatValue(value -> Component.literal(String.format(Locale.US, "%.3f", value))))
				.available(HANDLER.instance().offhandSwapperEnabled
						&& HANDLER.instance().autominerCooldownPlaySound
						&& (HANDLER.instance().autominerCooldownSound == null || HANDLER.instance().autominerCooldownSound.usesPitch()))
				.build();

		@SuppressWarnings("unchecked")
		final Option<Boolean>[] autominerSoundToggle = new Option[1];

		Option<LootAlertSound> autominerSound = Option.<LootAlertSound>createBuilder()
				.name(Component.literal("Sound"))
				.description(OptionDescription.of(Component.literal(
						"Notenblock oder Challenge-Complete-Sound.\nStandard: Bell.")))
				.binding(LootAlertSound.BELL, () -> HANDLER.instance().autominerCooldownSound, value -> HANDLER.instance().autominerCooldownSound = value)
				.controller(opt -> EnumDropdownControllerBuilder.create(opt)
						.formatValue(value -> Component.literal(value.displayName())))
				.available(HANDLER.instance().offhandSwapperEnabled && HANDLER.instance().autominerCooldownPlaySound)
				.addListener((option, event) -> autominerPitch.setAvailable(
						HANDLER.instance().offhandSwapperEnabled
								&& autominerSoundToggle[0] != null
								&& autominerSoundToggle[0].pendingValue()
								&& option.pendingValue().usesPitch()))
				.build();

		Option<Boolean> autominerSoundEnabled = Option.<Boolean>createBuilder()
				.name(Component.literal("Sound an/aus"))
				.description(OptionDescription.of(Component.literal(
						"Spielt einen Sound, wenn der Autominer-Cooldown abgelaufen ist.")))
				.binding(true, () -> HANDLER.instance().autominerCooldownPlaySound, value -> HANDLER.instance().autominerCooldownPlaySound = value)
				.controller(TickBoxControllerBuilder::create)
				.available(HANDLER.instance().offhandSwapperEnabled)
				.addListener((option, event) -> {
					boolean enabled = HANDLER.instance().offhandSwapperEnabled && option.pendingValue();
					autominerSound.setAvailable(enabled);
					autominerPitch.setAvailable(enabled && autominerSound.pendingValue().usesPitch());
				})
				.build();
		autominerSoundToggle[0] = autominerSoundEnabled;

		Option<String> autominerHotkey = Option.<String>createBuilder()
				.name(Component.literal("Hotkey"))
				.description(OptionDescription.of(Component.literal(
						"Taste zum De-/Aktivieren der Autominer-Erkennung. Klicken, dann Taste drücken.")))
				.binding("key.keyboard.r", OffhandSwapper::currentHotkeyName, OffhandSwapper::setHotkeyFromConfig)
				.customController(KeyBindController::new)
				.build();

		Option<Boolean> autominerDetection = Option.<Boolean>createBuilder()
				.name(Component.literal("Autominer Erkennung an/aus"))
				.description(OptionDescription.of(Component.literal(
						"Erkennt Autominer in der Mainhand und startet beim Abbauen den Cooldown.\nIm Spiel per Hotkey oder Overlay-Editor umschaltbar.\nOverlay-Optionen findest du im Overlay-Editor.")))
				.binding(false, () -> HANDLER.instance().offhandSwapperEnabled, value -> OffhandSwapper.setDetectionEnabled(value, false))
				.controller(TickBoxControllerBuilder::create)
				.addListener((option, event) -> {
					boolean enabled = option.pendingValue();
					autominerSoundEnabled.setAvailable(enabled);
					boolean soundOn = enabled && autominerSoundEnabled.pendingValue();
					autominerSound.setAvailable(soundOn);
					autominerPitch.setAvailable(soundOn && autominerSound.pendingValue().usesPitch());
				})
				.build();

		Option<Integer> collectionInterval = Option.<Integer>createBuilder()
				.name(Component.literal("Intervall"))
				.description(OptionDescription.of(Component.literal(
						"Wartezeit in Sekunden zwischen dem Auslesen. Läuft nur bei Farming, Mining oder Foraging und stoppt nach 5 Minuten ohne diese Skills.")))
				.binding(60, () -> HANDLER.instance().collectionScannerIntervalSeconds, value -> HANDLER.instance().collectionScannerIntervalSeconds = Math.max(5, value))
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
						.min(5)
						.max(600)
						.valueFormatter(value -> Component.literal(value + "sek")))
				.available(HANDLER.instance().collectionScannerEnabled)
				.build();

		Option<Boolean> collectionScanner = Option.<Boolean>createBuilder()
				.name(Component.literal("Automatisch auslesen an/aus"))
				.description(OptionDescription.of(Component.literal(
						"Collection automatisch auslesen, sobald Farming, Mining oder Foraging erkannt wird. Stoppt nach 5 Minuten ohne diese Skills.")))
				.binding(true, () -> HANDLER.instance().collectionScannerEnabled, value -> HANDLER.instance().collectionScannerEnabled = value)
				.controller(TickBoxControllerBuilder::create)
				.addListener((option, event) -> collectionInterval.setAvailable(option.pendingValue()))
				.build();

		return YetAnotherConfigLib.createBuilder()
				.title(Component.literal("Elements More Features"))
				.category(ConfigCategory.createBuilder()
						.name(Component.literal("Benachrichtigungen"))
						.tooltip(Component.literal("Optionen für Crafting-, Loot-, Autominer- und Resourcebag-Benachrichtigungen"))
						.group(OptionGroup.createBuilder()
								.name(Component.literal("Crafting abgeschlossen"))
								.option(Option.<Boolean>createBuilder()
										.name(Component.literal("Titel ein/aus"))
										.description(OptionDescription.of(Component.literal(
												"Zeigt „Crafting Fertig“ als Titel, wenn im Chat „Crafting abgeschlossen.“ erscheint.")))
										.binding(true, () -> HANDLER.instance().craftingTitleEnabled, value -> HANDLER.instance().craftingTitleEnabled = value)
										.controller(TickBoxControllerBuilder::create)
										.build())
								.option(craftingTitleSound)
								.option(craftingTitleSoundVolume)
								.build())
						.group(OptionGroup.createBuilder()
								.name(Component.literal("Loot Rarity Benachrichtigungen"))
								.option(lootRarityAlerts)
								.option(lootRarityTitle)
								.option(lootRarityVolume)
								.option(lootRarityMinimum)
								.option(lootRaritySound)
								.option(lootRarityPitch)
								.build())
						.group(OptionGroup.createBuilder()
								.name(Component.literal("Resourcebag voll"))
								.option(resourceBagHide)
								.option(resourceBagHideAfter)
								.build())
						.group(OptionGroup.createBuilder()
								.name(Component.literal("Autominer Erkennung"))
								.option(autominerDetection)
								.option(autominerHotkey)
								.option(autominerSoundEnabled)
								.option(autominerSound)
								.option(autominerPitch)
								.build())
						.build())
				.category(ConfigCategory.createBuilder()
						.name(Component.literal("Funktionen"))
						.tooltip(Component.literal("Zusätzliche Spiel-Funktionen"))
						.group(OptionGroup.createBuilder()
								.name(Component.literal("Collections Auslesen"))
								.option(collectionScanner)
								.option(collectionInterval)
								.build())
						.build())
				.category(ConfigCategory.createBuilder()
						.name(Component.literal("Debug"))
						.tooltip(Component.literal("Debug-Ausgaben für Entwicklung und Fehlersuche"))
						.option(Option.<Boolean>createBuilder()
								.name(Component.literal("Debug Farming Tracker"))
								.description(OptionDescription.of(Component.literal(
										"Schreibt in den Log, was aus der Actionbar gelesen wurde, wie Raten berechnet werden und ob/wie das Overlay gerendert wird.")))
								.binding(false, () -> HANDLER.instance().debugSkillXp, value -> HANDLER.instance().debugSkillXp = value)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.option(Option.<Boolean>createBuilder()
								.name(Component.literal("Debug Dialog Screen"))
								.description(OptionDescription.of(Component.literal(
										"Schreibt in den Log, ob der aktuelle Screen ein DialogScreen ist und welche Titel, Body-Elemente, Inputs und Buttons/Actions gelesen werden. Prüft zusätzlich Elements-Menü (Button Resourcebag) und Resourcebag (Item + Text mit Tooltip).")))
								.binding(false, () -> HANDLER.instance().debugDialogScreen, value -> HANDLER.instance().debugDialogScreen = value)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.build())
				.save(HANDLER::save)
				.build()
				.generateScreen(parent);
	}

	public static final class SkillFruitTimer {
		@SerialEntry
		public String name = "";

		@SerialEntry
		public int stacks = 1;

		@SerialEntry
		public long untilMs = 0L;
	}
}
