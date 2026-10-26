package net.st3ix.lyrix.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class SpotifyApi {
	public static class ApiException extends Exception {
		public ApiException(String message) {
			super(message);
		}
	}

	private static final String BASE = "https://api.spotify.com/v1";
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final SpotifyAuth auth;

	public SpotifyApi(SpotifyAuth auth) {
		this.auth = auth;
	}

	public PlaybackState player() throws Exception {
		long start = System.nanoTime();
		HttpResponse<String> res = send("GET", "/me/player?additional_types=track", null);
		long end = System.nanoTime();
		if (res.statusCode() == 204 || res.body() == null || res.body().isBlank()) return null;
		check(res);
		JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
		JsonElement itemEl = json.get("item");
		if (itemEl == null || itemEl.isJsonNull()) return null;
		JsonObject item = itemEl.getAsJsonObject();
		if (!item.has("id") || item.get("id").isJsonNull()) return null;
		long progress = num(json, "progress_ms", 0);
		boolean playing = json.has("is_playing") && json.get("is_playing").getAsBoolean();
		JsonObject album = item.has("album") ? item.getAsJsonObject("album") : null;
		String deviceName = "";
		int volume = -1;
		if (json.has("device") && json.get("device").isJsonObject()) {
			JsonObject d = json.getAsJsonObject("device");
			deviceName = str(d, "name");
			volume = (int) num(d, "volume_percent", -1);
		}
		boolean shuffle = json.has("shuffle_state") && !json.get("shuffle_state").isJsonNull() && json.get("shuffle_state").getAsBoolean();
		String repeat = json.has("repeat_state") && !json.get("repeat_state").isJsonNull() ? json.get("repeat_state").getAsString() : "off";
		return new PlaybackState(
				item.get("id").getAsString(),
				str(item, "uri"),
				str(item, "name"),
				artists(item),
				album != null ? str(album, "name") : "",
				num(item, "duration_ms", 0),
				progress,
				playing,

				start + (end - start) / 2,
				album != null ? cover(album) : null,
				deviceName, volume, shuffle, repeat);
	}

	public List<TrackResult> search(String query) throws Exception {
		List<TrackResult> all = new ArrayList<>(searchPage(query, 0));
		if (all.size() >= 10) {
			try {
				all.addAll(searchPage(query, 10));
			} catch (Exception ignored) {
			}
		}
		return all;
	}

	private List<TrackResult> searchPage(String query, int offset) throws Exception {
		HttpResponse<String> res = send("GET", "/search?type=track&limit=10&offset=" + offset
				+ "&q=" + SpotifyAuth.enc(query), null);
		check(res);
		JsonArray items = JsonParser.parseString(res.body()).getAsJsonObject()
				.getAsJsonObject("tracks").getAsJsonArray("items");
		return tracks(items);
	}

	public List<TrackResult> queue() throws Exception {
		HttpResponse<String> res = send("GET", "/me/player/queue", null);
		check(res);
		JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
		return json.has("queue") ? tracks(json.getAsJsonArray("queue")) : List.of();
	}

	public List<Device> devices() throws Exception {
		HttpResponse<String> res = send("GET", "/me/player/devices", null);
		check(res);
		List<Device> list = new ArrayList<>();
		for (JsonElement el : JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("devices")) {
			JsonObject d = el.getAsJsonObject();
			if (!d.has("id") || d.get("id").isJsonNull()) continue;
			list.add(new Device(d.get("id").getAsString(), str(d, "name"), str(d, "type"),
					d.has("is_active") && d.get("is_active").getAsBoolean(), (int) num(d, "volume_percent", -1)));
		}
		return list;
	}

	public boolean isLiked(String trackId) throws Exception {
		HttpResponse<String> res = send("GET", "/me/tracks/contains?ids=" + trackId, null);
		check(res);
		JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
		return !arr.isEmpty() && arr.get(0).getAsBoolean();
	}

	public void play(String trackUri, String deviceId) throws Exception {
		String path = "/me/player/play" + (deviceId != null ? "?device_id=" + SpotifyAuth.enc(deviceId) : "");
		check(send("PUT", path, "{\"uris\":[\"" + trackUri + "\"],\"position_ms\":0}"));
	}

	public void resume() throws Exception {
		check(send("PUT", "/me/player/play", null));
	}

	public void pause() throws Exception {
		check(send("PUT", "/me/player/pause", null));
	}

	public void next() throws Exception {
		check(send("POST", "/me/player/next", null));
	}

	public void previous() throws Exception {
		check(send("POST", "/me/player/previous", null));
	}

	public void seek(long positionMs) throws Exception {
		check(send("PUT", "/me/player/seek?position_ms=" + Math.max(0, positionMs), null));
	}

	public void volume(int percent) throws Exception {
		check(send("PUT", "/me/player/volume?volume_percent=" + Math.clamp(percent, 0, 100), null));
	}

	public void shuffle(boolean on) throws Exception {
		check(send("PUT", "/me/player/shuffle?state=" + on, null));
	}

	public void repeat(String mode) throws Exception {
		check(send("PUT", "/me/player/repeat?state=" + mode, null));
	}

	public void addToQueue(String trackUri) throws Exception {
		check(send("POST", "/me/player/queue?uri=" + SpotifyAuth.enc(trackUri), null));
	}

	public void transfer(String deviceId) throws Exception {
		check(send("PUT", "/me/player", "{\"device_ids\":[\"" + deviceId + "\"],\"play\":true}"));
	}

	public void setLiked(String trackId, boolean liked) throws Exception {
		check(send(liked ? "PUT" : "DELETE", "/me/tracks?ids=" + trackId, null));
	}

	private HttpResponse<String> send(String method, String path, String jsonBody) throws Exception {
		HttpResponse<String> res = sendOnce(method, path, jsonBody);
		if (res.statusCode() == 401) {
			auth.invalidateAccessToken();
			res = sendOnce(method, path, jsonBody);
		}
		return res;
	}

	private HttpResponse<String> sendOnce(String method, String path, String jsonBody) throws Exception {
		String token = auth.accessToken();
		if (token == null) throw new ApiException("Not connected to Spotify.");
		HttpRequest.BodyPublisher body = jsonBody == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(jsonBody);
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path))
				.timeout(Duration.ofSeconds(10))
				.header("Authorization", "Bearer " + token)
				.method(method, body);
		if (jsonBody != null) b.header("Content-Type", "application/json");
		return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static void check(HttpResponse<String> res) throws ApiException {
		int code = res.statusCode();
		if (code >= 200 && code < 300) return;
		String reason = "";
		try {
			JsonObject err = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("error");
			if (err.has("reason")) reason = err.get("reason").getAsString();
			else if (err.has("message")) reason = err.get("message").getAsString();
		} catch (Exception ignored) {
		}
		String lower = reason.toLowerCase();
		throw new ApiException(switch (code) {
			case 403 -> lower.contains("scope")
					? "Missing permission. Reconnect Spotify in the Account tab."
					: lower.contains("premium") || reason.isEmpty()
					? "This needs Spotify Premium (or your account is not added to the app)."
					: "Not allowed: " + reason;
			case 404 -> "No active device. Open Spotify somewhere and play something.";
			case 429 -> "Too many requests, please wait a moment...";
			default -> "Spotify error " + code + (reason.isEmpty() ? "" : ": " + reason);
		});
	}

	private static List<TrackResult> tracks(JsonArray items) {
		List<TrackResult> list = new ArrayList<>();
		for (JsonElement el : items) {
			if (el == null || el.isJsonNull() || !el.isJsonObject()) continue;
			JsonObject t = el.getAsJsonObject();
			if (!t.has("uri") || !"track".equals(str(t, "type"))) continue;
			JsonObject album = t.has("album") ? t.getAsJsonObject("album") : null;
			list.add(new TrackResult(str(t, "uri"), str(t, "name"), artists(t),
					album != null ? str(album, "name") : "", num(t, "duration_ms", 0),
					album != null ? cover(album) : null));
		}
		return list;
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static long num(JsonObject o, String key, long fallback) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? fallback : e.getAsLong();
	}

	private static String artists(JsonObject track) {
		StringBuilder sb = new StringBuilder();
		if (track.has("artists")) {
			for (JsonElement a : track.getAsJsonArray("artists")) {
				if (!sb.isEmpty()) sb.append(", ");
				sb.append(str(a.getAsJsonObject(), "name"));
			}
		}
		return sb.toString();
	}

	private static String cover(JsonObject album) {
		if (!album.has("images")) return null;
		String best = null;
		int bestDiff = Integer.MAX_VALUE;
		for (JsonElement el : album.getAsJsonArray("images")) {
			JsonObject img = el.getAsJsonObject();
			int w = (int) num(img, "width", 300);
			int diff = Math.abs(w - 300);
			if (diff < bestDiff) {
				bestDiff = diff;
				best = str(img, "url");
			}
		}
		return best;
	}
}
