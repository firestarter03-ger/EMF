package net.fire.emf.client.overlay;

import java.util.Locale;

final class OverlayFormat {
	static final String REACHED = "Erreicht";
	static final String MAX_REACHED = "Max erreicht";

	private OverlayFormat() {
	}

	static String compact(long value) {
		return compact(value * 1.0);
	}

	static String compact(double value) {
		double abs = Math.abs(value);
		if (abs >= 1_000_000.0) {
			return String.format(Locale.US, "%.1fM", value / 1_000_000.0);
		}
		if (abs >= 10_000.0) {
			return String.format(Locale.US, "%.1fK", value / 1_000.0);
		}
		return String.valueOf(Math.round(value));
	}

	static String withTarget(String eta, long target) {
		if (target <= 0L) {
			return eta;
		}
		return eta + " [" + compact(target) + "]";
	}

	static String[] splitTarget(String formatted) {
		if (formatted == null) {
			return new String[] {"", ""};
		}
		int start = formatted.lastIndexOf(" [");
		if (start >= 0 && formatted.endsWith("]")) {
			return new String[] {formatted.substring(0, start), formatted.substring(start)};
		}
		return new String[] {formatted, ""};
	}

	static boolean blinkVisible() {
		return (System.currentTimeMillis() / 500L) % 2L == 0L;
	}
}
