package net.st3ix.lyrix.spotify;

import net.st3ix.lyrix.LyrixClient;
import net.st3ix.lyrix.lyrics.LyricsManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class SpotifyService {
	private static final long POLL_PLAYING_MS = 2000;
	private static final long POLL_IDLE_MS = 2500;

	private final SpotifyAuth auth = new SpotifyAuth();
	private final SpotifyApi api = new SpotifyApi(auth);
	private final LyricsManager lyrics;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "Lyrix-Poller");
		t.setDaemon(true);
		return t;
	});
	private final Map<String, Boolean> liked = new ConcurrentHashMap<>();

	private volatile PlaybackState current;
	private volatile String message = "";
	private volatile long messageUntil;
	private volatile boolean messageIsError;
	private volatile long trackChangedAt;
	private volatile long ignoreUpdatesUntil;
	private long nextPollAt;

	public SpotifyService(LyricsManager lyrics) {
		this.lyrics = lyrics;
		executor.scheduleWithFixedDelay(this::tick, 1000, 250, TimeUnit.MILLISECONDS);
	}

	public SpotifyAuth auth() {
		return auth;
	}

	public PlaybackState current() {
		return current;
	}

	public long trackChangedAt() {
		return trackChangedAt;
	}

	public Boolean liked() {
		PlaybackState s = current;
		return s == null ? null : liked.get(s.trackId());
	}

	public String message() {
		return System.currentTimeMillis() < messageUntil ? message : "";
	}

	public boolean messageIsError() {
		return messageIsError;
	}

	private void tick() {
		if (System.currentTimeMillis() < nextPollAt) return;
		if (!auth.isLoggedIn()) {
			current = null;
			nextPollAt = System.currentTimeMillis() + 1000;
			return;
		}
		poll();
	}

	private void poll() {
		long delay = POLL_IDLE_MS;
		try {
			PlaybackState state = api.player();
			PlaybackState old = current;
			if (System.currentTimeMillis() < ignoreUpdatesUntil && old != null && state != null && old.trackId().equals(state.trackId())) {
				state = new PlaybackState(state.trackId(), state.trackUri(), state.title(), state.artist(), state.album(),
						state.durationMs(), state.progressMs(), state.playing(), state.fetchedNanos(), state.coverUrl(),
						state.deviceName(), old.volume(), old.shuffle(), old.repeat());
			}
			if (state != null && (old == null || !old.trackId().equals(state.trackId()))) {
				trackChangedAt = System.currentTimeMillis();
				fetchLiked(state.trackId());
			}
			current = state;
			if (state != null) {
				lyrics.onTrack(state);
				if (state.playing()) delay = POLL_PLAYING_MS;
			}
		} catch (SpotifyApi.ApiException e) {
			setError(e.getMessage());
			delay = 8000;
		} catch (Exception e) {
			LyrixClient.LOGGER.debug("Spotify request failed", e);
			delay = 8000;
		}
		nextPollAt = System.currentTimeMillis() + delay;
	}

	private void fetchLiked(String trackId) {
		if (liked.containsKey(trackId)) return;
		executor.execute(() -> {
			try {
				liked.put(trackId, api.isLiked(trackId));
			} catch (Exception ignored) {
			}
		});
	}

	private void pollSoon() {
		nextPollAt = System.currentTimeMillis() + 350;
	}

	private void setError(String msg) {
		message = msg;
		messageIsError = true;
		messageUntil = System.currentTimeMillis() + 6000;
	}

	public void info(String msg) {
		message = msg;
		messageIsError = false;
		messageUntil = System.currentTimeMillis() + 3000;
	}

	private interface ApiCall {
		void run() throws Exception;
	}

	private void command(ApiCall call) {
		executor.execute(() -> {
			try {
				call.run();
				pollSoon();
			} catch (Exception e) {
				setError(e.getMessage());
			}
		});
	}

	private void optimistic(PlaybackState updated) {
		current = updated;
		ignoreUpdatesUntil = System.currentTimeMillis() + 2500;
	}

	public void play(TrackResult track) {
		executor.execute(() -> {
			try {
				String device = pickDevice();
				boolean album = track.albumUri() != null && !track.albumUri().isEmpty();
				if (album) api.playInAlbum(track.uri(), track.albumUri(), device);
				else api.play(track.uri(), device);
				if (!startedPlaying(track)) {
					api.play(track.uri(), device);
					if (!startedPlaying(track)) {
						PlaybackState s = api.player();
						if (s != null && track.uri().equals(s.trackUri()) && !s.playing()) api.resume();
					}
				}
				info("Playing " + track.title());
				pollSoon();
			} catch (Exception e) {
				setError(e.getMessage());
			}
		});
	}

	private String pickDevice() throws Exception {
		List<Device> list = api.devices();
		if (list.isEmpty()) {
			throw new SpotifyApi.ApiException("No Spotify device found. Open Spotify on your PC or phone first.");
		}
		for (Device d : list) {
			if (d.active()) return d.id();
		}
		return list.get(0).id();
	}

	private boolean startedPlaying(TrackResult track) throws Exception {
		for (int i = 0; i < 4; i++) {
			Thread.sleep(400);
			PlaybackState s = api.player();
			boolean same = s != null && (track.uri().equals(s.trackUri()) || track.title().equalsIgnoreCase(s.title()));
			if (same && s.playing()) {
				current = s;
				return true;
			}
		}
		return false;
	}

	public void togglePlay() {
		PlaybackState s = current;
		boolean playing = s != null && s.playing();
		command(playing ? api::pause : api::resume);
		if (s != null) current = s.withPlaying(!playing);
	}

	public void next() {
		command(api::next);
	}

	public void skipTo(TrackResult track, int skips, Runnable done) {
		executor.execute(() -> {
			try {
				info("Skipping to " + track.title() + "...");
				for (int i = 0; i < skips; i++) {
					api.next();

					if (i + 1 < skips) Thread.sleep(220);
				}
				pollSoon();
			} catch (Exception e) {
				setError(e.getMessage());
			}
			done.run();
		});
	}

	public void previous() {
		command(api::previous);
	}

	public void seek(long ms) {
		PlaybackState s = current;
		if (s != null) current = s.withProgress(ms);
		command(() -> api.seek(ms));
	}

	public void setVolume(int percent) {
		PlaybackState s = current;
		if (s != null) optimistic(s.withVolume(percent));
		command(() -> api.volume(percent));
	}

	public void toggleShuffle() {
		PlaybackState s = current;
		if (s == null) return;
		boolean on = !s.shuffle();
		optimistic(s.withShuffle(on));
		command(() -> api.shuffle(on));
	}

	public void cycleRepeat() {
		PlaybackState s = current;
		if (s == null) return;
		String next = switch (s.repeat()) {
			case "off" -> "context";
			case "context" -> "track";
			default -> "off";
		};
		optimistic(s.withRepeat(next));
		command(() -> api.repeat(next));
	}

	public void toggleLike() {
		PlaybackState s = current;
		if (s == null) return;
		boolean now = !Boolean.TRUE.equals(liked.get(s.trackId()));
		liked.put(s.trackId(), now);
		String id = s.trackId();
		executor.execute(() -> {
			try {
				api.setLiked(id, now);
				info(now ? "Added to Liked Songs" : "Removed from Liked Songs");
			} catch (Exception e) {
				liked.put(id, !now);
				setError(e.getMessage());
			}
		});
	}

	public void addToQueue(TrackResult track) {
		executor.execute(() -> {
			try {
				api.addToQueue(track.uri());
				info("Added to queue: " + track.title());
			} catch (Exception e) {
				setError(e.getMessage());
			}
		});
	}

	public void transfer(Device device) {
		command(() -> {
			api.transfer(device.id());
			info("Playing on " + device.name());
		});
	}

	public void search(String query, Consumer<List<TrackResult>> onResult, Consumer<String> onError) {
		async(() -> api.search(query), onResult, onError);
	}

	public void loadQueue(Consumer<List<TrackResult>> onResult, Consumer<String> onError) {
		async(api::queue, onResult, onError);
	}

	public void loadDevices(Consumer<List<Device>> onResult, Consumer<String> onError) {
		async(api::devices, onResult, onError);
	}

	private interface ApiGet<T> {
		T get() throws Exception;
	}

	private <T> void async(ApiGet<T> call, Consumer<T> onResult, Consumer<String> onError) {
		executor.execute(() -> {
			try {
				onResult.accept(call.get());
			} catch (Exception e) {
				onError.accept(e.getMessage());
			}
		});
	}
}
