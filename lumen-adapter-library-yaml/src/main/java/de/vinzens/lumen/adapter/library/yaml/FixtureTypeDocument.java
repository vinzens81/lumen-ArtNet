package de.vinzens.lumen.adapter.library.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Structure of a fixture type YAML file as it is read. Mapped to the domain afterwards.
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

	/** @param heads channels repeated per head of a multi-pixel fixture, after (or before) {@code channels} */
	record ModeDocument(String name, List<ChannelDocument> channels, HeadsDocument heads) {
	}

	/**
	 * @param count    number of heads (segments, pixels)
	 * @param first    the heads come before the channels of the whole fixture
	 * @param channels channels of one head, in order
	 */
	record HeadsDocument(Integer count, Boolean first, List<ChannelDocument> channels) {
	}

	/** @param head head of a multi-pixel fixture this channel belongs to (instead of a {@code heads} block) */
	record ChannelDocument(
			String function,
			String name,
			Integer resolution,
			@JsonProperty("default") Integer defaultValue,
			Boolean invert,
			List<RangeDocument> ranges,
			String fade,
			Integer head) {
	}

	record RangeDocument(Integer from, Integer to, String label, String color) {
	}
}
