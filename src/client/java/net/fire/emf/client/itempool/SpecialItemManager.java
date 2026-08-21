package net.fire.emf.client.itempool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.api.EmfApiClient;
import net.fire.emf.client.config.EmfConfig;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Verwaltet normalisierte Spezial-Items (eingebettet → lokal → Server-Revision).
 */
public final class SpecialItemManager {
	private static final String FILE_NAME = "specialItems.dat";
	private static final String EMBEDDED_RESOURCE = "/assets/emf/data/special_items.json";
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private static final SpecialItemManager INSTANCE = new SpecialItemManager();

	private volatile SpecialItemsDocument active = new SpecialItemsDocument(0, List.of());
	private volatile List<SpecialItemDefinition> definitions = List.of();
	private final AtomicBoolean ready = new AtomicBoolean(false);
	private final AtomicBoolean serverRefreshStarted = new AtomicBoolean(false);

	private SpecialItemManager() {
	}

	public static SpecialItemManager get() {
		return INSTANCE;
	}

	/**
	 * Synchroner Bootstrap (eingebettet + lokal), danach bereit für Item-/Loot-Erfassung.
	 * Server-Refresh startet asynchron und ersetzt nur bei höherer gültiger Revision.
	 */
	public synchronized void initialize() {
		EmfRuntimeDataFolder.initialize();
		bootstrapFromEmbeddedAndLocal();
		ready.set(true);
		startServerRefreshAsync();
	}

	public boolean isReady() {
		return ready.get();
	}

	public int revision() {
		return active.revision;
	}

	public synchronized List<SpecialItemDefinition> all() {
		return definitions;
	}

	/**
	 * Normalisierte Special-Items für JEI/EMI (ohne Duplikate nach itemId).
	 */
	public synchronized List<PoolItemEntry> normalizedItemsForJei() {
		Map<String, PoolItemEntry> byId = new LinkedHashMap<>();
		for (SpecialItemDefinition definition : definitions) {
			if (definition == null || definition.item == null || definition.item.itemId == null) {
				continue;
			}
			byId.putIfAbsent(definition.item.itemId, definition.item);
		}
		return List.copyOf(byId.values());
	}

	public Optional<SpecialItemDefinition> findSpecialItem(ItemStack stack) {
		if (!ready.get() || stack == null || stack.isEmpty()) {
			return Optional.empty();
		}
		ItemIdentifier.IdentifiedItem identified = ItemIdentifier.identify(stack);
		if (identified == null) {
			return Optional.empty();
		}
		return findSpecialItem(identified.itemName(), identified.itemData().hoverText, identified.registryId(), stack);
	}

	public Optional<SpecialItemDefinition> findSpecialItemByName(String itemName) {
		if (!ready.get()) {
			return Optional.empty();
		}
		String cleaned = ItemIgnoreManager.cleanName(itemName);
		if (cleaned.isBlank()) {
			return Optional.empty();
		}
		return findSpecialItem(cleaned, "", null, null);
	}

	private Optional<SpecialItemDefinition> findSpecialItem(
			String itemName, String hoverText, String registryId, ItemStack stack) {
		List<SpecialItemDefinition> snapshot = definitions;
		for (SpecialItemDefinition definition : snapshot) {
			if (definition == null || definition.match == null) {
				continue;
			}
			if (SpecialItemMatcher.matches(definition.match, stack, itemName, hoverText, registryId)) {
				return Optional.of(definition);
			}
		}
		return Optional.empty();
	}

	private void bootstrapFromEmbeddedAndLocal() {
		SpecialItemsDocument embedded = loadEmbedded();
		if (embedded == null || !SpecialItemsValidator.isValid(embedded)) {
			ElementsMoreFeatures.LOGGER.error(
					"[EMF SpecialItems] Eingebettete special_items.json fehlt oder ist ungültig");
			embedded = new SpecialItemsDocument(0, List.of());
		}

		Path localPath = file();
		Optional<SpecialItemsDocument> localOpt = CompressedStorage.tryLoadCompressedJson(
				localPath, SpecialItemsDocument.class);
		SpecialItemsDocument local = null;
		if (localOpt.isPresent()) {
			if (SpecialItemsValidator.isValid(localOpt.get())) {
				local = localOpt.get();
			} else {
				ElementsMoreFeatures.LOGGER.warn(
						"[EMF SpecialItems] Lokale specialItems.dat ungültig — Backup");
				CompressedStorage.backupCorruptFile(localPath);
			}
		}

		SpecialItemsDocument selected;
		boolean needWrite;
		if (local == null) {
			selected = embedded;
			needWrite = true;
		} else if (local.revision > embedded.revision) {
			selected = local;
			needWrite = false;
		} else if (local.revision < embedded.revision) {
			selected = embedded;
			needWrite = true;
		} else {
			// gleiche Revision: lokale Datei behalten (bereits vorhanden)
			selected = local;
			needWrite = false;
		}

		applyDocument(selected);
		if (needWrite || !Files.isRegularFile(localPath)) {
			persistAtomically(selected);
		}
		ElementsMoreFeatures.LOGGER.info(
				"[EMF SpecialItems] Aktiv revision={} ({} Einträge, Quelle={})",
				selected.revision,
				selected.specialItems == null ? 0 : selected.specialItems.size(),
				local != null && selected == local ? "lokal" : "eingebettet");
	}

