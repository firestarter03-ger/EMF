package net.fire.emf.client.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP-Zugriff auf den EMF-Backend-Server (Token im Header {@code x-auth-token}).
 */
public final class EmfApiHttpClient {
	private static final Gson GSON = new Gson();
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

	private final java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.build();

	public JsonObject get(String endpoint) throws IOException, InterruptedException {
		return sendGet(endpoint, null);
	}

	public JsonObject getWithToken(String endpoint, String token) throws IOException, InterruptedException {
		return sendGet(endpoint, token);
	}

	public JsonObject post(String endpoint, JsonObject body) throws IOException, InterruptedException {
		return sendPost(endpoint, body, null);
	}

	public JsonObject postWithToken(String endpoint, JsonObject body, String token) throws IOException, InterruptedException {
		return sendPost(endpoint, body, token);
	}

	private JsonObject sendGet(String endpoint, String token) throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl() + endpoint))
				.timeout(REQUEST_TIMEOUT)
				.GET();
		if (token != null && !token.isBlank()) {
			builder.header("x-auth-token", token);
		}
		return execute(builder.build(), "GET", endpoint);
	}

	private JsonObject sendPost(String endpoint, JsonObject body, String token) throws IOException, InterruptedException {
		String json = GSON.toJson(body == null ? new JsonObject() : body);
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl() + endpoint))
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json));
		if (token != null && !token.isBlank()) {
			builder.header("x-auth-token", token);
		}
		if (EmfConfig.debugApi()) {
			ElementsMoreFeatures.LOGGER.info("[EMF API] POST {} body={}", endpoint, json);
		}
		return execute(builder.build(), "POST", endpoint);
	}

	private JsonObject execute(HttpRequest request, String method, String endpoint)
			throws IOException, InterruptedException {
		if (EmfConfig.debugApi()) {
			ElementsMoreFeatures.LOGGER.info("[EMF API] {} {}", method, baseUrl() + endpoint);
		}
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (EmfConfig.debugApi()) {
			ElementsMoreFeatures.LOGGER.info("[EMF API] <- {} {}", response.statusCode(), response.body());
		}
		int status = response.statusCode();
		if (status < 200 || status >= 300) {
			ElementsMoreFeatures.LOGGER.warn("[EMF API] {} {} fehlgeschlagen: {} {}", method, endpoint, status, response.body());
			return null;
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return new JsonObject();
		}
		try {
			return GSON.fromJson(body, JsonObject.class);
		} catch (JsonSyntaxException exception) {
			ElementsMoreFeatures.LOGGER.warn("[EMF API] Ungültiges JSON von {} {}", method, endpoint, exception);
			return null;
		}
	}

	private static String baseUrl() {
		String url = EmfConfig.HANDLER.instance().apiBaseUrl;
		if (url == null || url.isBlank()) {
			return EmfConfig.DEFAULT_API_BASE_URL;
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url.trim();
	}
}
