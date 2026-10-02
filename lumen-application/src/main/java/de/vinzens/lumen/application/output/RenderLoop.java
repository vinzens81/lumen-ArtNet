package de.vinzens.lumen.application.output;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Calls {@link OutputEngine#tick()} at a fixed rate on a dedicated thread. Any failure in a tick is logged
 * and does not stop the loop – the light must keep running. If a tick hangs, {@link #restartIfStalled} replaces
 * the thread.
 */
public class RenderLoop implements AutoCloseable {

	private static final System.Logger LOG = System.getLogger(RenderLoop.class.getName());
	private static final Duration ERROR_LOG_INTERVAL = Duration.ofSeconds(10);

	private final Runnable tick;
	private final int rateHz;
	private final long periodNanos;
	private ScheduledExecutorService executor;
	private long lastErrorLog;

	private final AtomicLong ticks = new AtomicLong();
	private final AtomicLong errors = new AtomicLong();
	private volatile long lastTickNanos;
	private volatile String lastError;
	private volatile int restarts;

	public RenderLoop(OutputEngine engine, int rateHz) {
		this(Objects.requireNonNull(engine, "engine")::tick, rateHz);
	}

	RenderLoop(Runnable tick, int rateHz) {
		this.tick = Objects.requireNonNull(tick, "tick");
		if (rateHz < 1 || rateHz > 44) {
			throw new IllegalArgumentException("rateHz must be between 1 and 44, was " + rateHz);
		}
		this.rateHz = rateHz;
		this.periodNanos = TimeUnit.SECONDS.toNanos(1) / rateHz;
	}

	public synchronized void start() {
		if (executor != null) {
			return;
		}
		lastTickNanos = System.nanoTime();
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
			tick.run();
		} catch (Throwable e) { // an Error would cancel the schedule for good
			errors.incrementAndGet();
			lastError = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
			var now = System.nanoTime();
			if (now - lastErrorLog > ERROR_LOG_INTERVAL.toNanos()) {
				lastErrorLog = now;
				LOG.log(Level.ERROR, "Render tick failed, keeping the loop alive", e);
			}
		} finally {
			ticks.incrementAndGet();
			lastTickNanos = System.nanoTime();
		}
	}

	/**
	 * Replaces the render thread if no tick has completed for the given time, e.g. because a tick hangs.
	 *
	 * @return whether the loop was restarted
	 */
	public synchronized boolean restartIfStalled(Duration maxAge) {
		if (executor == null || System.nanoTime() - lastTickNanos < maxAge.toNanos()) {
			return false;
		}
		LOG.log(Level.ERROR, "Render loop stalled for more than {0} ms, restarting it", maxAge.toMillis());
		executor.shutdownNow();
		executor = null;
		restarts++;
		start();
		return true;
	}

	public Stats stats() {
		boolean running;
		synchronized (this) {
			running = executor != null;
		}
		return new Stats(running, rateHz, ticks.get(), errors.get(), Optional.ofNullable(lastError),
				Duration.ofNanos(System.nanoTime() - lastTickNanos), restarts);
	}

	/**
	 * @param ticks        completed ticks since start
	 * @param errors       failed ticks since start
	 * @param lastTickAge  time since the last tick completed
	 * @param restarts     how often a stalled loop was restarted
	 */
	public record Stats(boolean running, int rateHz, long ticks, long errors, Optional<String> lastError,
			Duration lastTickAge, int restarts) {
	}

	@Override
	public synchronized void close() {
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}
}
