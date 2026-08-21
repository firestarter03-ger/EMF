package net.fire.emf.client.itempool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fire.emf.ElementsMoreFeatures;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Generisches GZIP+JSON Speichern/Laden für alle .emf-data Dateien (items.dat, specialItems.dat, …).
 */
public final class CompressedStorage {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private CompressedStorage() {
	}

	public static <T> void saveCompressedJson(Path path, T data) throws IOException {
		if (path.getParent() != null) {
			Files.createDirectories(path.getParent());
		}
		Path temp = path.resolveSibling(path.getFileName() + ".tmp");
		try (Writer writer = new OutputStreamWriter(
				new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temp))),
				StandardCharsets.UTF_8)) {
			GSON.toJson(data, writer);
		}
		try {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException ignored) {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public static <T> T loadCompressedJson(Path path, Class<T> type, Supplier<T> emptyFactory) {
		if (!Files.isRegularFile(path)) {
			T empty = emptyFactory.get();
			try {
				saveCompressedJson(path, empty);
			} catch (IOException exception) {
				ElementsMoreFeatures.LOGGER.error("Konnte leere Datei nicht anlegen: {}", path, exception);
			}
			return empty;
		}
		try (Reader reader = new InputStreamReader(
				new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path))),
				StandardCharsets.UTF_8)) {
			T loaded = GSON.fromJson(reader, type);
			if (loaded == null) {
				throw new JsonSyntaxException("null root");
			}
			return loaded;
		} catch (Exception exception) {
			ElementsMoreFeatures.LOGGER.error(
					"Beschädigte Datei {}, Backup und Neuanlage: {}", path, exception.toString());
			backupCorrupt(path);
			T empty = emptyFactory.get();
			try {
				saveCompressedJson(path, empty);
			} catch (IOException writeError) {
				ElementsMoreFeatures.LOGGER.error("Neuanlage fehlgeschlagen: {}", path, writeError);
			}
			return empty;
		}
	}

	/**
	 * Lädt GZIP-JSON. Fehlt die Datei oder ist sie beschädigt → Optional.empty()
	 * (bei Beschädigung: Backup). Schreibt keine leere Datei.
	 */
	public static <T> Optional<T> tryLoadCompressedJson(Path path, Class<T> type) {
		if (!Files.isRegularFile(path)) {
			return Optional.empty();
		}
		try (Reader reader = new InputStreamReader(
				new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path))),
				StandardCharsets.UTF_8)) {
			T loaded = GSON.fromJson(reader, type);
			if (loaded == null) {
				throw new JsonSyntaxException("null root");
			}
			return Optional.of(loaded);
		} catch (Exception exception) {
			ElementsMoreFeatures.LOGGER.error(
					"Beschädigte Datei {}, Backup: {}", path, exception.toString());
			backupCorrupt(path);
			return Optional.empty();
		}
	}

	public static void backupCorruptFile(Path path) {
		backupCorrupt(path);
	}

	private static void backupCorrupt(Path path) {
		try {
			String stamp = LocalDateTime.now().format(BACKUP_TS);
			Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + stamp);
			Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
			ElementsMoreFeatures.LOGGER.warn("Backup: {}", backup);
		} catch (IOException exception) {
			ElementsMoreFeatures.LOGGER.error("Backup von {} fehlgeschlagen", path, exception);
			try {
				Files.deleteIfExists(path);
			} catch (IOException ignored) {
			}
		}
	}
}
