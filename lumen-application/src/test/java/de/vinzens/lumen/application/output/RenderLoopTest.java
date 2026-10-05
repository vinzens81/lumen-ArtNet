package de.vinzens.lumen.application.output;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class RenderLoopTest {

	/** The tests make ticks fail and hang on purpose; the loop's error log would look like a broken build. */
	private final Logger log = Logger.getLogger(RenderLoop.class.getName());
	private Level previousLevel;

	@BeforeEach
	void silenceExpectedErrors() {
		previousLevel = log.getLevel();
		log.setLevel(Level.OFF);
	}

	@AfterEach
	void restoreLogging() {
		log.setLevel(previousLevel);
	}

	@Test
	void keepsRunningWhenATickThrowsEvenAnError() throws InterruptedException {
		var calls = new AtomicInteger();
		var later = new CountDownLatch(5);
		try (var loop = new RenderLoop(() -> {
			later.countDown();
			if (calls.incrementAndGet() <= 2) {
				throw new StackOverflowError("deep");
			}
		}, 44)) {
			loop.start();

			assertThat(later.await(2, TimeUnit.SECONDS)).isTrue();
			var stats = loop.stats();
			assertThat(stats.errors()).isEqualTo(2);
			assertThat(stats.lastError()).contains("StackOverflowError: deep");
			assertThat(stats.ticks()).isGreaterThanOrEqualTo(5);
		}
	}

	@Test
	void restartsAHangingLoop() throws InterruptedException {
		var hang = new CountDownLatch(1);
		var afterRestart = new CountDownLatch(3);
		var first = new AtomicInteger();
		try (var loop = new RenderLoop(() -> {
			if (first.getAndIncrement() == 0) {
				try {
					new CountDownLatch(1).await(); // hangs until interrupted
				} catch (InterruptedException e) {
					hang.countDown();
				}
			} else {
				afterRestart.countDown();
			}
		}, 44)) {
			loop.start();
			Thread.sleep(120);
			assertThat(loop.restartIfStalled(Duration.ofSeconds(5))).as("not stalled long enough").isFalse();

			assertThat(loop.restartIfStalled(Duration.ofMillis(100))).isTrue();

			assertThat(hang.await(1, TimeUnit.SECONDS)).as("hanging tick interrupted").isTrue();
			assertThat(afterRestart.await(1, TimeUnit.SECONDS)).isTrue();
			assertThat(loop.stats().restarts()).isEqualTo(1);
			assertThat(loop.stats().running()).isTrue();
		}
	}
}
