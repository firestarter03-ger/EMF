package net.fire.emf.client.itempool;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.api.EmfApiClient;
import net.fire.emf.client.config.EmfConfig;

import java.util.concurrent.CompletableFuture;

/**
 * Sendet neue Items an {@code POST /api/mod/sync-item}.
 */
public final class ItemUploader {
	private static final Gson GSON = new Gson();

	private ItemUploader() {
	}

	public static void uploadAsync(PoolItemEntry entry) {
		if (entry == null || entry.itemId == null || entry.itemId.isBlank()) {
			return;
		}
		if (!EmfConfig.isDataTrackingEnabled()) {
			return;
		}
		JsonObject body = buildBody(entry);
		CompletableFuture.runAsync(() -> {
			try {
				JsonObject response = EmfApiClient.get().postAuthed("/api/mod/sync-item", body);
				if (response == null) {
					ElementsMoreFeatures.LOGGER.warn(
							"[EMF ItemPool] Upload fehlgeschlagen: {} ({})", entry.itemName, entry.itemId);
					return;
				}
				if (EmfConfig.debugApi()) {
					boolean stored = response.has("stored") && response.get("stored").getAsBoolean();
					ElementsMoreFeatures.LOGGER.info(
							"[EMF ItemPool] sync-item '{}' stored={}", entry.itemName, stored);
				}
			} catch (Exception exception) {
				ElementsMoreFeatures.LOGGER.warn(
						"[EMF ItemPool] Upload-Fehler '{}': {}", entry.itemName, exception.getMessage());
			}
		});
	}

	private static JsonObject buildBody(PoolItemEntry entry) {
		JsonObject body = new JsonObject();
		body.addProperty("itemId", entry.itemId);
		body.addProperty("itemName", entry.itemName == null ? "" : entry.itemName);
		// itemData 1:1 unverändert
		body.add("itemData", GSON.toJsonTree(entry.itemData == null ? new ItemDataPayload() : entry.itemData));
		return body;
	}
}
