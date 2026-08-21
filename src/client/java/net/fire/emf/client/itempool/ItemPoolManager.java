package net.fire.emf.client.itempool;

import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfConfig;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestriert Special-/Ignore-Prüfung, lokale Persistenz und Upload neuer Items.
 */
public final class ItemPoolManager {
	private static final String FILE_NAME = "items.dat";
	private static final ItemPoolManager INSTANCE = new ItemPoolManager();

	private final Map<String, PoolItemEntry> byId = new LinkedHashMap<>();
	private boolean loaded;

	private ItemPoolManager() {
	}

	public static ItemPoolManager get() {
		return INSTANCE;
	}

	public synchronized void initialize() {
		EmfRuntimeDataFolder.initialize();
		SpecialItemManager.get().initialize();
		load();
	}

	public synchronized void load() {
		Path path = file();
		ItemPoolFile file = CompressedStorage.loadCompressedJson(path, ItemPoolFile.class, ItemPoolFile::new);
		byId.clear();
		if (file.items != null) {
			for (PoolItemEntry entry : file.items) {
				if (entry == null || entry.itemId == null || entry.itemId.isBlank()) {
					continue;
				}
				byId.put(entry.itemId, entry);
			}
		}
		loaded = true;
		ElementsMoreFeatures.LOGGER.info("[EMF ItemPool] items.dat geladen ({} Items)", byId.size());
	}

	public Path file() {
		return EmfRuntimeDataFolder.resolve(FILE_NAME);
	}

	public synchronized boolean contains(String itemId) {
		ensureLoaded();
		return itemId != null && byId.containsKey(itemId);
	}

	public synchronized List<PoolItemEntry> allItems() {
		ensureLoaded();
		return List.copyOf(byId.values());
	}

	public synchronized List<PoolItemEntry> allItemsForJei() {
		ensureLoaded();
		Map<String, PoolItemEntry> merged = new LinkedHashMap<>();
		for (PoolItemEntry entry : byId.values()) {
			if (entry != null && entry.itemId != null) {
				merged.put(entry.itemId, entry);
			}
		}
		for (PoolItemEntry special : SpecialItemManager.get().normalizedItemsForJei()) {
			if (special != null && special.itemId != null) {
				merged.putIfAbsent(special.itemId, special);
			}
		}
		return List.copyOf(merged.values());
	}

	/**
	 * Verarbeitet ein erhaltenes Item. Special → überspringen; Ignore → überspringen;
	 * bekannt → nichts; neu → speichern + Upload.
	 *
	 * @return Optional der neuen Pool-Eintrags, sonst empty
	 */
	public Optional<PoolItemEntry> offer(ItemStack stack) {
		if (!SpecialItemManager.get().isReady()) {
			return Optional.empty();
		}
		if (stack == null || stack.isEmpty()) {
			return Optional.empty();
		}

		Optional<SpecialItemDefinition> special = SpecialItemManager.get().findSpecialItem(stack);
		if (special.isPresent()) {
			if (EmfConfig.debugApi()) {
				ElementsMoreFeatures.LOGGER.info(
						"[EMF ItemPool] Special-Item '{}', überspringe Variante",
						special.get().specialId);
			}
			return Optional.empty();
		}

		if (ItemIgnoreManager.shouldIgnoreItem(stack)) {
			return Optional.empty();
		}

		ItemIdentifier.IdentifiedItem identified = ItemIdentifier.identify(stack);
		if (identified == null || identified.itemId().isBlank()) {
			return Optional.empty();
		}

		synchronized (this) {
			ensureLoaded();
			if (byId.containsKey(identified.itemId())) {
				return Optional.empty();
			}

			PoolItemEntry entry = new PoolItemEntry();
			entry.itemId = identified.itemId();
			entry.registryId = identified.registryId();
			entry.itemName = identified.itemName();
			entry.itemData = identified.itemData();

			byId.put(entry.itemId, entry);
			persistLocked();
			ElementsMoreFeatures.LOGGER.info(
					"[EMF ItemPool] Neu: '{}' ({})", entry.itemName, entry.itemId);
			ItemUploader.uploadAsync(entry);
			return Optional.of(entry);
		}
	}

	private void persistLocked() {
		ItemPoolFile file = new ItemPoolFile();
		file.items = new ArrayList<>(byId.values());
		try {
			CompressedStorage.saveCompressedJson(file(), file);
		} catch (IOException exception) {
			ElementsMoreFeatures.LOGGER.error("[EMF ItemPool] Speichern fehlgeschlagen: {}", file(), exception);
		}
	}

	private void ensureLoaded() {
		if (!loaded) {
			load();
		}
	}
}
