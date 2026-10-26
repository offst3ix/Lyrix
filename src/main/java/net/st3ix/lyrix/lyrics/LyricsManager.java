package net.st3ix.lyrix.lyrics;

import net.st3ix.lyrix.LyrixClient;
import net.st3ix.lyrix.spotify.PlaybackState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LyricsManager {
	public enum Status { NONE, LOADING, FOUND, NOT_FOUND, ERROR }

	private final LrcLibClient client = new LrcLibClient();
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Lyrix-Lyrics");
		t.setDaemon(true);
		return t;
	});
	private final Map<String, Lyrics> cache = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Lyrics> eldest) {
			return size() > 40;
		}
	};

	private final Map<String, Long> matchDiffs = new java.util.concurrent.ConcurrentHashMap<>();
	private volatile String trackId;

	public Long matchDiffMs(String id) {
		return id == null ? null : matchDiffs.get(id);
	}
	private volatile Lyrics lyrics;
	private volatile Status status = Status.NONE;

	public Lyrics lyrics() {
		return lyrics;
	}

	public Status status() {
		return status;
	}

	public String trackId() {
		return trackId;
	}

	public void onTrack(PlaybackState state) {
		if (state.trackId().equals(trackId)) return;
		trackId = state.trackId();
		lyrics = null;
		synchronized (cache) {
			Lyrics cached = cache.get(state.trackId());
			if (cached != null) {
				lyrics = cached;
				status = Status.FOUND;
				return;
			}
		}
		status = Status.LOADING;
		String id = state.trackId();
		executor.execute(() -> {
			try {
				LrcLibClient.Found found = client.fetchSynced(state.title(), state.artist(), state.album(), state.durationMs());
				if (!id.equals(trackId)) return;
				if (found == null) {
					status = Status.NOT_FOUND;
					return;
				}
				matchDiffs.put(id, Math.round(found.durationSec() * 1000 - state.durationMs()));
				Lyrics parsed = LrcParser.parse(found.lrc(), state.durationMs());
				if (parsed.lines().isEmpty()) {
					status = Status.NOT_FOUND;
					return;
				}
				synchronized (cache) {
					cache.put(id, parsed);
				}
				lyrics = parsed;
				status = Status.FOUND;
			} catch (Exception e) {
				LyrixClient.LOGGER.warn("Could not load lyrics", e);
				if (id.equals(trackId)) status = Status.ERROR;
			}
		});
	}

	public void reparse() {
		synchronized (cache) {
			cache.replaceAll((id, l) -> LrcParser.parse(l.raw(), l.durationMs()));
			Lyrics current = trackId != null ? cache.get(trackId) : null;
			if (current != null) lyrics = current;
		}
	}

	public String statusText() {
		return switch (status) {
			case NONE -> "";
			case LOADING -> "Loading lyrics...";
			case FOUND -> "Lyrics found";
			case NOT_FOUND -> "No synced lyrics found for this song";
			case ERROR -> "Could not load lyrics";
		};
	}
}
