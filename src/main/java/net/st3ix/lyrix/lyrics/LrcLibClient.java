package net.st3ix.lyrix.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class LrcLibClient {
	private static final String BASE = "https://lrclib.net/api";
	private static final String USER_AGENT = "Lyrix/1.0 (Minecraft Fabric mod; https://docs.st3ix.net)";
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	public String fetchSynced(String title, String artist, String album, long durationMs) throws Exception {
		String mainArtist = artist.contains(",") ? artist.substring(0, artist.indexOf(',')).trim() : artist;
		long durationSec = Math.round(durationMs / 1000.0);

		HttpResponse<String> res = get("/get?track_name=" + enc(title) + "&artist_name=" + enc(mainArtist)
				+ "&album_name=" + enc(album) + "&duration=" + durationSec);
		if (res.statusCode() == 200) {
			String synced = synced(JsonParser.parseString(res.body()).getAsJsonObject());
			if (synced != null) return synced;
		}

		String cleanTitle = title.replaceAll("\\s*[(\\[].*?[)\\]]", "").replaceAll("\\s+-\\s+.*$", "").trim();
		if (cleanTitle.isEmpty()) cleanTitle = title;
		res = get("/search?track_name=" + enc(cleanTitle) + "&artist_name=" + enc(mainArtist));
		if (res.statusCode() != 200) return null;
		JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
		String best = null;
		double bestDiff = Double.MAX_VALUE;
		for (JsonElement el : arr) {
			JsonObject o = el.getAsJsonObject();
			String synced = synced(o);
			if (synced == null) continue;
			double d = o.has("duration") && !o.get("duration").isJsonNull() ? o.get("duration").getAsDouble() : 0;
			double diff = Math.abs(d - durationSec);

			if (diff < bestDiff && diff <= 3) {
				bestDiff = diff;
				best = synced;
			}
		}
		return best;
	}

	private static String synced(JsonObject o) {
		JsonElement s = o.get("syncedLyrics");
		if (s == null || s.isJsonNull()) return null;
		String str = s.getAsString();
		return str.isBlank() ? null : str;
	}

	private HttpResponse<String> get(String path) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", USER_AGENT)
				.GET()
				.build();
		return http.send(req, HttpResponse.BodyHandlers.ofString());
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
