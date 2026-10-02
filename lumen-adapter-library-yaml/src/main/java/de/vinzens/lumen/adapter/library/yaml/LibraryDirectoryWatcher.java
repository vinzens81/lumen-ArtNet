package de.vinzens.lumen.adapter.library.yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Notices added, changed and removed YAML files in the fixture library directory (including subdirectories).
 *
 * <p>Compares file times and sizes at a fixed interval instead of using {@code WatchService}: on macOS the JDK
 * implements {@code WatchService} by polling every few seconds anyway, and polling behaves the same everywhere.
 * A change is reported once the files have stopped changing for one interval, so a half-saved file is not loaded.
 */
public class LibraryDirectoryWatcher implements AutoCloseable {

	private static final System.Logger LOG = System.getLogger(LibraryDirectoryWatcher.class.getName());

	private final Path directory;
	private final Duration interval;
	private final Runnable onChange;
	private ScheduledExecutorService executor;

	private Map<String, String> reported;
	private Map<String, String> pending;

	public LibraryDirectoryWatcher(Path directory, Duration interval, Runnable onChange) {
		this.directory = Objects.requireNonNull(directory, "directory");
		this.interval = Objects.requireNonNull(interval, "interval");
		this.onChange = Objects.requireNonNull(onChange, "onChange");
	}

	public synchronized void start() {
		if (executor != null) {
			return;
		}
		reported = snapshot();
		executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			var thread = new Thread(runnable, "lumen-library-watcher");
			thread.setDaemon(true);
			return thread;
		});
		executor.scheduleWithFixedDelay(this::check, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
	}

	/** Compares the directory with the last state; called every interval. */
	synchronized void check() {
		try {
			var current = snapshot();
			if (current.equals(reported)) {
				pending = null;
			} else if (current.equals(pending)) {
				reported = current;
				pending = null;
				onChange.run();
			} else {
				pending = current; // still changing: wait one more interval
			}
		} catch (RuntimeException e) {
			LOG.log(Level.WARNING, "Checking the fixture library for changes failed", e);
		}
	}

	/** Relative path → modification time and size of every YAML file. */
	private Map<String, String> snapshot() {
		var result = new TreeMap<String, String>();
		if (!Files.isDirectory(directory)) {
			return result;
		}
		try (Stream<Path> files = Files.walk(directory)) {
			files.filter(Files::isRegularFile)
					.filter(file -> {
						var name = file.getFileName().toString();
						return name.endsWith(".yml") || name.endsWith(".yaml");
					})
					.forEach(file -> {
						try {
							result.put(directory.relativize(file).toString(),
									Files.getLastModifiedTime(file).toMillis() + ":" + Files.size(file));
						} catch (IOException e) {
							// deleted while walking: treated as missing
						}
					});
		} catch (IOException | UncheckedIOException e) {
			LOG.log(Level.DEBUG, "Cannot read fixture library directory " + directory, e);
		}
		return result;
	}

	@Override
	public synchronized void close() {
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}
}
