package net.fire.emf.client.api;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfConfig;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Token-Auth wie CCLive: einmal {@code POST /register}, danach {@code x-auth-token} an geschützte Routen.
 * Identität = Minecraft-Spielername (kein UUID).
 */
public final class EmfApiClient {
	private static final EmfApiClient INSTANCE = new EmfApiClient();

	private final EmfApiHttpClient http = new EmfApiHttpClient();
	private final AtomicBoolean registerInFlight = new AtomicBoolean(false);
	private volatile String playerToken;
	private volatile String playerName;
	private volatile boolean registered;
	private boolean eventsRegistered;

	private EmfApiClient() {
	}

	public static EmfApiClient get() {
		return INSTANCE;
	}

	public void initialize() {
		if (eventsRegistered) {
			return;
		}
		eventsRegistered = true;
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				client.execute(this::registerAsync));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearSession());
	}

	public EmfApiHttpClient http() {
		return http;
	}

	public String playerToken() {
		return playerToken;
	}

	public String playerName() {
		return playerName;
	}

	public boolean isRegistered() {
		return registered && playerToken != null && !playerToken.isBlank();
	}

	public boolean isTrackingEnabled() {
		return EmfConfig.isDataTrackingEnabled();
	}

	/**
	 * Asynchrone Registrierung (Join / Start).
	 */
	public void registerAsync() {
		if (!isTrackingEnabled()) {
			ElementsMoreFeatures.LOGGER.info("[EMF API] Datentracking aus — Registrierung übersprungen");
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		if (!registerInFlight.compareAndSet(false, true)) {
			return;
		}
		String name = client.player.getName().getString();
		CompletableFuture.runAsync(() -> {
			try {
				registerInternal(name);
			} finally {
				registerInFlight.set(false);
			}
		});
	}

	/**
	 * Synchron vor geschützten Uploads — blockiert den aufrufenden Thread (nicht Render-Thread).
	 */
	public boolean ensureRegisteredSync() {
		if (isRegistered()) {
			return true;
		}
		if (!isTrackingEnabled()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return false;
		}
		return registerInternal(client.player.getName().getString());
	}

	public void refreshRegistration() {
		CompletableFuture.runAsync(this::refreshRegistrationSync);
	}

	public enum RefreshResult {
		SUCCESS,
		FALLBACK,
		FAILED
	}

	/**
	 * Token verwerfen, erneut {@code POST /register}; bei Fail altes Token wiederherstellen.
	 */
	public RefreshResult refreshRegistrationSync() {
		if (!isTrackingEnabled()) {
			return RefreshResult.FAILED;
		}
		Minecraft client = Minecraft.getInstance();
		String name = playerName;
		if ((name == null || name.isBlank()) && client.player != null) {
			name = client.player.getName().getString();
		}
		if (name == null || name.isBlank()) {
			return RefreshResult.FAILED;
		}
		String oldToken = playerToken;
		registered = false;
		playerToken = null;
		if (registerInternal(name)) {
			return RefreshResult.SUCCESS;
		}
		if (oldToken != null && !oldToken.isBlank()) {
			playerToken = oldToken;
			registered = true;
			ElementsMoreFeatures.LOGGER.warn("[EMF API] Re-Register fehlgeschlagen — altes Token behalten");
			return RefreshResult.FALLBACK;
		}
		return RefreshResult.FAILED;
	}

	public JsonObject postAuthed(String endpoint, JsonObject body) throws Exception {
		if (!ensureRegisteredSync()) {
			return null;
		}
		return http.postWithToken(endpoint, body, playerToken);
	}

	public JsonObject getAuthed(String endpoint) throws Exception {
		if (!ensureRegisteredSync()) {
			return null;
		}
		return http.getWithToken(endpoint, playerToken);
	}

	private boolean registerInternal(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		try {
			JsonObject body = new JsonObject();
			body.addProperty("player", name.trim());
			JsonObject response = http.post("/register", body);
			if (response != null && response.has("token")) {
				playerToken = response.get("token").getAsString();
				playerName = name.trim();
				registered = playerToken != null && !playerToken.isBlank();
				if (registered) {
					ElementsMoreFeatures.LOGGER.info("[EMF API] Registriert als '{}'", playerName);
					return true;
				}
			}
			ElementsMoreFeatures.LOGGER.warn("[EMF API] Registrierung ohne Token-Antwort");
		} catch (Exception exception) {
			ElementsMoreFeatures.LOGGER.warn("[EMF API] Registrierung fehlgeschlagen: {}", exception.getMessage());
		}
		return false;
	}

	private void clearSession() {
		// Token bewusst im Memory behalten (wie CCLive) — nur Name-Wechsel bei neuem Join relevant.
		registerInFlight.set(false);
	}
}
