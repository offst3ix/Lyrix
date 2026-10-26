package net.st3ix.lyrix.spotify;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.st3ix.lyrix.LyrixClient;
import net.st3ix.lyrix.config.ModConfig;
import net.minecraft.util.Util;

import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public final class SpotifyAuth {
	public static final int PORT = 25588;
	public static final String REDIRECT_URI = "http://127.0.0.1:" + PORT + "/callback";
	private static final String SCOPES = "user-read-currently-playing user-read-playback-state user-modify-playback-state user-library-read user-library-modify";
	private static final Gson GSON = new Gson();

	private static class Tokens {
		String accessToken;
		String refreshToken;
		long expiresAt;
	}

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private Tokens tokens;
	private HttpServer server;
	private String pendingVerifier;
	private String pendingState;
	private volatile String status = "";

	public SpotifyAuth() {
		loadTokens();
	}

	public boolean isLoggedIn() {
		return tokens != null && tokens.refreshToken != null;
	}

	public String status() {
		return status;
	}

	public synchronized void startLogin() {
		String clientId = ModConfig.get().clientId.trim();
		if (clientId.isEmpty()) {
			status = "Enter your Client ID first.";
			return;
		}
		try {
			startCallbackServer();
		} catch (Exception e) {
			status = "Port " + PORT + " is already in use: " + e.getMessage();
			LyrixClient.LOGGER.error("Could not start callback server", e);
			return;
		}
		SecureRandom rnd = new SecureRandom();
		byte[] verifierBytes = new byte[64];
		rnd.nextBytes(verifierBytes);
		pendingVerifier = base64Url(verifierBytes);
		byte[] stateBytes = new byte[16];
		rnd.nextBytes(stateBytes);
		pendingState = base64Url(stateBytes);
		String challenge;
		try {
			challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(pendingVerifier.getBytes(StandardCharsets.US_ASCII)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		String url = "https://accounts.spotify.com/authorize"
				+ "?response_type=code"
				+ "&client_id=" + enc(clientId)
				+ "&scope=" + enc(SCOPES)
				+ "&redirect_uri=" + enc(REDIRECT_URI)
				+ "&code_challenge_method=S256"
				+ "&code_challenge=" + challenge
				+ "&state=" + pendingState;
		status = "Browser opened, log in to Spotify...";
		Util.getPlatform().openUri(URI.create(url));
	}

	public synchronized void logout() {
		tokens = null;
		try {
			Files.deleteIfExists(tokenFile());
		} catch (Exception ignored) {
		}
		status = "Logged out.";
	}

	public synchronized String accessToken() throws Exception {
		if (!isLoggedIn()) return null;
		if (tokens.accessToken == null || System.currentTimeMillis() > tokens.expiresAt - 60_000) {
			refresh();
		}
		return tokens.accessToken;
	}

	public synchronized void invalidateAccessToken() {
		if (tokens != null) tokens.expiresAt = 0;
	}

	private void refresh() throws Exception {
		Map<String, String> form = new HashMap<>();
		form.put("grant_type", "refresh_token");
		form.put("refresh_token", tokens.refreshToken);
		form.put("client_id", ModConfig.get().clientId.trim());
		JsonObject json = postToken(form);
		if (json == null) {
			tokens = null;
			Files.deleteIfExists(tokenFile());
			status = "Login expired, please connect again.";
			return;
		}
		applyTokenResponse(json);
	}

	private void startCallbackServer() throws Exception {
		if (server != null) return;
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
		server.setExecutor(Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "Lyrix-Callback");
			t.setDaemon(true);
			return t;
		}));
		server.createContext("/callback", exchange -> {
			Map<String, String> q = parseQuery(exchange.getRequestURI().getRawQuery());
			boolean ok = false;
			String message;
			if (q.containsKey("error")) {
				message = "Login cancelled: " + q.get("error");
			} else if (pendingState == null || !pendingState.equals(q.get("state"))) {
				message = "Invalid request (state does not match).";
			} else {
				try {
					exchangeCode(q.get("code"));
					ok = true;
					message = "Connected! You can close this window and go back to Minecraft.";
				} catch (Exception e) {
					LyrixClient.LOGGER.error("Token exchange failed", e);
					message = "Login failed: " + e.getMessage();
				}
			}
			status = message;
			byte[] body = page(ok, message).getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
			if (ok) stopServerLater();
		});
		server.start();
	}

	private void stopServerLater() {
		HttpServer s = server;
		server = null;
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(500);
			} catch (InterruptedException ignored) {
			}
			s.stop(0);
		}, "Lyrix-CallbackStop");
		t.setDaemon(true);
		t.start();
	}

	private synchronized void exchangeCode(String code) throws Exception {
		Map<String, String> form = new HashMap<>();
		form.put("grant_type", "authorization_code");
		form.put("code", code);
		form.put("redirect_uri", REDIRECT_URI);
		form.put("client_id", ModConfig.get().clientId.trim());
		form.put("code_verifier", pendingVerifier);
		JsonObject json = postToken(form);
		if (json == null) throw new IllegalStateException("Spotify rejected the code. Is the Redirect URI correct?");
		tokens = new Tokens();
		applyTokenResponse(json);
		pendingVerifier = null;
		pendingState = null;
	}

	private JsonObject postToken(Map<String, String> form) throws Exception {
		StringBuilder body = new StringBuilder();
		for (var e : form.entrySet()) {
			if (!body.isEmpty()) body.append('&');
			body.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
		}
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
				.timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) {
			LyrixClient.LOGGER.warn("Token request failed ({}): {}", res.statusCode(), res.body());
			return null;
		}
		return JsonParser.parseString(res.body()).getAsJsonObject();
	}

	private void applyTokenResponse(JsonObject json) {
		tokens.accessToken = json.get("access_token").getAsString();
		tokens.expiresAt = System.currentTimeMillis() + json.get("expires_in").getAsLong() * 1000L;
		if (json.has("refresh_token") && !json.get("refresh_token").isJsonNull()) {
			tokens.refreshToken = json.get("refresh_token").getAsString();
		}
		saveTokens();
	}

	private static Path tokenFile() {
		return ModConfig.dir().resolve("tokens.json");
	}

	private void loadTokens() {
		Path f = tokenFile();
		if (!Files.exists(f)) return;
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			tokens = GSON.fromJson(r, Tokens.class);
		} catch (Exception e) {
			LyrixClient.LOGGER.warn("Could not read saved login", e);
		}
	}

	private void saveTokens() {
		try {
			Files.createDirectories(ModConfig.dir());
			try (Writer w = Files.newBufferedWriter(tokenFile(), StandardCharsets.UTF_8)) {
				GSON.toJson(tokens, w);
			}
		} catch (Exception e) {
			LyrixClient.LOGGER.warn("Could not save login", e);
		}
	}

	private static Map<String, String> parseQuery(String raw) {
		Map<String, String> map = new HashMap<>();
		if (raw == null) return map;
		for (String part : raw.split("&")) {
			int i = part.indexOf('=');
			if (i <= 0) continue;
			map.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8),
					URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
		}
		return map;
	}

	private static String page(boolean ok, String message) {
		String color = ok ? "#1DB954" : "#e5534b";
		String safe = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return "<!doctype html><html><head><meta charset='utf-8'><title>Lyrix</title></head>"
				+ "<body style='margin:0;height:100vh;display:flex;align-items:center;justify-content:center;"
				+ "background:#121212;color:#fff;font-family:system-ui,sans-serif'>"
				+ "<div style='text-align:center;padding:32px 40px;border-radius:16px;background:#1e1e1e'>"
				+ "<div style='font-size:40px;color:" + color + "'>" + (ok ? "&#10003;" : "&#10007;") + "</div>"
				+ "<h2 style='margin:8px 0'>Lyrix</h2><p style='color:#b3b3b3'>" + safe + "</p></div></body></html>";
	}

	static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String base64Url(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
