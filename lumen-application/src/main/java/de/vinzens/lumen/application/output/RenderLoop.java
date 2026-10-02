package de.vinzens.lumen.application.output;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Calls {@link OutputEngine#tick()} at a fixed rate on a dedicated thread. An exception in a tick is logged
 * and does not stop the loop – the light must keep running.
 */
public class RenderLoop implements AutoCloseable {

	private static final System.Logger LOG = System.getLogger(RenderLoop.class.getName());
	private static final Duration ERROR_LOG_INTERVAL = Duration.ofSeconds(10);

	private final OutputEngine engine;
	private final long periodNanos;
	private ScheduledExecutorService executor;
	private long lastErrorLog;

	public RenderLoop(OutputEngine engine, int rateHz) {
		this.engine = Objects.requireNonNull(engine, "engine");
		if (rateHz < 1 || rateHz > 44) {
			throw new IllegalArgumentException("rateHz must be between 1 and 44, was " + rateHz);
		}
		this.periodNanos = TimeUnit.SECONDS.toNanos(1) / rateHz;
	}

	public synchronized void start() {
		if (executor != null) {
			return;
		}
		executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			var thread = new Thread(runnable, "lumen-render-loop");
			thread.setDaemon(true);
			thread.setPriority(Thread.MAX_PRIORITY);
			return thread;
		});
		executor.scheduleAtFixedRate(this::safeTick, 0, periodNanos, TimeUnit.NANOSECONDS);
	}

	private void safeTick() {
		try {
			engine.tick();
		} catch (RuntimeException e) {
			var now = System.nanoTime();
			if (now - lastErrorLog > ERROR_LOG_INTERVAL.toNanos()) {
				lastErrorLog = now;
				LOG.log(Level.ERROR, "Render tick failed, keeping the loop alive", e);
			}
		}
	}

	@Override
	public synchronized void close() {
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}
}
