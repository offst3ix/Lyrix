package net.st3ix.lyrix.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
	private static final Pattern LINE_TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
	private static final Pattern WORD_TIME = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");

	private record RawLine(long start, String text) {
	}

	private LrcParser() {
	}

	public static Lyrics parse(String lrc, long durationMs) {
		List<RawLine> raw = new ArrayList<>();
		for (String line : lrc.split("\\r?\\n")) {
			Matcher m = LINE_TIME.matcher(line);
			List<Long> times = new ArrayList<>();
			int end = 0;
			while (m.find() && m.start() == end) {
				times.add(toMs(m.group(1), m.group(2), m.group(3)));
				end = m.end();
			}
			if (times.isEmpty()) continue;
			String text = line.substring(end).trim();
			for (long t : times) raw.add(new RawLine(t, text));
		}
		raw.sort((a, b) -> Long.compare(a.start, b.start));

		List<Lyrics.Line> lines = new ArrayList<>();
		for (int i = 0; i < raw.size(); i++) {
			RawLine r = raw.get(i);
			String plain = WORD_TIME.matcher(r.text).replaceAll("").replaceAll("\\s+", " ").trim();

			if (plain.isEmpty() || plain.equals("♪")) continue;
			long next = i + 1 < raw.size() ? raw.get(i + 1).start : Math.max(r.start + 5000, durationMs);
			long lineEnd = Math.min(next, r.start + 12_000);
			if (lineEnd <= r.start) lineEnd = r.start + 1500;
			List<Lyrics.Word> words = WORD_TIME.matcher(r.text).find()
					? enhancedWords(r.text, r.start, lineEnd)
					: estimateWords(plain, r.start, lineEnd);
			if (words.isEmpty()) continue;
			lines.add(new Lyrics.Line(r.start, lineEnd, plain, words));
		}
		return new Lyrics(lines, lrc, durationMs);
	}

	private static List<Lyrics.Word> enhancedWords(String text, long lineStart, long lineEnd) {
		List<long[]> times = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		Matcher m = WORD_TIME.matcher(text);
		int lastEnd = 0;
		long lastTime = lineStart;
		while (m.find()) {
			addWords(text.substring(lastEnd, m.start()), lastTime, texts, times);
			lastTime = toMs(m.group(1), m.group(2), m.group(3));
			lastEnd = m.end();
		}
		addWords(text.substring(lastEnd), lastTime, texts, times);

		List<Lyrics.Word> words = new ArrayList<>();
		for (int i = 0; i < texts.size(); i++) {
			long start = times.get(i)[0];
			long end = i + 1 < texts.size() ? times.get(i + 1)[0] : lineEnd;
			words.add(new Lyrics.Word(texts.get(i), start, Math.max(end, start + 80)));
		}
		return words;
	}

	private static void addWords(String chunk, long time, List<String> texts, List<long[]> times) {
		for (String w : chunk.trim().split("\\s+")) {
			if (w.isEmpty()) continue;
			texts.add(w);
			times.add(new long[]{time});
		}
	}

	private static List<Lyrics.Word> estimateWords(String text, long start, long end) {
		String[] parts = text.split(" ");
		long available = end - start;
		double totalWeight = 0;
		double estimate = 0;
		for (String p : parts) {
			totalWeight += weight(p);
			estimate += 200 + 70.0 * letters(p);
		}

		double even = Math.min(available * 0.92, parts.length * 1100.0 + 800);
		double natural = Math.min(Math.max(estimate, available * 0.6), available * 0.92);
		float pacing = net.st3ix.lyrix.config.ModConfig.get().wordPacing;
		double singing = even + (natural - even) * pacing;
		List<Lyrics.Word> words = new ArrayList<>();
		double t = start;
		for (String p : parts) {
			double len = singing * weight(p) / totalWeight;
			words.add(new Lyrics.Word(p, Math.round(t), Math.round(t + len)));
			t += len;
		}
		return words;
	}

	private static double weight(String word) {
		return letters(word) + 2.0;
	}

	private static int letters(String word) {
		int n = 0;
		for (int i = 0; i < word.length(); i++) if (Character.isLetterOrDigit(word.charAt(i))) n++;
		return Math.max(1, n);
	}

	private static long toMs(String min, String sec, String frac) {
		long ms = Long.parseLong(min) * 60_000L + Long.parseLong(sec) * 1000L;
		if (frac != null) {
			String f = (frac + "00").substring(0, 3);
			ms += Long.parseLong(f);
		}
		return ms;
	}
}
