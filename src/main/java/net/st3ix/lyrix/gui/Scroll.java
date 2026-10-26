package net.st3ix.lyrix.gui;

final class Scroll {
	private double pos;
	private double target;
	private double max;

	int pos() {
		return (int) Math.round(pos);
	}

	double rawPos() {
		return pos;
	}

	void wheel(double delta) {
		target = Math.clamp(target - delta, 0, max);
	}

	void scrollTo(double value) {
		target = Math.clamp(value, 0, max);
	}

	void reset() {
		pos = target = 0;
	}

	void update(int contentHeight, int viewHeight) {
		max = Math.max(0, contentHeight - viewHeight);
		target = Math.clamp(target, 0, max);
		pos += (target - pos) * 0.3;
		if (Math.abs(target - pos) < 0.5) pos = target;
		pos = Math.clamp(pos, 0, max);
	}
}
