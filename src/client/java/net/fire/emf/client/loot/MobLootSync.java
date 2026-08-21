package net.fire.emf.client.loot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.api.EmfApiClient;
import net.fire.emf.client.config.EmfConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Sendet neue Mob-Drops an {@code POST /api/mod/sync-loot}, wenn lokal noch kein Eintrag existierte.
 */
public final class MobLootSync {
	private static final Gson GSON = new Gson();

	private MobLootSync() {
	}

	/**
	 * Asynchroner Upload — nur bei Community-Service aktiv. Blockiert nicht den Client-Thread.
	 */
	public static void syncNewDropAsync(
			String mobName,
			String itemName,
			String itemNameJson,
			String hoverText,
			List<String> hoverLineJsons) {
		if (!EmfConfig.isDataTrackingEnabled()) {
			return;
		}
		if (mobName == null || mobName.isBlank() || itemName == null || itemName.isBlank()) {
			return;
		}
		JsonObject body = buildBody(mobName, itemName, itemNameJson, hoverText, hoverLineJsons);
		CompletableFuture.runAsync(() -> {
			try {
				JsonObject response = EmfApiClient.get().postAuthed("/api/mod/sync-loot", body);
				if (response == null) {
					ElementsMoreFeatures.LOGGER.warn(
							"[EMF Mob Loot Sync] Upload fehlgeschlagen: '{}' / '{}'", mobName, itemName);
					return;
				}
				if (EmfConfig.debugApi()) {
					boolean stored = response.has("stored") && response.get("stored").getAsBoolean();
					ElementsMoreFeatures.LOGGER.info(
							"[EMF Mob Loot Sync] '{}' / '{}' → stored={}", mobName, itemName, stored);
				}
			} catch (Exception exception) {
				ElementsMoreFeatures.LOGGER.warn(
						"[EMF Mob Loot Sync] Fehler bei '{}' / '{}': {}",
						mobName, itemName, exception.getMessage());
			}
		});
	}

	private static JsonObject buildBody(
			String mobName,
			String itemName,
			String itemNameJson,
			String hoverText,
			List<String> hoverLineJsons) {
		MobLootPoolStore.DropEntry lootData = new MobLootPoolStore.DropEntry();
		lootData.itemName = itemName;
		lootData.itemNameJson = itemNameJson == null ? "" : itemNameJson;
		lootData.hoverText = hoverText == null ? "" : hoverText;
		lootData.hoverLineJsons = hoverLineJsons == null ? List.of() : List.copyOf(hoverLineJsons);

		JsonObject body = new JsonObject();
		body.addProperty("mobName", mobName);
		body.addProperty("itemName", itemName);
		// 1:1 dasselbe Objektformat wie in mobs_loot_pool.json
		body.add("lootData", GSON.toJsonTree(lootData));
		return body;
	}
}
