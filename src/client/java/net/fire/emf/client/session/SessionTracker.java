package net.fire.emf.client.session;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.loot.MobLootPoolStore;
import net.fire.emf.client.loot.MobLootSync;
import net.fire.emf.client.itempool.PoolItemEntry;
import net.fire.emf.client.itempool.SpecialItemManager;
import net.fire.emf.client.overlay.SkillActionBarParser;
import net.fire.emf.client.overlay.SkillActionBarParser.SkillReading;
import net.fire.emf.client.session.SessionModels.DisplayMode;
import net.fire.emf.client.session.SessionModels.LiveSession;
import net.fire.emf.client.title.LootRarity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SessionTracker {
	private static final Pattern COLOR_CODES = Pattern.compile("§x(?:§[0-9a-fA-F]){6}|§[0-9a-fk-orA-FK-OR]");
	private static final Pattern BRACKET_PREFIX = Pattern.compile("^\\s*(?:\\[[^\\]]*\\]\\s*)+(.+)$");
	/** Chat stapelt Loot als „Name (xN)“ / „Name §8(§7x§rN§8)“. */
	private static final Pattern LOOT_STACK_SUFFIX = Pattern.compile("^(.*?)\\s*\\(\\s*x\\s*(\\d+)\\s*\\)\\s*$", Pattern.CASE_INSENSITIVE);
	private static final long ATTACK_NAME_TTL_MS = 30_000L;
	private static final long KILL_NAME_TTL_MS = 15_000L;
	/** Actionbar + Entity-Death können denselben Kill melden. */
	private static final long KILL_DEDUP_MS = 400L;
	/** Eigene Actionbar-Baseline — unabhängig vom Farming-Tracker. */
	private static final long MAX_ACTIONBAR_KILL_DELTA = 5L;
	private static final String COMBAT_SKILL = "combat";

	private static LiveSession allSession;
	private static LiveSession mobSession;
	private static DisplayMode displayMode = DisplayMode.ALL;
	private static final Set<String> expandedMobs = new HashSet<>();
	/** Letzte angekündigte Stack-Zahl pro Item (für Chat-Updates x2, x3, …). */
	private static final Map<String, Integer> lastLootStackAnnounce = new HashMap<>();
	private static String lastAttackedMobName;
	private static long lastAttackedMobMs;
	private static int lastAttackedEntityId = -1;
	private static String lastKilledMobName;
	private static long lastKilledMobMs;
	private static String lastCreditedKillMob;
	private static long lastCreditedKillMs;
	/** -1 = noch keine Combat-Actionbar in dieser Session gesehen. */
	private static long actionBarResources = -1L;
	private static boolean wasInWorld;
	private static boolean registered;

	private SessionTracker() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ClientTickEvents.END_CLIENT_TICK.register(SessionTracker::onClientTick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> startSessions());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> endAndSaveSessions());
	}

	public static boolean isFeatureEnabled() {
		return EmfConfig.HANDLER.instance().sessionSummaryEnabled;
	}

	public static DisplayMode displayMode() {
		return displayMode;
	}

	public static void cycleDisplayMode(int direction) {
		if (direction >= 0) {
			displayMode = displayMode.next();
		} else {
			displayMode = displayMode.previous();
		}
	}

	public static boolean isExpanded(String mobName) {
		return mobName != null && expandedMobs.contains(mobName);
	}

	public static void toggleExpanded(String mobName) {
		if (mobName == null || mobName.isBlank()) {
			return;
		}
		if (!expandedMobs.add(mobName)) {
			expandedMobs.remove(mobName);
		}
	}

	public static LiveSession allSession() {
		return allSession;
	}

	public static LiveSession mobSession() {
		return mobSession;
	}

	public static void onAttackEntity(Entity entity) {
		// Auch ohne Session-Feature: für dauerhafte Loot-Pool-Zuordnung nötig.
		if (!(entity instanceof LivingEntity living)) {
			return;
		}
		String name = extractMobName(living);
		if (name == null) {
			return;
		}
		lastAttackedMobName = name;
		lastAttackedMobMs = System.currentTimeMillis();
		lastAttackedEntityId = living.getId();
		if (isFeatureEnabled()) {
			SessionSummaryDebug.mob("Angriff erkannt: '" + name + "'");
		}
	}

	/**
	 * Eigene Combat-Actionbar-Auswertung (Res/Kills), ohne Farming-Tracker-State.
	 */
	public static void onActionBar(String raw) {
		if (!isFeatureEnabled() || raw == null || raw.isBlank()) {
			return;
		}
		SkillReading reading = SkillActionBarParser.parse(raw);
		if (reading == null || !COMBAT_SKILL.equalsIgnoreCase(reading.skill())) {
			return;
		}
		ensureSessions();
		long value = reading.resources();
		if (actionBarResources < 0L) {
			actionBarResources = value;
			maybeCreditOpeningKill();
			SessionSummaryDebug.mob("Actionbar-Baseline Res/Kills=" + value);
			return;
		}
		if (value < actionBarResources) {
			SessionSummaryDebug.mob("Actionbar Res/Kills-Reset " + actionBarResources + " -> " + value);
			actionBarResources = value;
			return;
		}
		if (value == actionBarResources) {
			return;
		}
		long delta = value - actionBarResources;
		actionBarResources = value;
		if (delta > MAX_ACTIONBAR_KILL_DELTA) {
			SessionSummaryDebug.mob("Actionbar-Sprung +" + delta + " ignoriert (Resync -> " + value + ")");
			return;
		}
		creditActionBarKills(delta);
	}

	private static void maybeCreditOpeningKill() {
		long now = System.currentTimeMillis();
		if (lastAttackedMobName == null || now - lastAttackedMobMs > 3_000L) {
			return;
		}
		if (lastCreditedKillMs >= lastAttackedMobMs) {
			return;
		}
		creditKill(lastAttackedMobName, 1L, "Actionbar-Baseline");
	}

	private static void creditActionBarKills(long amount) {
		if (amount <= 0L) {
			return;
		}
		String mobName = currentAttackMobName();
		if (mobName == null) {
			SessionSummaryDebug.mob("Kill +" + amount + " ignoriert (kein Mobname)");
			return;
		}
		creditKill(mobName, amount, "Actionbar");
	}

	public static void onLootDrop(String itemName, String itemNameJson, String hoverText, List<String> hoverLineJsons, LootRarity rarity) {
		if (itemName == null || itemName.isBlank()) {
			return;
		}
		ParsedLootDrop parsed = parseLootDrop(itemName);
		if (parsed.baseName().isBlank()) {
			return;
		}
		int delta = lootDeltaForAnnounce(parsed.baseName(), parsed.announcedCount(), parsed.explicitStack());
		if (delta <= 0) {
			if (isFeatureEnabled()) {
				SessionSummaryDebug.mob("Drop '" + parsed.baseName() + "' x" + parsed.announcedCount()
						+ " ignoriert (kein Delta, last="
						+ lastLootStackAnnounce.getOrDefault(parsed.baseName().toLowerCase(Locale.ROOT), 0) + ")");
			}
			return;
		}
		String mobName = resolveLootMobName();
		if (mobName == null) {
			if (isFeatureEnabled()) {
				SessionSummaryDebug.mob("Drop '" + parsed.baseName() + "' +" + delta + " ignoriert (kein Mob zum Zuordnen)");
			}
			return;
		}
		String cleanJson = stripStackSuffixFromJson(itemNameJson, parsed.baseName());
		var special = SpecialItemManager.get().findSpecialItemByName(parsed.baseName());
		if (special.isPresent()) {
			// Dynamische Variante nicht in mob-loot speichern/hochladen — Session nutzt Normalform.
			PoolItemEntry normalized = special.get().item;
			String sessionName = normalized != null && normalized.itemName != null && !normalized.itemName.isBlank()
					? normalized.itemName : parsed.baseName();
			String sessionJson = normalized != null && normalized.itemData != null
					? normalized.itemData.itemNameJson : cleanJson;
			String sessionHover = normalized != null && normalized.itemData != null
					? normalized.itemData.hoverText : hoverText;
			List<String> sessionLines = normalized != null && normalized.itemData != null
					? normalized.itemData.hoverLineJsons : hoverLineJsons;
			if (!isFeatureEnabled()) {
				return;
			}
			ensureSessions();
			allSession.mob(mobName).addLoot(sessionName, sessionJson, sessionHover, sessionLines, rarity, delta);
			if (mobSession != null && mobName.equals(mobSession.currentMobName)) {
				mobSession.mob(mobName).addLoot(sessionName, sessionJson, sessionHover, sessionLines, rarity, delta);
			}
			SessionSummaryDebug.mob("Drop '" + sessionName + "' +" + delta
					+ " (Special '" + special.get().specialId + "') -> '" + mobName + "'");
			return;
		}

		boolean isNewPoolEntry = MobLootPoolStore.recordDrop(
				mobName, parsed.baseName(), cleanJson, hoverText, hoverLineJsons);
		if (isNewPoolEntry) {
			MobLootSync.syncNewDropAsync(mobName, parsed.baseName(), cleanJson, hoverText, hoverLineJsons);
		}

		if (!isFeatureEnabled()) {
			return;
		}
		ensureSessions();
		allSession.mob(mobName).addLoot(parsed.baseName(), cleanJson, hoverText, hoverLineJsons, rarity, delta);
		if (mobSession != null && mobName.equals(mobSession.currentMobName)) {
			mobSession.mob(mobName).addLoot(parsed.baseName(), cleanJson, hoverText, hoverLineJsons, rarity, delta);
		}
		SessionSummaryDebug.mob("Drop '" + parsed.baseName() + "' +" + delta
				+ " (chat x" + parsed.announcedCount() + ") -> '" + mobName + "' (Rarity="
				+ (rarity == null ? "?" : rarity.displayName()) + ")");
	}

	private static void creditKill(String mobName, long amount, String source) {
		if (mobName == null || mobName.isBlank() || amount <= 0L || allSession == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (amount == 1L
				&& mobName.equalsIgnoreCase(lastCreditedKillMob)
				&& now - lastCreditedKillMs <= KILL_DEDUP_MS) {
			SessionSummaryDebug.mob("Kill +1 -> '" + mobName + "' übersprungen (Dedup, kam als " + source + ")");
			return;
		}
		switchToMob(mobName);
		lastKilledMobName = mobName;
		lastKilledMobMs = now;
		lastCreditedKillMob = mobName;
		lastCreditedKillMs = now;
		allSession.mob(mobName).addKill(amount);
		if (mobSession != null && mobName.equals(mobSession.currentMobName)) {
			mobSession.mob(mobName).addKill(amount);
		}
		SessionSummaryDebug.mob("Kill +" + amount + " -> '" + mobName + "' (gesamt "
				+ allSession.mob(mobName).kills + ", via " + source + ")");
	}

	private static String resolveLootMobName() {
		long now = System.currentTimeMillis();
		boolean attackFresh = lastAttackedMobName != null && now - lastAttackedMobMs <= ATTACK_NAME_TTL_MS;
		boolean killFresh = lastKilledMobName != null && now - lastKilledMobMs <= KILL_NAME_TTL_MS;
		// Angriff neuer als letzter Kill: Loot gehört zum aktuellen Ziel (nicht zum vorherigen Mob).
		if (attackFresh && (!killFresh || lastAttackedMobMs >= lastKilledMobMs)) {
			return lastAttackedMobName;
		}
		// Loot oft leicht verzögert nach Kill, ohne neuen Angriff dazwischen.
		if (killFresh) {
			return lastKilledMobName;
		}
		if (attackFresh) {
			return lastAttackedMobName;
		}
		if (allSession != null && allSession.currentMobName != null && !allSession.currentMobName.isBlank()) {
			return allSession.currentMobName;
		}
		return null;
	}

	public static void onTitle(Component title) {
		if (title == null || !isFeatureEnabled()) {
			return;
		}
		String text = normalizeTitle(clean(title.getString()));
		// Zuerst "AFK OFF", sonst greift bei startsWith/ähnlichem fälschlich nur "AFK".
		if (text.equalsIgnoreCase("AFK OFF")) {
			setPaused(false);
			SessionSummaryDebug.mob("AFK Off erkannt (Timer weiter)");
		} else if (text.equalsIgnoreCase("AFK")) {
			setPaused(true);
			SessionSummaryDebug.mob("AFK erkannt (Timer pausiert)");
		}
	}

	private static String normalizeTitle(String value) {
		if (value == null) {
			return "";
		}
		return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	public static String formatElapsed(long elapsedMs) {
		long totalMinutes = Math.max(0L, elapsedMs / 60_000L);
		if (totalMinutes <= 59L) {
			return totalMinutes + "min";
		}
		long hours = totalMinutes / 60L;
		long minutes = totalMinutes % 60L;
		return hours + ":" + String.format(Locale.ROOT, "%02d", minutes) + "h";
	}

	private static void onClientTick(Minecraft client) {
		boolean inWorld = client != null && client.player != null && client.level != null;
		if (inWorld && !wasInWorld) {
			startSessions();
		} else if (!inWorld && wasInWorld) {
			endAndSaveSessions();
		}
		wasInWorld = inWorld;
		if (!inWorld || !isFeatureEnabled()) {
			if (!isFeatureEnabled()) {
				clearLive();
			}
			return;
		}
		ensureSessions();
		tickTimers(System.currentTimeMillis());
		maybeCreditDeadAttackTarget(client);
	}

	/**
	 * Wenn die Actionbar den ersten Kill verschluckt (Advancement/Baseline), zählt der Tod des
	 * zuletzt angegriffenen Custom-Mobs trotzdem.
	 */
	private static void maybeCreditDeadAttackTarget(Minecraft client) {
		if (!isFeatureEnabled() || client == null || client.level == null || lastAttackedEntityId < 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if (lastAttackedMobName == null || now - lastAttackedMobMs > 3_000L) {
			lastAttackedEntityId = -1;
			return;
		}
		Entity entity = client.level.getEntity(lastAttackedEntityId);
		if (entity == null) {
			creditKill(lastAttackedMobName, 1L, "Entity-Remove");
			lastAttackedEntityId = -1;
			return;
		}
		if (entity instanceof LivingEntity living && (living.isDeadOrDying() || living.getHealth() <= 0.0f)) {
			creditKill(lastAttackedMobName, 1L, "Entity-Death");
			lastAttackedEntityId = -1;
		}
	}

	private static void startSessions() {
		if (!isFeatureEnabled()) {
			clearLive();
			return;
		}
		long now = System.currentTimeMillis();
		allSession = newLive(now);
		mobSession = newLive(now);
		expandedMobs.clear();
		lastAttackedMobName = null;
		lastAttackedMobMs = 0L;
		lastAttackedEntityId = -1;
		lastKilledMobName = null;
		lastKilledMobMs = 0L;
		lastCreditedKillMob = null;
		lastCreditedKillMs = 0L;
		actionBarResources = -1L;
		lastLootStackAnnounce.clear();
	}

	private static void endAndSaveSessions() {
		if (allSession != null) {
			tickTimers(System.currentTimeMillis());
			SessionStore.appendAllSession(allSession);
		}
		if (mobSession != null && mobSession.currentMobName != null) {
			SessionStore.saveMobSession(mobSession);
		}
		clearLive();
	}

	private static void clearLive() {
		allSession = null;
		mobSession = null;
		lastAttackedMobName = null;
		lastAttackedMobMs = 0L;
		lastAttackedEntityId = -1;
		lastKilledMobName = null;
		lastKilledMobMs = 0L;
		lastCreditedKillMob = null;
		lastCreditedKillMs = 0L;
		actionBarResources = -1L;
		lastLootStackAnnounce.clear();
	}

	private static void ensureSessions() {
		if (!isFeatureEnabled()) {
			return;
		}
		if (allSession == null || mobSession == null) {
			startSessions();
		}
	}

	private static LiveSession newLive(long now) {
		LiveSession session = new LiveSession();
		session.startedAtMs = now;
		session.lastTickMs = now;
		session.accumulatedActiveMs = 0L;
		session.paused = false;
		return session;
	}

	private static void tickTimers(long now) {
		tickOne(allSession, now);
		tickOne(mobSession, now);
	}

	private static void tickOne(LiveSession session, long now) {
		if (session == null) {
			return;
		}
		if (!session.paused && session.lastTickMs > 0L) {
			session.accumulatedActiveMs += Math.max(0L, now - session.lastTickMs);
		}
		session.lastTickMs = now;
	}

	private static void setPaused(boolean paused) {
		long now = System.currentTimeMillis();
		tickTimers(now);
		if (allSession != null) {
			allSession.paused = paused;
		}
		if (mobSession != null) {
			mobSession.paused = paused;
		}
	}

	private static void switchToMob(String mobName) {
		if (allSession != null) {
			allSession.currentMobName = mobName;
		}
		if (mobSession == null) {
			return;
		}
		if (mobName.equals(mobSession.currentMobName)) {
			return;
		}
		if (mobSession.currentMobName != null && !mobSession.currentMobName.isBlank()) {
			tickTimers(System.currentTimeMillis());
			SessionStore.saveMobSession(mobSession);
		}
		long now = System.currentTimeMillis();
		boolean paused = mobSession.paused;
		mobSession = newLive(now);
		mobSession.paused = paused;
		mobSession.currentMobName = mobName;
		mobSession.mob(mobName);
	}

	private static String currentAttackMobName() {
		long now = System.currentTimeMillis();
		if (lastAttackedMobName != null && now - lastAttackedMobMs <= ATTACK_NAME_TTL_MS) {
			return lastAttackedMobName;
		}
		// Actionbar-Kill kommt oft verzögert; zuletzt getöteten / aktuellen Mob behalten.
		if (lastKilledMobName != null && now - lastKilledMobMs <= KILL_NAME_TTL_MS) {
			return lastKilledMobName;
		}
		if (allSession != null && allSession.currentMobName != null && !allSession.currentMobName.isBlank()) {
			return allSession.currentMobName;
		}
		return lastAttackedMobName;
	}

	private static ParsedLootDrop parseLootDrop(String rawName) {
		String cleaned = clean(rawName);
		Matcher matcher = LOOT_STACK_SUFFIX.matcher(cleaned);
		if (matcher.matches()) {
			String base = matcher.group(1).trim();
			int count = Math.max(1, Integer.parseInt(matcher.group(2)));
			return new ParsedLootDrop(base, count, true);
		}
		return new ParsedLootDrop(cleaned.trim(), 1, false);
	}

	/**
	 * Chat sendet bei Stack-Updates die Absolute (x2, x3, …), nicht +1.
	 * Ohne Suffix startet eine neue Kette (+1).
	 */
	private static int lootDeltaForAnnounce(String baseName, int announced, boolean explicitStack) {
		String key = baseName.toLowerCase(Locale.ROOT);
		int previous = lastLootStackAnnounce.getOrDefault(key, 0);
		int delta;
		if (!explicitStack) {
			delta = 1;
			lastLootStackAnnounce.put(key, 1);
		} else if (announced > previous) {
			delta = announced - previous;
			lastLootStackAnnounce.put(key, announced);
		} else if (announced < previous) {
			// Stack-Zähler im Chat neu gestartet (z. B. nach Bank)
			delta = announced;
			lastLootStackAnnounce.put(key, announced);
		} else {
			delta = 0;
		}
		return Math.max(0, delta);
	}

	private static String stripStackSuffixFromJson(String itemNameJson, String baseName) {
		if (baseName == null || baseName.isBlank()) {
			return itemNameJson;
		}
		Component plain = Component.literal(baseName);
		String asJson = SessionComponents.toJson(plain);
		if (itemNameJson == null || itemNameJson.isBlank()) {
			return asJson;
		}
		String cleanedJsonName = clean(SessionComponents.fromJson(itemNameJson).getString());
		Matcher matcher = LOOT_STACK_SUFFIX.matcher(cleanedJsonName);
		if (matcher.matches() || !cleanedJsonName.equalsIgnoreCase(baseName)) {
			return asJson;
		}
		return itemNameJson;
	}

	private record ParsedLootDrop(String baseName, int announcedCount, boolean explicitStack) {
	}

	static String extractMobName(LivingEntity entity) {
		if (entity == null) {
			return null;
		}
		Component custom = entity.getCustomName();
		if (custom != null) {
			String parsed = parseDisplayName(custom.getString());
			if (parsed != null) {
				return parsed;
			}
		}
		return null;
	}

	static String parseDisplayName(String raw) {
		String cleaned = clean(raw);
		if (cleaned.isBlank()) {
			return null;
		}
		Matcher matcher = BRACKET_PREFIX.matcher(cleaned);
		if (matcher.matches()) {
			cleaned = matcher.group(1).trim();
		}
		return cleaned.isBlank() ? null : cleaned;
	}

	private static String clean(String value) {
		return value == null ? "" : COLOR_CODES.matcher(value).replaceAll("").trim();
	}
}