	private void startServerRefreshAsync() {
		if (!serverRefreshStarted.compareAndSet(false, true)) {
			return;
		}
		// Nach Join (Token verfügbar) — Netzwerkfehler belassen die geladene Liste.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				CompletableFuture.runAsync(() -> {
					try {
						refreshFromServer();
					} catch (Exception exception) {
						ElementsMoreFeatures.LOGGER.warn(
								"[EMF SpecialItems] Server-Refresh fehlgeschlagen: {}", exception.getMessage());
					}
				}));
	}

	private void refreshFromServer() {
		if (!EmfConfig.isDataTrackingEnabled()) {
			return;
		}
		try {
			JsonObject response = EmfApiClient.get().getAuthed("/api/mod/download-special-items");
			if (response == null) {
				return;
			}
			SpecialItemsDocument remote = parseServerDocument(response);
			if (!SpecialItemsValidator.isValid(remote)) {
				ElementsMoreFeatures.LOGGER.warn("[EMF SpecialItems] Serverliste ungültig — ignoriert");
				return;
			}
			synchronized (this) {
				if (remote.revision <= active.revision) {
					if (EmfConfig.debugApi()) {
						ElementsMoreFeatures.LOGGER.info(
								"[EMF SpecialItems] Server revision={} <= lokal {} — behalten",
								remote.revision, active.revision);
					}
					return;
				}
				persistAtomically(remote);
				applyDocument(remote);
				ElementsMoreFeatures.LOGGER.info(
						"[EMF SpecialItems] Server revision={} übernommen ({} Einträge)",
						remote.revision,
						remote.specialItems.size());
			}
		} catch (Exception exception) {
			ElementsMoreFeatures.LOGGER.warn(
					"[EMF SpecialItems] Netzwerkfehler — behalte revision={}: {}",
					active.revision, exception.getMessage());
		}
	}

	private SpecialItemsDocument parseServerDocument(JsonObject response) {
		SpecialItemsDocument document = new SpecialItemsDocument();
		if (response.has("revision") && response.get("revision").isJsonPrimitive()) {
			document.revision = response.get("revision").getAsInt();
		}
		document.specialItems = new ArrayList<>();
		if (response.has("specialItems") && response.get("specialItems").isJsonArray()) {
			JsonArray array = response.getAsJsonArray("specialItems");
			for (JsonElement element : array) {
				SpecialItemDefinition definition = GSON.fromJson(element, SpecialItemDefinition.class);
				if (definition != null) {
					document.specialItems.add(definition);
				}
			}
		}
		return document;
	}

	private void applyDocument(SpecialItemsDocument document) {
		SpecialItemsDocument copy = new SpecialItemsDocument(
				document.revision,
				document.specialItems == null ? List.of() : document.specialItems);
		active = copy;
		definitions = List.copyOf(copy.specialItems);
	}

	private void persistAtomically(SpecialItemsDocument document) {
		if (!SpecialItemsValidator.isValid(document)) {
			ElementsMoreFeatures.LOGGER.warn("[EMF SpecialItems] Ungültiges Dokument — nicht gespeichert");
			return;
		}
		try {
			CompressedStorage.saveCompressedJson(file(), document);
		} catch (IOException exception) {
			ElementsMoreFeatures.LOGGER.error(
					"[EMF SpecialItems] Speichern fehlgeschlagen: {}", file(), exception);
		}
	}

	private static SpecialItemsDocument loadEmbedded() {
		try (InputStream stream = SpecialItemManager.class.getResourceAsStream(EMBEDDED_RESOURCE)) {
			if (stream == null) {
				return null;
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return GSON.fromJson(reader, SpecialItemsDocument.class);
			}
		} catch (Exception exception) {
			ElementsMoreFeatures.LOGGER.error(
					"[EMF SpecialItems] Eingebettete Liste konnte nicht gelesen werden", exception);
			return null;
		}
	}

	private static Path file() {
		return EmfRuntimeDataFolder.resolve(FILE_NAME);
	}
}
