package net.fire.emf.client.overlay;

import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.overlay.SkillActionBarParser.SkillReading;
import org.slf4j.Logger;

public final class SkillXpDebug {
	private static final Logger LOGGER = ElementsMoreFeatures.LOGGER;
	private static String lastActionBarKey = "";
	private static String lastCalcKey = "";
	private static String lastOverlayKey = "";

	private SkillXpDebug() {
	}

	public static boolean enabled() {
		return EmfConfig.debugSkillXp();
	}

	public static void actionBar(String raw, boolean visible, String parseDescription) {
		if (!enabled()) {
			return;
		}

		String key = visible + "|" + (raw == null ? "" : raw);
		if (key.equals(lastActionBarKey)) {
			return;
		}
		lastActionBarKey = key;

		LOGGER.info("[SkillXP Debug] Actionbar sichtbar={} roh='{}' -> {}", visible, raw, parseDescription);
	}

	public static void calculation(String message) {
		if (!enabled()) {
			return;
		}
		if (message.equals(lastCalcKey)) {
			return;
		}
		lastCalcKey = message;
		LOGGER.info("[SkillXP Debug] Berechnung: {}", message);
	}

	public static void block(String message) {
		calculation(message);
	}

	public static void overlay(String message) {
		if (!enabled()) {
			return;
		}

		if (message.equals(lastOverlayKey)) {
			return;
		}
		lastOverlayKey = message;
		LOGGER.info("[SkillXP Debug] Overlay: {}", message);
	}

	public static String describeReading(SkillReading reading) {
		if (reading == null) {
			return "kein gültiges Skill-Format";
		}
		return "Skill=" + reading.skill()
				+ " XP=" + reading.currentXp() + "/" + reading.targetXp()
				+ " Res/Kills=" + reading.resources();
	}
}
