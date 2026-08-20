package net.fire.emf.client.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfDataFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CollectionGoalStore {
	private static final String FILE_NAME = "collection_goal.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static Map<String, List<Long>> cache;
	private static GameProfile cachedProfile = GameProfile.UNKNOWN;

	private CollectionGoalStore() {
	}

	public static Path file() {
		return EmfDataFolder.folder().resolve(FILE_NAME);
	}

	public static void onProfileChanged() {
		cache = null;
		cachedProfile = GameProfile.UNKNOWN;
	}

	public static void mergeAndSave(String name, List<Long> extraGoals) {
		if (name == null || name.isBlank() || ProfileDetector.current() == GameProfile.UNKNOWN) {
			return;
		}
		Map<String, List<Long>> values = load();
		List<Long> merged = new ArrayList<>(values.getOrDefault(name, List.of()));
		merged.addAll(clean(extraGoals));
		List<Long> cleaned = clean(merged);
		if (cleaned.equals(values.getOrDefault(name, List.of()))) {
			return;
		}
		values.put(name, cleaned);
		write(values);
	}

	public static Long nextGoal(String name, long current) {
		if (name == null || name.isBlank() || ProfileDetector.current() == GameProfile.UNKNOWN) {
			return null;
		}
		for (Long goal : load().getOrDefault(name, List.of())) {
			if (goal != null && current < goal) {
				return goal;
			}
		}
		return null;
	}

	public static boolean hasGoals(String name) {
		return name != null && !name.isBlank()
				&& ProfileDetector.current() != GameProfile.UNKNOWN
				&& load().containsKey(name);
	}

	private static void write(Map<String, List<Long>> values) {
		GameProfile profile = ProfileDetector.current();
		if (profile == GameProfile.UNKNOWN) {
			return;
		}

		Map<String, Map<String, List<Long>>> allProfiles = readAllProfiles();
		allProfiles.put(profile.id(), new LinkedHashMap<>(values));

		JsonObject json = new JsonObject();
		for (Map.Entry<String, Map<String, List<Long>>> entry : allProfiles.entrySet()) {
			JsonObject profileJson = new JsonObject();
			for (Map.Entry<String, List<Long>> resource : entry.getValue().entrySet()) {
				JsonArray array = new JsonArray();
				for (Long goal : resource.getValue()) {
					array.add(goal);
				}
				profileJson.add(resource.getKey(), array);
			}
			json.add(entry.getKey(), profileJson);
		}

		try {
			Files.writeString(file(), GSON.toJson(json) + System.lineSeparator());
			EmfDataFolder.refreshExistingFiles();
			cache = values;
			cachedProfile = profile;
		} catch (IOException exception) {
			ElementsMoreFeatures.LOGGER.error("Collection-Ziele konnten nicht gespeichert werden: {}", file(), exception);
		}
	}

	private static Map<String, List<Long>> load() {
		GameProfile profile = ProfileDetector.current();
		if (cache != null && cachedProfile == profile) {
			return cache;
		}

		Map<String, List<Long>> values = readAllProfiles().getOrDefault(profile.id(), new LinkedHashMap<>());
		cache = values;
		cachedProfile = profile;
		return values;
	}

	private static Map<String, Map<String, List<Long>>> readAllProfiles() {
		Map<String, Map<String, List<Long>>> profiles = new LinkedHashMap<>();
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
			Map<String, List<Long>> legacy = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				if (entry.getValue().isJsonObject()) {
					Map<String, List<Long>> values = new LinkedHashMap<>();
					for (Map.Entry<String, JsonElement> resource : entry.getValue().getAsJsonObject().entrySet()) {
						values.put(resource.getKey(), readGoals(resource.getValue()));
					}
					profiles.put(entry.getKey(), values);
				} else {
					legacy.put(entry.getKey(), readGoals(entry.getValue()));
				}
			}

			if (!legacy.isEmpty()) {
				profiles.computeIfAbsent(GameProfile.SKYBLOCK.id(), ignored -> new LinkedHashMap<>()).putAll(legacy);
				persistProfiles(profiles);
			}
		} catch (Exception exception) {
			ElementsMoreFeatures.LOGGER.error("collection_goal.json konnte nicht gelesen werden: {}", path, exception);
		}
		return profiles;
	}

	private static void persistProfiles(Map<String, Map<String, List<Long>>> profiles) {
		JsonObject json = new JsonObject();
		for (Map.Entry<String, Map<String, List<Long>>> entry : profiles.entrySet()) {
			JsonObject profileJson = new JsonObject();
			for (Map.Entry<String, List<Long>> resource : entry.getValue().entrySet()) {
				JsonArray array = new JsonArray();
				for (Long goal : resource.getValue()) {
					array.add(goal);
				}
				profileJson.add(resource.getKey(), array);
			}
			json.add(entry.getKey(), profileJson);
		}
		try {
			Files.writeString(file(), GSON.toJson(json) + System.lineSeparator());
			EmfDataFolder.refreshExistingFiles();
		} catch (IOException exception) {
			ElementsMoreFeatures.LOGGER.error("Collection-Ziele konnten nicht migriert werden: {}", file(), exception);
		}
	}

	private static List<Long> readGoals(JsonElement element) {
		List<Long> goals = new ArrayList<>();
		if (element == null || element.isJsonNull()) {
			return goals;
		}
		if (element.isJsonArray()) {
			for (JsonElement value : element.getAsJsonArray()) {
				try {
					goals.add(value.getAsLong());
				} catch (RuntimeException ignored) {
				}
			}
		} else {
			try {
				goals.add(element.getAsLong());
			} catch (RuntimeException ignored) {
			}
		}
		return clean(goals);
	}

	private static List<Long> clean(List<Long> remainingGoals) {
		if (remainingGoals == null || remainingGoals.isEmpty()) {
			return List.of();
		}
		List<Long> cleaned = new ArrayList<>();
		for (Long goal : remainingGoals) {
			if (goal != null && goal > 0L && !cleaned.contains(goal)) {
				cleaned.add(goal);
			}
		}
		Collections.sort(cleaned);
		return List.copyOf(cleaned);
	}
}
