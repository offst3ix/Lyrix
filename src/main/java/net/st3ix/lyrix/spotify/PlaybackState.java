package net.st3ix.lyrix.spotify;

public record PlaybackState(
		String trackId,
		String trackUri,
		String title,
		String artist,
		String album,
		long durationMs,
		long progressMs,
		boolean playing,
		long fetchedNanos,
		String coverUrl,
		String deviceName,
		int volume,
		boolean shuffle,
		String repeat
) {
	public long progressNow() {
		if (!playing) return progressMs;
		long elapsed = (System.nanoTime() - fetchedNanos) / 1_000_000L;
		return Math.min(durationMs, progressMs + elapsed);
	}

	public PlaybackState withPlaying(boolean p) {
		return new PlaybackState(trackId, trackUri, title, artist, album, durationMs, progressNow(), p, System.nanoTime(),
				coverUrl, deviceName, volume, shuffle, repeat);
	}

	public PlaybackState withProgress(long ms) {
		return new PlaybackState(trackId, trackUri, title, artist, album, durationMs, ms, playing, System.nanoTime(),
				coverUrl, deviceName, volume, shuffle, repeat);
	}

	public PlaybackState withVolume(int v) {
		return new PlaybackState(trackId, trackUri, title, artist, album, durationMs, progressNow(), playing, System.nanoTime(),
				coverUrl, deviceName, v, shuffle, repeat);
	}

	public PlaybackState withShuffle(boolean s) {
		return new PlaybackState(trackId, trackUri, title, artist, album, durationMs, progressNow(), playing, System.nanoTime(),
				coverUrl, deviceName, volume, s, repeat);
	}

	public PlaybackState withRepeat(String r) {
		return new PlaybackState(trackId, trackUri, title, artist, album, durationMs, progressNow(), playing, System.nanoTime(),
				coverUrl, deviceName, volume, shuffle, r);
	}
}
