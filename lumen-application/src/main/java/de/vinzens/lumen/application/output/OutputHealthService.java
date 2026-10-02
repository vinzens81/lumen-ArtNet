package de.vinzens.lumen.application.output;

import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.QueryHealthUseCase;
import de.vinzens.lumen.application.output.port.out.DmxOutputPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * Watches the light output once per second: restarts a stalled render loop (watchdog), measures render and
 * packet rates and publishes the result to all clients.
 */
public class OutputHealthService implements QueryHealthUseCase, AutoCloseable {

	/** A render loop without a completed tick for this long is restarted. */
	static final Duration STALL_LIMIT = Duration.ofSeconds(2);
	private static final Duration INTERVAL = Duration.ofSeconds(1);

	private final RenderLoop loop;
	private final DmxOutputPort output;
	private final IntSupplier universes;
	private final StateEvents events;
	private final LongSupplier nanoTime;
	private ScheduledExecutorService sampler;

	private Sample previous;
	private volatile OutputHealth health = OutputHealth.UNKNOWN;

	/**
	 * @param universes number of universes of the active project
	 */
	public OutputHealthService(RenderLoop loop, DmxOutputPort output, IntSupplier universes, StateEvents events) {
		this(loop, output, universes, events, System::nanoTime);
	}

	OutputHealthService(RenderLoop loop, DmxOutputPort output, IntSupplier universes, StateEvents events,
			LongSupplier nanoTime) {
		this.loop = Objects.requireNonNull(loop, "loop");
		this.output = Objects.requireNonNull(output, "output");
		this.universes = Objects.requireNonNull(universes, "universes");
		this.events = Objects.requireNonNull(events, "events");
		this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
	}

	public synchronized void start() {
		if (sampler != null) {
			return;
		}
		sampler = Executors.newSingleThreadScheduledExecutor(runnable -> {
			var thread = new Thread(runnable, "lumen-output-watchdog");
			thread.setDaemon(true);
			return thread;
		});
		sampler.scheduleWithFixedDelay(this::sample, INTERVAL.toMillis(), INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
	}

	@Override
	public OutputHealth health() {
		return health;
	}

	/** Takes a measurement; called every second. */
	synchronized void sample() {
		loop.restartIfStalled(STALL_LIMIT);
		var current = new Sample(nanoTime.getAsLong(), loop.stats(), output.counters());
		if (previous != null && current.nanos() > previous.nanos()) {
			health = evaluate(previous, current, universes.getAsInt());
			events.publish(StateTopic.HEALTH);
		}
		previous = current;
	}

	private static OutputHealth evaluate(Sample from, Sample to, int universes) {
		var seconds = (to.nanos() - from.nanos()) / 1e9;
		var renderRate = (to.loop().ticks() - from.loop().ticks()) / seconds;
		var newErrors = to.loop().errors() - from.loop().errors();
		var packetRate = (to.counters().packetsSent() - from.counters().packetsSent()) / seconds;
		var sendErrorRate = (to.counters().sendErrors() - from.counters().sendErrors()) / seconds;

		var level = Level.OK;
		var problems = new ArrayList<String>();
		if (!to.loop().running() || to.loop().lastTickAge().compareTo(STALL_LIMIT) >= 0) {
			level = Level.ERROR;
			problems.add("The render loop is not running – the light does not change");
		} else if (renderRate < to.loop().rateHz() * 0.75) {
			level = Level.WARNING;
			problems.add("The render loop is slow (%.0f of %d frames per second)".formatted(renderRate,
					to.loop().rateHz()));
		}
		if (to.loop().restarts() > from.loop().restarts()) {
			level = Level.ERROR;
			problems.add("The render loop hung and was restarted");
		}
		if (newErrors > 0) {
			level = max(level, Level.WARNING);
			problems.add("Rendering failed " + newErrors + " times: " + to.loop().lastError().orElse("unknown error"));
		}
		if (to.counters().active()) {
			if (sendErrorRate > 0) {
				level = max(level, Level.WARNING);
				problems.add("Art-Net packets cannot be sent (network unavailable?)");
			} else if (universes > 0 && packetRate == 0) {
				level = max(level, Level.WARNING);
				problems.add("No Art-Net packets are sent – no node found for any universe");
			}
		}
		return new OutputHealth(level, problems, round(renderRate), to.loop().rateHz(), to.loop().errors(),
				to.loop().lastError(), to.loop().restarts(), to.counters().active(), round(packetRate),
				round(sendErrorRate), universes);
	}

	private static Level max(Level a, Level b) {
		return a.compareTo(b) >= 0 ? a : b;
	}

	private static double round(double value) {
		return Math.round(value * 10) / 10.0;
	}

	@Override
	public synchronized void close() {
		if (sampler != null) {
			sampler.shutdownNow();
			sampler = null;
		}
	}

	private record Sample(long nanos, RenderLoop.Stats loop, DmxOutputPort.Counters counters) {
	}
}
