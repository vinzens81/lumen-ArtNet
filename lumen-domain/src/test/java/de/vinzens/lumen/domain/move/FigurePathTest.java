package de.vinzens.lumen.domain.move;

import de.vinzens.lumen.domain.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FigurePathTest {

	/** A square of side 0.5: four lines of the same length. */
	private static final List<FigurePoint> SQUARE = List.of(new FigurePoint(0.25, 0.25),
			new FigurePoint(0.75, 0.25), new FigurePoint(0.75, 0.75), new FigurePoint(0.25, 0.75));

	@Test
	void aClosedFigureGoesBackToItsFirstPoint() {
		var path = new FigurePath(SQUARE, true);
		assertThat(path.length()).isCloseTo(2.0, within(1e-9));
		assertThat(path.shareAt(2)).isCloseTo(0.5, within(1e-9));
		assertPoint(path.at(0.875), 0.25, 0.5);
		assertPoint(path.at(1.25), 0.75, 0.25); // wraps into the next round
	}

	@Test
	void anOpenFigureStaysAtItsEnds() {
		var path = new FigurePath(SQUARE, false);
		assertThat(path.length()).isCloseTo(1.5, within(1e-9));
		assertPoint(path.at(1), 0.25, 0.75);
		assertPoint(path.at(1.5), 0.25, 0.75);
		assertPoint(path.at(-1), 0.25, 0.25);
	}

	@Test
	void speedIsEvenAlongTheWayNotPerPoint() {
		// a long line and a short one: halfway is still on the long line
		var path = new FigurePath(List.of(new FigurePoint(0, 0), new FigurePoint(0.9, 0), new FigurePoint(1, 0)),
				false);
		assertPoint(path.at(0.5), 0.5, 0);
		assertThat(path.shareAt(1)).isCloseTo(0.9, within(1e-9));
	}

	@Test
	void aSinglePointIsAFixedPosition() {
		var path = new FigurePath(List.of(FigurePoint.ofDmx(128, 100)), true);
		assertThat(path.length()).isZero();
		assertThat(path.closed()).isFalse();
		assertPoint(path.at(0.7), 128 / 255.0, 100 / 255.0);
		assertThat(path.shareAt(0)).isZero();
	}

	@Test
	void theCenterIsTheMiddleOfTheBoundingBox() {
		var path = new FigurePath(List.of(new FigurePoint(0.1, 0.2), new FigurePoint(0.5, 0.9),
				new FigurePoint(0.3, 0.4)), true);
		assertPoint(path.center(), 0.3, 0.55);
	}

	@Test
	void figuresNeedAPointWithinTheGrid() {
		assertThatThrownBy(() -> new FigureSpec("Empty", true, List.of()))
				.isInstanceOf(DomainValidationException.class).hasMessageContaining("at least one point");
		assertThatThrownBy(() -> FigurePoint.ofDmx(256, 0)).isInstanceOf(DomainValidationException.class);
	}

	static void assertPoint(FigurePoint point, double x, double y) {
		assertThat(point.x()).as("x").isCloseTo(x, within(1e-9));
		assertThat(point.y()).as("y").isCloseTo(y, within(1e-9));
	}
}
