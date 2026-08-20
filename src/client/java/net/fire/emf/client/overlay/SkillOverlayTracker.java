package net.fire.emf.client.overlay;

import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.overlay.SkillActionBarParser.SkillReading;
import net.fire.emf.client.resource.CollectionGoalStore;
import net.fire.emf.client.resource.CollectionStore;
import net.fire.emf.client.resource.ResourceNameMapping;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;

public final class SkillOverlayTracker {
	private static final long SAMPLE_INTERVAL_MS = 1000L;
	private static final long[] RATE_WINDOWS_MS = {10_000L, 30_000L, 60_000L, 120_000L};
	private static final long MAX_SAMPLE_AGE_MS = 120_000L;
	private static final long MIN_RATE_ELAPSED_MS = 1000L;
	private static final long FADE_MS = 1000L;
	private static final long COMBAT_IDLE_MS = 30_000L;
	private static final long BLOCK_MATCH_WINDOW_MS = 2000L;
	private static final String COMBAT_SKILL = "combat";

	private static String skill;
	private static String resourceName;
	private static long currentXp;
	private static long targetXp;
	private static long resources;
	private static boolean hasSnapshot;
	private static long lastRecognizedMs;
	private static long lastGainMs;
	private static long lastSampleMs;
	private static String pendingBrokenResource;
	private static long pendingBrokenResourceMs;
	private static final ArrayDeque<Sample> xpSamples = new ArrayDeque<>();
	private static final ArrayDeque<Sample> resourceSamples = new ArrayDeque<>();

	private SkillOverlayTracker() {
	}

	public static void onActionBar(String text) {
		SkillActionBarParser.ParseOutcome outcome = SkillActionBarParser.parseDetailed(text);
		SkillXpDebug.actionBar(text, true, outcome.description());
		applyReading(outcome.reading(), System.currentTimeMillis());
	}

	public static void tick(String overlayText, boolean overlayVisible) {
		long now = System.currentTimeMillis();
		boolean recognized = false;
		if (overlayVisible) {
			SkillActionBarParser.ParseOutcome outcome = SkillActionBarParser.parseDetailed(overlayText);
			SkillXpDebug.actionBar(overlayText, true, outcome.description());
			recognized = applyReading(outcome.reading(), now);
		} else {
			SkillXpDebug.actionBar(overlayText, false, "Actionbar nicht sichtbar");
		}

		if (recognized && now - lastSampleMs >= SAMPLE_INTERVAL_MS) {
			recordSample(now);
			lastSampleMs = now;
		}

		if (hasSnapshot && !alwaysShow() && fadeAlpha(now) <= 0.0f) {
			SkillXpDebug.calculation("Overlay-Session beendet (Fade fertig)");
			reset();
		}
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
		double xpPerMin = EmfConfig.skillXpNeedsXpRate() ? ratePerMinute(xpSamples) : 0.0;
		double resourcesPerMin = EmfConfig.skillXpNeedsResourceRate() ? ratePerMinute(resourceSamples) : 0.0;

		String skillXp = null;
		if (config.skillXpShowXpRate) {
			skillXp = formatRate(xpPerMin, xpPerMin * 60.0);
		}

		String nextLevel = null;
		if (config.skillXpShowNextLevel) {
			long remainingXp = Math.max(0L, targetXp - currentXp);
			double xpPerSecond = xpPerMin / 60.0;
			nextLevel = xpPerSecond > 0.0 ? formatEta(remainingXp / xpPerSecond) : "--";
		}

		String resourcesText = null;
		if (config.skillXpShowResources) {
			resourcesText = formatRate(resourcesPerMin, resourcesPerMin * 60.0);
		}

		String resourceTarget = null;
		if (config.skillXpShowResourceTarget) {
			long remainingResources = Math.max(0L, config.skillXpResourceTarget - resources);
			double resourcesPerSecond = resourcesPerMin / 60.0;
			if (config.skillXpResourceTarget <= 0L) {
				resourceTarget = "--";
			} else if (remainingResources <= 0L) {
				resourceTarget = OverlayFormat.withTarget(OverlayFormat.REACHED, config.skillXpResourceTarget);
			} else {
				String eta = resourcesPerSecond > 0.0 ? formatEta(remainingResources / resourcesPerSecond) : "--";
				resourceTarget = OverlayFormat.withTarget(eta, config.skillXpResourceTarget);
			}
		}

		return new OverlaySnapshot(skill, resourceName, skillXp, nextLevel, resourcesText, resourceTarget,
				collectionText(resourceName), collectionMax(resourceName));
	}

