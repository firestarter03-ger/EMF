package net.fire.emf.client.config;

import net.fabricmc.loader.api.FabricLoader;
import net.fire.emf.ElementsMoreFeatures;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EmfDataFolder {
	public static final String FOLDER_NAME = "EMF";
	public static final String CONFIG_FILE_NAME = "emf_config.json";
	private static final String LEGACY_CONFIG_FILE_NAME = "emf.json5";

	private static Path folder;
	private static List<Path> existingFiles = List.of();

	private EmfDataFolder() {
	}

	public static void initialize() {
		folder = FabricLoader.getInstance().getConfigDir().resolve(FOLDER_NAME);
		try {
			if (Files.isDirectory(folder)) {
				existingFiles = listRegularFiles(folder);
			} else {
				Files.createDirectories(folder);
				existingFiles = List.of();
			}
			migrateLegacyConfig();
		} catch (IOException exception) {
			ElementsMoreFeatures.LOGGER.error("EMF-Config-Ordner konnte nicht vorbereitet werden: {}", folder, exception);
			existingFiles = List.of();
		}
	}

	public static Path folder() {
		if (folder == null) {
			initialize();
		}
		return folder;
	}

	public static Path configFile() {
		return folder().resolve(CONFIG_FILE_NAME);
	}

	public static List<Path> existingFiles() {
		return existingFiles;
	}

	public static List<Path> refreshExistingFiles() {
		try {
			if (Files.isDirectory(folder())) {
				existingFiles = listRegularFiles(folder());
			} else {
				existingFiles = List.of();
			}
		} catch (IOException exception) {
			ElementsMoreFeatures.LOGGER.error("EMF-Config-Ordner konnte nicht gelesen werden: {}", folder, exception);
			existingFiles = List.of();
		}
		return existingFiles;
	}

	private static void migrateLegacyConfig() throws IOException {
		Path configFile = configFile();
		if (Files.exists(configFile)) {
			return;
		}
		Path legacy = FabricLoader.getInstance().getConfigDir().resolve(LEGACY_CONFIG_FILE_NAME);
		if (Files.isRegularFile(legacy)) {
			String text = Files.readString(legacy);
			text = text.replaceAll("(?m)^\\s*//.*\\R?", "");
			text = text.replaceAll(",\\s*([}\\]])", "$1");
			Files.writeString(configFile, text);
			ElementsMoreFeatures.LOGGER.info("Alte EMF-Config nach {} übernommen.", configFile);
		}
	}

	private static List<Path> listRegularFiles(Path directory) throws IOException {
		List<Path> files = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
			for (Path path : stream) {
				if (Files.isRegularFile(path)) {
					files.add(path);
				}
			}
		}
		return Collections.unmodifiableList(files);
	}
}
