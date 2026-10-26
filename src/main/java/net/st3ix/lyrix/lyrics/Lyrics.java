package net.st3ix.lyrix.lyrics;

import java.util.List;

public record Lyrics(List<Line> lines, String raw, long durationMs) {
	public record Word(String text, long start, long end) {
	}

	public record Line(long start, long end, String text, List<Word> words) {
		public int wordAt(long t) {
			int idx = -1;
			for (int i = 0; i < words.size(); i++) {
				if (words.get(i).start() <= t) idx = i;
				else break;
			}
			return idx;
		}
	}

	public int lineAt(long t) {
		int lo = 0, hi = lines.size() - 1, found = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).start() <= t) {
				found = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		if (found >= 0 && t >= lines.get(found).end()) return -1;
		return found;
	}

	public int lastStartedLine(long t) {
		int found = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).start() <= t) found = i;
			else break;
		}
		return found;
	}
}
