package net.fire.emf.client.session;

import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfConfig;
import org.slf4j.Logger;

public final class SessionSummaryDebug {
	private static final Logger LOGGER = ElementsMoreFeatures.LOGGER;

	private SessionSummaryDebug() {
	}

	public static boolean enabled() {
		return EmfConfig.HANDLER.instance().debugSessionSummary;
	}

	public static void mob(String message) {
		if (enabled()) {
			LOGGER.info("[Session Debug] {}", message);
		}
	}
}
