package net.fire.emf.client.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfDataFolder;
import net.fire.emf.client.session.SessionSummaryDebug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dauerhafte Wissensdatenbank: Mobname → einzigartige Drops inkl. Hover (wie Session).
 * Datei: config/EMF/mobs_loot_pool.json — wird nie automatisch gelöscht.
 */
public final class MobLootPoolStore {
	private static final String FILE_NAME = "mobs_loot_pool.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static PoolFile cache;
	private static boolean loaded;

	private MobLootPoolStore() {
	}

	public static Path file() {
		return EmfDataFolder.folder().resolve(FILE_NAME);
	}

	/**
	 * Neuen Drop für einen Mob merken. Gleicher Itemname → Hover überschreiben, kein Duplikat.
	 * @return {@code true} wenn ein neuer Drop-Eintrag angelegt wurde (nicht nur Hover-Update)
	 */
	public static synchronized boolean recordDrop(
			String mobName,
			String itemName,
			String itemNameJson,
			String hoverText,
			List<String> hoverLineJsons) {
		if (mobName == null || mobName.isBlank() || itemName == null || itemName.isBlank()) {
			return false;
		}
		PoolFile pool = load();
		MobEntry mob = pool.mobs.computeIfAbsent(mobName, ignored -> new MobEntry());
		if (mob.drops == null) {
			mob.drops = new ArrayList<>();
		}
		DropEntry existing = findDrop(mob.drops, itemName);
		if (existing != null) {
			boolean changed = updateHover(existing, itemNameJson, hoverText, hoverLineJsons);
			if (changed) {
				save(pool);
				SessionSummaryDebug.mob("Loot-Pool Hover aktualisiert: '" + mobName + "' / '" + itemName + "'");
			}
			return false;
		}
		DropEntry drop = new DropEntry();
		drop.itemName = itemName;
		drop.itemNameJson = itemNameJson == null ? "" : itemNameJson;
		drop.hoverText = hoverText == null ? "" : hoverText;
		drop.hoverLineJsons = hoverLineJsons == null ? new ArrayList<>() : new ArrayList<>(hoverLineJsons);
		mob.drops.add(drop);
		save(pool);
		SessionSummaryDebug.mob("Loot-Pool neu: '" + mobName + "' / '" + itemName + "'");
		return true;
	}

	private static DropEntry findDrop(List<DropEntry> drops, String itemName) {
		for (DropEntry drop : drops) {
			if (drop != null && itemName.equalsIgnoreCase(drop.itemName)) {
				return drop;
			}
		}
		return null;
	}

	private static boolean updateHover(
			DropEntry drop,
			String itemNameJson,
			String hoverText,
			List<String> hoverLineJsons) {
		boolean changed = false;
		if (itemNameJson != null && !itemNameJson.isBlank() && !itemNameJson.equals(drop.itemNameJson)) {
			drop.itemNameJson = itemNameJson;
			changed = true;
		}
		if (hoverText != null && !hoverText.equals(drop.hoverText)) {
			drop.hoverText = hoverText;
			changed = true;
		}
		if (hoverLineJsons != null) {
			List<String> next = new ArrayList<>(hoverLineJsons);
			if (drop.hoverLineJsons == null || !drop.hoverLineJsons.equals(next)) {
				drop.hoverLineJsons = next;
				changed = true;
			}
		}
		return changed;
	}

	private static PoolFile load() {
		if (loaded && cache != null) {
			return cache;
		}
		Path path = file();
		if (!Files.isRegularFile(path)) {
			cache = new PoolFile();
			loaded = true;
			return cache;
		}
		try {
			PoolFile file = GSON.fromJson(Files.readString(path), PoolFile.class);
			if (file == null) {
				file = new PoolFile();
			}
			if (file.mobs == null) {
				file.mobs = new LinkedHashMap<>();
			}
			normalize(file);
			cache = file;
			loaded = true;
			return cache;
		} catch (Exception exception) {
			ElementsMoreFeatures.LOGGER.error("mobs_loot_pool.json konnte nicht gelesen werden: {}", path, exception);
			cache = new PoolFile();
			loaded = true;
			return cache;
		}
	}

	private static void normalize(PoolFile file) {
		Map<String, MobEntry> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, MobEntry> entry : file.mobs.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				continue;
			}
			MobEntry mob = entry.getValue();
			if (mob.drops == null) {
				mob.drops = new ArrayList<>();
			}
			List<DropEntry> unique = new ArrayList<>();
			for (DropEntry drop : mob.drops) {
				if (drop == null || drop.itemName == null || drop.itemName.isBlank()) {
					continue;
				}
				DropEntry existing = findDrop(unique, drop.itemName);
				if (existing == null) {
					if (drop.hoverLineJsons == null) {
						drop.hoverLineJsons = new ArrayList<>();
					}
					unique.add(drop);
				} else {
					updateHover(existing, drop.itemNameJson, drop.hoverText, drop.hoverLineJsons);
				}
			}
			mob.drops = unique;
			normalized.put(entry.getKey(), mob);
		}
		file.mobs = normalized;
	}

	private static void save(PoolFile pool) {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(pool) + System.lineSeparator());
			EmfDataFolder.refreshExistingFiles();
			cache = pool;
			loaded = true;
		} catch (IOException exception) {
			ElementsMoreFeatures.LOGGER.error("mobs_loot_pool.json konnte nicht geschrieben werden: {}", path, exception);
		}
	}

	public static final class PoolFile {
		/** Mob-Anzeigename → Drops */
		public Map<String, MobEntry> mobs = new LinkedHashMap<>();
	}

	public static final class MobEntry {
		public List<DropEntry> drops = new ArrayList<>();
	}

	public static final class DropEntry {
		public String itemName = "";
		public String itemNameJson = "";
		public String hoverText = "";
		public List<String> hoverLineJsons = new ArrayList<>();
	}
}
