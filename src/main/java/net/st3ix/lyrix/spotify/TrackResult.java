package net.st3ix.lyrix.spotify;

public record TrackResult(String uri, String title, String artist, String album, long durationMs, String coverUrl, String albumUri) {
}