	public static void onBlockBroken(BlockState state) {
		if (state == null || state.isAir()) {
			return;
		}
		String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		String mapped = ResourceNameMapping.nameFor(blockId);
		if (mapped == null) {
			return;
		}
		pendingBrokenResource = mapped;
		pendingBrokenResourceMs = System.currentTimeMillis();
		SkillXpDebug.calculation("Block erkannt " + blockId + " -> " + mapped);
	}

	private static boolean applyReading(SkillReading reading, long now) {
		if (reading == null) {
			return false;
		}

		if (skill == null || !skill.equalsIgnoreCase(reading.skill())) {
			SkillXpDebug.calculation("Neue Session Skill=" + reading.skill()
					+ " XP=" + reading.currentXp() + "/" + reading.targetXp()
					+ " Res/Kills=" + reading.resources());
			resetSession(reading, now);
			lastRecognizedMs = now;
			lastGainMs = now;
			hasSnapshot = true;
			return true;
		}

		boolean gained = reading.currentXp() != currentXp || reading.resources() != resources;
		if (reading.currentXp() < currentXp) {
			SkillXpDebug.calculation("XP-Reset erkannt " + currentXp + " -> " + reading.currentXp() + ", Samples geleert");
			xpSamples.clear();
			lastSampleMs = 0L;
		}
		if (reading.resources() < resources) {
			SkillXpDebug.calculation("Res/Kills-Reset erkannt " + resources + " -> " + reading.resources() + ", Samples geleert");
			resourceSamples.clear();
			lastSampleMs = 0L;
		}

		currentXp = reading.currentXp();
		targetXp = reading.targetXp();
		resources = reading.resources();
		lastRecognizedMs = now;
		if (gained) {
			lastGainMs = now;
			consumePendingResource(now);
		}
		hasSnapshot = true;
		return true;
	}

	private static void consumePendingResource(long now) {
		if (isCombatSkill()) {
			resourceName = null;
			return;
		}
		if (pendingBrokenResource != null && now - pendingBrokenResourceMs <= BLOCK_MATCH_WINDOW_MS) {
			resourceName = pendingBrokenResource;
			SkillXpDebug.calculation("Ressource übernommen: " + resourceName);
		}
		pendingBrokenResource = null;
		pendingBrokenResourceMs = 0L;
	}

	private static void recordSample(long now) {
		boolean needXp = EmfConfig.skillXpNeedsXpRate();
		boolean needResources = EmfConfig.skillXpNeedsResourceRate();
		if (!needXp && !needResources) {
			xpSamples.clear();
			resourceSamples.clear();
			return;
		}

		if (needXp) {
			xpSamples.addLast(new Sample(now, currentXp));
			trimWindow(xpSamples, now);
		} else {
			xpSamples.clear();
		}
		if (needResources) {
			resourceSamples.addLast(new Sample(now, resources));
			trimWindow(resourceSamples, now);
		} else {
			resourceSamples.clear();
		}

		if (!SkillXpDebug.enabled()) {
			return;
		}

		EmfConfig config = EmfConfig.HANDLER.instance();
		double xpPerMin = needXp ? ratePerMinute(xpSamples) : 0.0;
		double resPerMin = needResources ? ratePerMinute(resourceSamples) : 0.0;
		StringBuilder message = new StringBuilder("Sample");
		if (needXp) {
			message.append(" XP=").append(describeSamples(xpSamples))
					.append(" -> ").append(formatRate(xpPerMin, xpPerMin * 60.0));
			if (config.skillXpShowNextLevel) {
				long remainingXp = Math.max(0L, targetXp - currentXp);
				double xpPerSecond = xpPerMin / 60.0;
				message.append(" | remainingXP=").append(remainingXp)
						.append(" Next Level=").append(xpPerSecond > 0.0 ? formatEta(remainingXp / xpPerSecond) : "--");
			}
		}
		if (needResources) {
			message.append(" | Res/Kills=").append(describeSamples(resourceSamples))
					.append(" -> ").append(formatRate(resPerMin, resPerMin * 60.0));
			if (config.skillXpShowResourceTarget) {
				long remaining = Math.max(0L, config.skillXpResourceTarget - resources);
				double perSecond = resPerMin / 60.0;
				message.append(" | remainingRes=").append(remaining)
						.append(" Ziel=").append(perSecond > 0.0 ? formatEta(remaining / perSecond) : "--");
			}
		}
		SkillXpDebug.calculation(message.toString());
	}

	private static String describeSamples(ArrayDeque<Sample> samples) {
		if (samples.isEmpty()) {
			return "keine Samples";
		}
		Sample first = samples.peekFirst();
		Sample last = samples.peekLast();
		return "n=" + samples.size()
				+ " first=" + first.value()
				+ " last=" + last.value()
				+ " gained=" + (last.value() - first.value())
				+ " elapsedMs=" + (last.time() - first.time());
	}

