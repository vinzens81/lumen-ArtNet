package de.vinzens.lumen.adapter.library.yaml;

import de.vinzens.lumen.application.library.port.out.FixtureTypeSource;
import de.vinzens.lumen.application.library.port.out.LoadResult;
import de.vinzens.lumen.application.library.port.out.SourcedFixtureType;
import de.vinzens.lumen.domain.library.LibraryIssue;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Loads fixture types from all {@code *.yml}/{@code *.yaml} files of a directory (including subdirectories).
 */
public class YamlFixtureTypeSource implements FixtureTypeSource {

	private final Path directory;
	private final YAMLMapper mapper = YAMLMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	public YamlFixtureTypeSource(Path directory) {
		this.directory = Objects.requireNonNull(directory, "directory");
	}

	@Override
	public LoadResult loadAll() {
		if (!Files.isDirectory(directory)) {
			return new LoadResult(List.of(), List.of(LibraryIssue.warning(directory.toString(), null,
					"Fixture library directory does not exist")));
		}

		var types = new ArrayList<SourcedFixtureType>();
		var issues = new ArrayList<LibraryIssue>();
		for (var file : yamlFiles(issues)) {
			var source = directory.relativize(file).toString();
			try {
				var document = mapper.readValue(file.toFile(), FixtureTypeDocument.class);
				if (document == null) {
					issues.add(LibraryIssue.error(source, null, "File is empty"));
					continue;
				}
				var result = FixtureTypeDocumentMapper.map(document, source);
				issues.addAll(result.issues());
				result.fixtureType().ifPresent(type -> types.add(new SourcedFixtureType(type, source)));
			} catch (JacksonException e) {
				issues.add(LibraryIssue.error(source, null, describe(e)));
			}
		}
		return new LoadResult(types, issues);
	}

	private List<Path> yamlFiles(List<LibraryIssue> issues) {
		try (Stream<Path> files = Files.walk(directory)) {
			return files
					.filter(Files::isRegularFile)
					.filter(file -> {
						var name = file.getFileName().toString();
						return name.endsWith(".yml") || name.endsWith(".yaml");
					})
					.sorted()
					.toList();
		} catch (IOException e) {
			issues.add(LibraryIssue.error(directory.toString(), null, "Directory is not readable: " + e.getMessage()));
			return List.of();
		}
	}

	private static String describe(JacksonException e) {
		var line = e.getLocation() != null && e.getLocation().getLineNr() > 0
				? " (line " + e.getLocation().getLineNr() + ")"
				: "";
		if (e instanceof UnrecognizedPropertyException unknown) {
			return "Unknown field '" + unknown.getPropertyName() + "'" + line;
		}
		return "Invalid YAML" + line + ": " + e.getOriginalMessage();
	}
}
