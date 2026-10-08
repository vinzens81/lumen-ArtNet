package de.vinzens.lumen.domain.move;

import java.util.Objects;

/**
 * A figure of the project, e.g. "Eight": points in order that a move lets its fixtures follow. Several moves can use
 * the same figure; changing it changes them all.
 */
public record Figure(long id, long projectId, FigureSpec spec) {

	public Figure {
		Objects.requireNonNull(spec, "spec");
	}

	public FigurePath path() {
		return new FigurePath(spec.points(), spec.closed());
	}
}
