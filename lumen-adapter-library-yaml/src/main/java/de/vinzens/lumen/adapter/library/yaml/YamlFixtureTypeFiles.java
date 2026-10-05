package de.vinzens.lumen.adapter.library.yaml;

import de.vinzens.lumen.application.library.port.out.FixtureTypeFiles;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.FixtureType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/**
 * Converts OFL fixtures and writes profiles as YAML files into the library directory.
 */
public class YamlFixtureTypeFiles implements FixtureTypeFiles {

	private final Path directory;

	public YamlFixtureTypeFiles(Path directory) {
		this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
	}

	@Override
	public Converted convertOfl(String json, String manufacturer) {
		var converted = OflConverter.convert(json, manufacturer);
		return new Converted(converted.fixtureType(), converted.warnings());
	}

	@Override
	public void save(FixtureType type, String source, String comment) {
		var file = resolve(source);
		try {
			Files.createDirectories(file.getParent());
			var temp = Files.createTempFile(file.getParent(), ".import-", ".tmp");
			Files.writeString(temp, FixtureTypeYamlWriter.write(type, comment), StandardCharsets.UTF_8);
			// the library watcher must never see a half-written file
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			throw new UncheckedIOException("Writing " + file + " failed", e);
		}
	}

	@Override
	public boolean exists(String source) {
		return Files.exists(resolve(source));
	}

	/** The file of a source, which must stay inside the library. */
	private Path resolve(String source) {
		var file = directory.resolve(source).normalize();
		if (!file.startsWith(directory) || !file.getFileName().toString().endsWith(".yml")) {
			throw new DomainValidationException(List.of("'" + source + "' is not a profile file in the library"));
		}
		return file;
	}
}
