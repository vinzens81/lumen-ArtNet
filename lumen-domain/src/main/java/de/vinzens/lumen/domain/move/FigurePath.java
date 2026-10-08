package de.vinzens.lumen.domain.move;

import java.util.ArrayList;
import java.util.List;

/**
 * The way through the points of a figure, joined by straight lines; a closed figure goes back to its first point.
 * Positions on the way are given by the distance travelled, so a fixture following it moves at an even speed: long
 * lines take longer than short ones.
 */
public final class FigurePath {

	private final List<FigurePoint> points;
	private final boolean closed;
	/** the points in the order they are passed, a closed figure ending with its first point again */
	private final List<FigurePoint> corners;
	/** distance from the start to each point, and at the end the whole length */
	private final double[] distances;
	private final double length;
	private final FigurePoint center;

	public FigurePath(List<FigurePoint> points, boolean closed) {
		if (points.isEmpty()) {
			throw new IllegalArgumentException("a path needs at least one point");
		}
		this.points = List.copyOf(points);
		this.closed = closed && points.size() > 1;
		corners = corners(this.points, this.closed);
		distances = new double[corners.size()];
		var total = 0.0;
		for (int i = 1; i < corners.size(); i++) {
			total += distance(corners.get(i - 1), corners.get(i));
			distances[i] = total;
		}
		length = total;
		center = boundingBoxCenter(this.points);
	}

	/** Number of points of the figure. */
	public int pointCount() {
		return points.size();
	}

	public boolean closed() {
		return closed;
	}

	/** Length of the whole way (a closed figure including the line back to the first point). */
	public double length() {
		return length;
	}

	/** The middle of the bounding box of the points: the figure is scaled, rotated and mirrored around it. */
	public FigurePoint center() {
		return center;
	}

	/** Where point {@code index} (0-based) lies on the way, as a share of the whole length (0 ≤ result < 1). */
	public double shareAt(int index) {
		if (length <= 0) {
			return 0;
		}
		return distances[Math.floorMod(index, points.size())] / length;
	}

	/**
	 * The position at a share of the way. A closed figure wraps around (1.25 = a quarter into the second round), an
	 * open one stays at its ends.
	 */
	public FigurePoint at(double share) {
		if (length <= 0) {
			return points.getFirst();
		}
		var s = closed ? share - Math.floor(share) : Math.min(1, Math.max(0, share));
		var target = s * length;
		for (int i = 1; i < corners.size(); i++) {
			if (target <= distances[i] || i == corners.size() - 1) {
				var segment = distances[i] - distances[i - 1];
				var t = segment <= 0 ? 1 : Math.min(1, Math.max(0, (target - distances[i - 1]) / segment));
				var a = corners.get(i - 1);
				var b = corners.get(i);
				return new FigurePoint(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
			}
		}
		return corners.getLast();
	}

	private static List<FigurePoint> corners(List<FigurePoint> points, boolean closed) {
		var result = new ArrayList<>(points);
		if (closed) {
			result.add(points.getFirst());
		}
		return List.copyOf(result);
	}

	private static double distance(FigurePoint a, FigurePoint b) {
		return Math.hypot(b.x() - a.x(), b.y() - a.y());
	}

	private static FigurePoint boundingBoxCenter(List<FigurePoint> points) {
		var minX = points.stream().mapToDouble(FigurePoint::x).min().orElseThrow();
		var maxX = points.stream().mapToDouble(FigurePoint::x).max().orElseThrow();
		var minY = points.stream().mapToDouble(FigurePoint::y).min().orElseThrow();
		var maxY = points.stream().mapToDouble(FigurePoint::y).max().orElseThrow();
		return new FigurePoint((minX + maxX) / 2, (minY + maxY) / 2);
	}
}