	private static void trimWindow(ArrayDeque<Sample> samples, long now) {
		while (samples.size() > 1 && now - samples.peekFirst().time() > MAX_SAMPLE_AGE_MS) {
			samples.removeFirst();
		}
	}

	private static double ratePerMinute(ArrayDeque<Sample> samples) {
		if (samples.size() < 2) {
			return 0.0;
		}

		Sample last = samples.peekLast();
		double rate = 0.0;
		for (long window : RATE_WINDOWS_MS) {
			Sample start = sampleNear(samples, last.time() - window);
			if (start == null || start.time() >= last.time()) {
				continue;
			}
			long elapsed = last.time() - start.time();
			if (elapsed < MIN_RATE_ELAPSED_MS) {
				continue;
			}
			if (window > RATE_WINDOWS_MS[0] && elapsed < window * 0.8) {
				continue;
			}
			long gained = last.value() - start.value();
			if (gained < 0L) {
				continue;
			}
			rate = gained == 0L ? 0.0 : gained / (elapsed / 60000.0);
		}
		return rate;
	}

	private static Sample sampleNear(ArrayDeque<Sample> samples, long targetTime) {
		Sample best = null;
		for (Sample sample : samples) {
			if (sample.time() <= targetTime) {
				best = sample;
			} else {
				return best != null ? best : sample;
			}
		}
		return best;
	}

	private static String formatRate(double perMinute, double perHour) {
		return OverlayFormat.compact(perMinute) + "/min (" + OverlayFormat.compact(perHour) + "/h)";
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

		if (isCombatSkill()) {
			long idleElapsed = now - lastGainMs;
			if (idleElapsed <= COMBAT_IDLE_MS) {
				return 1.0f;
			}
			long fadeElapsed = idleElapsed - COMBAT_IDLE_MS;
			if (fadeElapsed >= FADE_MS) {
				return 0.0f;
			}
			return 1.0f - (fadeElapsed / (float) FADE_MS);
		}

		long elapsed = now - lastRecognizedMs;
		if (elapsed <= 0L) {
			return 1.0f;
		}
		if (elapsed >= FADE_MS) {
			return 0.0f;
		}

		return 1.0f - (elapsed / (float) FADE_MS);
	}

	private static boolean alwaysShow() {
		return EmfConfig.HANDLER.instance().skillXpAlwaysShow;
	}

	private static boolean isCombatSkill() {
		return skill != null && COMBAT_SKILL.equalsIgnoreCase(skill);
	}

	public static boolean isCombatSessionActive() {
		return hasSnapshot && isCombatSkill();
	}

	private static void resetSession(SkillReading reading, long now) {
		skill = reading.skill();
		resourceName = null;
		currentXp = reading.currentXp();
		targetXp = reading.targetXp();
		resources = reading.resources();
		xpSamples.clear();
		resourceSamples.clear();
		pendingBrokenResource = null;
		pendingBrokenResourceMs = 0L;
		lastSampleMs = now;
		xpSamples.addLast(new Sample(now, currentXp));
		resourceSamples.addLast(new Sample(now, resources));
	}

	private static void reset() {
		skill = null;
		resourceName = null;
		currentXp = 0L;
		targetXp = 0L;
		resources = 0L;
		hasSnapshot = false;
		lastRecognizedMs = 0L;
		lastGainMs = 0L;
		lastSampleMs = 0L;
		pendingBrokenResource = null;
		pendingBrokenResourceMs = 0L;
		xpSamples.clear();
		resourceSamples.clear();
	}

	public static void forgetPlayer() {
		reset();
	}

	public record OverlaySnapshot(String skill, String resourceName, String skillXp, String nextLevel, String resources,
			String resourceTarget, String collection, boolean collectionMax) {
	}

	private static String collectionText(String resource) {
		if (!CollectionGoalStore.hasGoals(resource)) {
			return null;
		}
		Long goal = CollectionGoalStore.nextGoal(resource, CollectionStore.get(resource));
		if (goal == null) {
			return OverlayFormat.MAX_REACHED;
		}
		return OverlayFormat.compact(CollectionStore.get(resource)) + "/" + OverlayFormat.compact(goal);
	}

	private static boolean collectionMax(String resource) {
		return CollectionGoalStore.hasGoals(resource)
				&& CollectionGoalStore.nextGoal(resource, CollectionStore.get(resource)) == null;
	}

	private record Sample(long time, long value) {
	}
}
