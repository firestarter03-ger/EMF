package net.fire.emf.client.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfDataFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CollectionStore {
	private static final String FILE_NAME = "collections_save.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static Map<String, Long> cache;
	private static GameProfile cachedProfile = GameProfile.UNKNOWN;

	private CollectionStore() {
	}

	public static Path file() {
		return EmfDataFolder.folder().resolve(FILE_NAME);
	}

	public static void onProfileChanged() {
		cache = null;
		cachedProfile = GameProfile.UNKNOWN;
	}

	public static long get(String name) {
		if (name == null || name.isBlank() || ProfileDetector.current() == GameProfile.UNKNOWN) {
			return 0L;
		}
		return load().getOrDefault(name, 0L);
	}

	public static void mergeAndSave(Map<String, Long> updates) {
		if (updates == null || updates.isEmpty() || ProfileDetector.current() == GameProfile.UNKNOWN) {
			return;
		}

		Map<String, Long> values = load();
		values.putAll(updates);
		write(values);
	}

	private static void write(Map<String, Long> values) {
		GameProfile profile = ProfileDetector.current();
		if (profile == GameProfile.UNKNOWN) {
			return;
		}

		Map<String, Map<String, Long>> allProfiles = readAllProfiles();
		allProfiles.put(profile.id(), new LinkedHashMap<>(values));

		JsonObject json = new JsonObject();
		for (Map.Entry<String, Map<String, Long>> entry : allProfiles.entrySet()) {
			JsonObject profileJson = new JsonObject();
			for (Map.Entry<String, Long> resource : entry.getValue().entrySet()) {
				if (resource.getKey() == null || resource.getKey().isBlank() || resource.getValue() == null) {
					continue;
				}
				profileJson.addProperty(resource.getKey(), resource.getValue());
			}
			json.add(entry.getKey(), profileJson);
		}

		try {
			Files.writeString(file(), GSON.toJson(json) + System.lineSeparator());
			EmfDataFolder.refreshExistingFiles();
			cache = values;
			cachedProfile = profile;
		} catch (IOException exception) {
			ElementsMoreFeatures.LOGGER.error("Collections konnten nicht gespeichert werden: {}", file(), exception);
		}
	}

	private static Map<String, Long> load() {
		GameProfile profile = ProfileDetector.current();
		if (cache != null && cachedProfile == profile) {
			return cache;
		}

		Map<String, Long> values = readAllProfiles().getOrDefault(profile.id(), new LinkedHashMap<>());
		cache = values;
		cachedProfile = profile;
		return values;
	}

	private static Map<String, Map<String, Long>> readAllProfiles() {
		Map<String, Map<String, Long>> profiles = new LinkedHashMap<>();
		for (GameProfile profile : GameProfile.values()) {
			if (profile != GameProfile.UNKNOWN) {
				profiles.put(profile.id(), new LinkedHashMap<>());
			}
		}

		Path path = file();
		if (!Files.isRegularFile(path)) {
			return profiles;
		}

		try {
			JsonElement parsed = com.google.gson.JsonParser.parseString(Files.readString(path));
			if (!parsed.isJsonObject()) {
				return profiles;
			}

			JsonObject json = parsed.getAsJsonObject();
			Map<String, Long> legacy = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				if (entry.getValue().isJsonObject()) {
					Map<String, Long> values = new LinkedHashMap<>();
					for (Map.Entry<String, JsonElement> resource : entry.getValue().getAsJsonObject().entrySet()) {
						try {
							values.put(resource.getKey(), resource.getValue().getAsLong());
						} catch (RuntimeException ignored) {
						}
					}
					profiles.put(entry.getKey(), values);
				} else {
					try {
						legacy.put(entry.getKey(), entry.getValue().getAsLong());
					} catch (RuntimeException ignored) {
					}
				}
			}

			if (!legacy.isEmpty()) {
				profiles.computeIfAbsent(GameProfile.SKYBLOCK.id(), ignored -> new LinkedHashMap<>()).putAll(legacy);
				persistProfiles(profiles);
			}
		} catch (Exception exception) {
			ElementsMoreFeatures.LOGGER.error("collections_save.json konnte nicht gelesen werden: {}", path, exception);
		}
		return profiles;
	}

	private static void persistProfiles(Map<String, Map<String, Long>> profiles) {
		JsonObject json = new JsonObject();
		for (Map.Entry<String, Map<String, Long>> entry : profiles.entrySet()) {
			JsonObject profileJson = new JsonObject();
			for (Map.Entry<String, Long> resource : entry.getValue().entrySet()) {
				profileJson.addProperty(resource.getKey(), resource.getValue());
			}
			json.add(entry.getKey(), profileJson);
		}
		try {
			Files.writeString(file(), GSON.toJson(json) + System.lineSeparator());
			EmfDataFolder.refreshExistingFiles();
		} catch (IOException exception) {
			ElementsMoreFeatures.LOGGER.error("Collections konnten nicht migriert werden: {}", file(), exception);
		}
	}
}
