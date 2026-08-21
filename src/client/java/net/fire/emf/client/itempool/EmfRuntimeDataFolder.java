package net.fire.emf.client.itempool;

import net.fabricmc.loader.api.FabricLoader;
import net.fire.emf.ElementsMoreFeatures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistenter Runtime-Datenordner: {@code .minecraft/.emf-data/}
 * (items.dat, specialItems.dat, später mob_loot.dat usw.)
 */
public final class EmfRuntimeDataFolder {
	public static final String FOLDER_NAME = ".emf-data";

	private static Path folder;

	private EmfRuntimeDataFolder() {
	}

	public static void initialize() {
		folder = FabricLoader.getInstance().getGameDir().resolve(FOLDER_NAME);
		try {
			Files.createDirectories(folder);
		} catch (IOException exception) {
			ElementsMoreFeatures.LOGGER.error("EMF-Runtime-Ordner konnte nicht erstellt werden: {}", folder, exception);
		}
	}

	public static Path folder() {
		if (folder == null) {
			initialize();
		}
		return folder;
	}

	public static Path resolve(String fileName) {
		return folder().resolve(fileName);
	}
}
