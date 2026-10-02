package de.vinzens.lumen.adapter.library.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Struktur einer Gerätetyp-YAML-Datei, wie sie gelesen wird. Wird anschließend auf die Domain abgebildet.
 */
record FixtureTypeDocument(
		String id,
		String manufacturer,
		String model,
		String category,
		PhysicalDocument physical,
		List<ModeDocument> modes) {

	record PhysicalDocument(
			@JsonProperty("pan-range") Integer panRange,
			@JsonProperty("tilt-range") Integer tiltRange) {
	}

	record ModeDocument(String name, List<ChannelDocument> channels) {
	}

	record ChannelDocument(
			String function,
			String name,
			Integer resolution,
			@JsonProperty("default") Integer defaultValue,
			Boolean invert,
			List<RangeDocument> ranges,
			String fade) {
	}

	record RangeDocument(Integer from, Integer to, String label, String color) {
	}
}
