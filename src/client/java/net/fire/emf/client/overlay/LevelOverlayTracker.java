package net.fire.emf.client.overlay;

import net.fire.emf.client.config.EmfConfig;
import net.minecraft.world.entity.player.Player;

public final class LevelOverlayTracker {
	private static final long MIN_RATE_ELAPSED_MS = 1000L;
	private static final long COMBAT_IDLE_MS = 30_000L;
	private static final long IDLE_MS = 10_000L;
	private static final long FADE_MS = 1000L;

	private static boolean initialized;
	private static boolean hasSnapshot;
	private static boolean combatIdle;
	private static int currentLevel;
	private static int sessionStartLevel;
	private static long sessionStartMs;
	private static long lastXp;
	private static long lastGainMs;

	private LevelOverlayTracker() {
	}

	public static void tick(Player player) {
		if (player == null) {
			forgetPlayer();
			return;
		}

		long now = System.currentTimeMillis();
		int level = player.experienceLevel;
		long xp = player.totalExperience;

		if (!initialized) {
			captureBaseline(level, xp);
			return;
		}

		if (isStatSync(level, xp)) {
			captureBaseline(level, xp);
			return;
		}

		boolean levelGained = level > currentLevel;
		if (xp > lastXp || levelGained) {
			lastGainMs = now;
			hasSnapshot = true;
			combatIdle = SkillOverlayTracker.isCombatSessionActive();
			if (sessionStartMs == 0L) {
				sessionStartMs = now;
				sessionStartLevel = currentLevel;
			}
		}

		if (level < currentLevel) {
			sessionStartMs = hasSnapshot ? now : 0L;
			sessionStartLevel = level;
		}

		currentLevel = level;
		lastXp = xp;

		if (hasSnapshot && !alwaysShow() && fadeAlpha(now) <= 0.0f) {
			reset();
		}
	}

	private static void captureBaseline(int level, long xp) {
		initialized = true;
		currentLevel = level;
		lastXp = xp;
	}

	private static boolean isStatSync(int level, long xp) {
		if (currentLevel == 0 && level > 1) {
			return true;
		}
		if (lastXp == 0L && xp > 0L && level > currentLevel) {
			return true;
		}
		return false;
	}

	private static void forgetPlayer() {
		initialized = false;
		currentLevel = 0;
		lastXp = 0L;
		reset();
	}

	public static boolean shouldRender() {
		return hasSnapshot && fadeAlpha(System.currentTimeMillis()) > 0.0f;
	}

	public static float fadeAlpha() {
		return fadeAlpha(System.currentTimeMillis());
	}

	public static OverlaySnapshot snapshot() {
		if (!hasSnapshot) {
			return null;
		}

		EmfConfig config = EmfConfig.HANDLER.instance();
		double levelsPerMin = EmfConfig.levelTrackerNeedsXpRate() ? levelsPerMinute() : 0.0;

		String levelRate = null;
		if (config.levelTrackerShowLevelRate) {
			levelRate = formatLevelRate(levelsPerMin);
		}

		String target = null;
		if (config.levelTrackerShowTarget) {
			if (config.levelTrackerTarget <= 0L) {
				target = "--";
			} else if (config.levelTrackerTarget - currentLevel <= 0L) {
				target = OverlayFormat.withTarget(OverlayFormat.REACHED, config.levelTrackerTarget);
			} else {
				double levelsPerSecond = levelsPerMin / 60.0;
				String eta = levelsPerSecond > 0.0 ? formatEta((config.levelTrackerTarget - currentLevel) / levelsPerSecond) : "--";
				target = OverlayFormat.withTarget(eta, config.levelTrackerTarget);
			}
		}

		return new OverlaySnapshot(levelRate, target);
	}

	private static double levelsPerMinute() {
		if (sessionStartMs == 0L) {
			return 0.0;
		}

		long elapsed = System.currentTimeMillis() - sessionStartMs;
		if (elapsed < MIN_RATE_ELAPSED_MS) {
			return 0.0;
		}

		int gained = currentLevel - sessionStartLevel;
		if (gained <= 0) {
			return 0.0;
		}

		return gained / (elapsed / 60000.0);
	}

	private static String formatLevelRate(double perMin) {
		if (perMin <= 0.0) {
			return "0/min";
		}
		return Math.round(perMin) + "/min";
	}

	private static String formatEta(double seconds) {
		if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0.0) {
			return "--";
		}

		long totalSeconds = Math.round(seconds);
		if (totalSeconds < 60L) {
			return "<1min";
		}
		if (totalSeconds < 3600L) {
			return (totalSeconds / 60L) + "min";
		}

		long hours = totalSeconds / 3600L;
		long minutes = (totalSeconds % 3600L) / 60L;
		return String.format("%02d:%02dh", hours, minutes);
	}

	private static float fadeAlpha(long now) {
		if (!hasSnapshot) {
			return 0.0f;
		}
		if (alwaysShow()) {
			return 1.0f;
		}

		long idleMs = combatIdle ? COMBAT_IDLE_MS : IDLE_MS;
		long elapsed = now - lastGainMs;
		if (elapsed <= idleMs) {
			return 1.0f;
		}

		long fadeElapsed = elapsed - idleMs;
		if (fadeElapsed >= FADE_MS) {
			return 0.0f;
		}

		return 1.0f - (fadeElapsed / (float) FADE_MS);
	}

	private static boolean alwaysShow() {
		return EmfConfig.HANDLER.instance().levelTrackerAlwaysShow;
	}

	private static void reset() {
		hasSnapshot = false;
		combatIdle = false;
		lastGainMs = 0L;
		sessionStartMs = 0L;
		sessionStartLevel = 0;
	}

	public record OverlaySnapshot(String levelRate, String target) {
	}
}
