package net.fire.emf.client.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfDataFolder;
import net.fire.emf.client.session.SessionModels.LiveSession;
import net.fire.emf.client.session.SessionModels.MobStats;
import net.fire.emf.client.session.SessionModels.SavedAllFile;
import net.fire.emf.client.session.SessionModels.SavedAllSession;
import net.fire.emf.client.session.SessionModels.SavedMobSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SessionStore {
	private static final String ALL_FILE = "last_session.json";
	private static final String MOB_FILE = "last_mob_session.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

	private SessionStore() {
	}

	public static Path allFile() {
		return EmfDataFolder.folder().resolve(ALL_FILE);
	}

	public static Path mobFile() {
		return EmfDataFolder.folder().resolve(MOB_FILE);
	}

	public static long peekNextAllId() {
		return readAllFile().nextId;
	}

	public static void appendAllSession(LiveSession live) {
		if (live == null) {
			return;
		}
		long elapsed = live.elapsedMs(System.currentTimeMillis());
		if (live.mobs.isEmpty() && elapsed < 60_000L) {
			return;
		}
		SavedAllFile file = readAllFile();
		SavedAllSession saved = new SavedAllSession();
		saved.id = file.nextId++;
		saved.savedAt = STAMP.format(LocalDateTime.now());
		saved.elapsedMs = elapsed;
		saved.mobs = copyMobs(live.mobs);
		file.sessions.add(saved);
		writeJson(allFile(), file);
	}

	public static void saveMobSession(LiveSession live) {
		if (live == null || live.currentMobName == null || live.currentMobName.isBlank()) {
			return;
		}
		MobStats stats = live.mobs.get(live.currentMobName);
		if (stats == null) {
			return;
		}
		SavedMobSession saved = new SavedMobSession();
		saved.id = peekNextAllId();
		saved.savedAt = STAMP.format(LocalDateTime.now());
		saved.mobName = live.currentMobName;
		saved.elapsedMs = live.elapsedMs(System.currentTimeMillis());
		saved.kills = stats.kills;
		saved.loot = new java.util.ArrayList<>(stats.loot);
		writeJson(mobFile(), saved);
	}

	private static Map<String, MobStats> copyMobs(Map<String, MobStats> source) {
		Map<String, MobStats> copy = new LinkedHashMap<>();
		for (Map.Entry<String, MobStats> entry : source.entrySet()) {
			MobStats stats = new MobStats();
			stats.kills = entry.getValue().kills;
			for (SessionModels.LootStack stack : entry.getValue().loot) {
				SessionModels.LootStack lootCopy = new SessionModels.LootStack(
						stack.itemName,
						stack.itemNameJson,
						stack.count,
						stack.hoverText,
						stack.hoverLineJsons,
						stack.rarityEnum());
				stats.loot.add(lootCopy);
			}
			copy.put(entry.getKey(), stats);
		}
		return copy;
	}

	private static SavedAllFile readAllFile() {
		Path path = allFile();
		if (!Files.isRegularFile(path)) {
			return new SavedAllFile();
		}
		try {
			SavedAllFile file = GSON.fromJson(Files.readString(path), SavedAllFile.class);
			if (file == null) {
				return new SavedAllFile();
			}
			if (file.sessions == null) {
				file.sessions = new java.util.ArrayList<>();
			}
			if (file.nextId < 1L) {
				file.nextId = 1L;
			}
			long maxId = 0L;
			for (SavedAllSession session : file.sessions) {
				if (session != null) {
					maxId = Math.max(maxId, session.id);
				}
			}
			if (file.nextId <= maxId) {
				file.nextId = maxId + 1L;
			}
			return file;
		} catch (Exception exception) {
			ElementsMoreFeatures.LOGGER.error("last_session.json konnte nicht gelesen werden: {}", path, exception);
			return new SavedAllFile();
		}
	}

	private static void writeJson(Path path, Object value) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(value) + System.lineSeparator());
			EmfDataFolder.refreshExistingFiles();
		} catch (IOException exception) {
			ElementsMoreFeatures.LOGGER.error("Session konnte nicht gespeichert werden: {}", path, exception);
		}
	}
}
