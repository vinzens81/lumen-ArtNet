package de.vinzens.lumen.adapter.library.yaml;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.ChannelRange;
import de.vinzens.lumen.domain.library.FixtureCategory;
import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.library.PhysicalProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts a fixture of the <a href="https://open-fixture-library.org">Open Fixture Library</a> (the "OFL JSON"
 * download of a fixture) into a Lumen fixture type. What Lumen cannot express is simplified and reported as a
 * warning: a channel with several kinds of capabilities gets the function of its main one, fine channels that do
 * not follow their coarse channel stay separate generic channels.
 */
final class OflConverter {

	/** The converted fixture type and what got lost or simplified on the way. */
	record Converted(FixtureType fixtureType, List<String> warnings) {
	}

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String PIXEL_KEY = "$pixelKey";

	private static final Map<String, ChannelFunction> COLORS = Map.ofEntries(
			Map.entry("Red", ChannelFunction.RED), Map.entry("Green", ChannelFunction.GREEN),
			Map.entry("Blue", ChannelFunction.BLUE), Map.entry("White", ChannelFunction.WHITE),
			Map.entry("Warm White", ChannelFunction.WARM_WHITE), Map.entry("Cold White", ChannelFunction.COLD_WHITE),
			Map.entry("Amber", ChannelFunction.AMBER), Map.entry("UV", ChannelFunction.UV),
			Map.entry("Lime", ChannelFunction.LIME), Map.entry("Cyan", ChannelFunction.CYAN),
			Map.entry("Magenta", ChannelFunction.MAGENTA), Map.entry("Yellow", ChannelFunction.YELLOW));

	private static final List<Map.Entry<String, FixtureCategory>> CATEGORIES = List.of(
			Map.entry("Moving Head", FixtureCategory.MOVING_HEAD), Map.entry("Scanner", FixtureCategory.SCANNER),
			Map.entry("Barrel Scanner", FixtureCategory.SCANNER), Map.entry("Pixel Bar", FixtureCategory.BAR),
			Map.entry("Dimmer", FixtureCategory.DIMMER), Map.entry("Strobe", FixtureCategory.STROBE),
			Map.entry("Blinder", FixtureCategory.STROBE), Map.entry("Smoke", FixtureCategory.FOG),
			Map.entry("Hazer", FixtureCategory.FOG), Map.entry("Color Changer", FixtureCategory.PAR));

	private final JsonNode root;
	private final Set<String> warnings = new LinkedHashSet<>();
	private final List<String> pixelKeys;
	/** Key of the channel being converted: a capability without "wheel" belongs to the wheel of that name. */
	private String channelKey;

	private OflConverter(JsonNode root) {
		this.root = root;
		this.pixelKeys = pixelKeys(root.path("matrix"));
	}

	/**
	 * @param json         the fixture file as downloaded from the Open Fixture Library
	 * @param manufacturer manufacturer name; the fixture file itself does not contain it
	 */
	static Converted convert(String json, String manufacturer) {
		JsonNode root;
		try {
			root = JSON.readTree(json);
		} catch (JacksonException e) {
			throw new DomainValidationException(List.of("This is not a JSON file: " + e.getOriginalMessage()));
		}
		if (root == null || !root.isObject() || !root.path("modes").isArray() || !root.path("name").isString()) {
			throw new DomainValidationException(List.of(
					"This is not a fixture of the Open Fixture Library (name and modes are missing)"));
		}
		if (manufacturer == null || manufacturer.isBlank()) {
			throw new DomainValidationException(List.of("manufacturer is missing"));
		}
		return new OflConverter(root).convert(manufacturer.strip());
	}

	private Converted convert(String manufacturer) {
		var model = root.path("name").asString();
		var modes = new ArrayList<Mode>();
		var modeNames = new HashSet<String>();
		for (var mode : root.path("modes")) {
			var name = mode.path("shortName").isString() ? mode.path("shortName").asString()
					: mode.path("name").asString();
			if (!modeNames.add(name)) {
				name = mode.path("name").asString();
			}
			modes.add(new Mode(name, channels(mode, name)));
		}
		var type = new FixtureType(slug(manufacturer + " " + model), manufacturer, model, category(), physical(),
				modes);
		return new Converted(type, List.copyOf(warnings));
	}

	// --- modes -------------------------------------------------------------------------------------------------

	/** A position in a mode: a channel of the fixture, a channel of a head, or an unused DMX channel. */
	private record Slot(String key, JsonNode definition, int head) {
	}

	private List<ChannelDefinition> channels(JsonNode mode, String modeName) {
		var slots = new ArrayList<Slot>();
		for (var entry : mode.path("channels")) {
			if (entry.isNull()) {
				slots.add(new Slot(null, null, 0));
			} else if (entry.isString()) {
				slots.add(slot(entry.asString()));
			} else if (entry.isObject() && "matrixChannels".equals(entry.path("insert").asString())) {
				slots.addAll(matrixSlots(entry, modeName));
			} else {
				warnings.add("Mode '" + modeName + "': an entry of its channel list is not understood and left out");
			}
		}

		var result = new ArrayList<ChannelDefinition>();
		var used = new HashSet<String>();
		for (int i = 0; i < slots.size(); i++) {
			var slot = slots.get(i);
			if (slot.definition() == null && slot.key() == null) {
				result.add(generic("Unused", slot.head(), used));
				continue;
			}
			var fineOf = fineOf(slot.key());
			if (fineOf.isPresent()) {
				var previous = i > 0 ? slots.get(i - 1) : null;
				if (previous != null && fineOf.get().equals(previous.key()) && previous.head() == slot.head()
						&& !result.isEmpty() && result.getLast().resolution() == ChannelDefinition.RESOLUTION_8_BIT) {
					result.set(result.size() - 1, sixteenBit(result.getLast()));
				} else {
					warnings.add("Mode '" + modeName + "': fine channel '" + slot.key()
							+ "' does not follow its coarse channel – it stays a channel of its own");
					result.add(generic(displayKey(slot.key()), slot.head(), used));
				}
				continue;
			}
			if (slot.definition() == null) {
				warnings.add("Mode '" + modeName + "': unknown channel '" + slot.key() + "' – kept as generic channel");
				result.add(generic(displayKey(slot.key()), slot.head(), used));
				continue;
			}
			result.add(channel(slot, used));
		}
		return result;
	}

	private Slot slot(String key) {
		var definition = root.path("availableChannels").get(key);
		if (definition != null) {
			return new Slot(key, definition, 0);
		}
		// a template channel listed with its pixel key, e.g. "Red 3"
		var templates = root.path("templateChannels");
		for (var name : templates.propertyNames()) {
			var pattern = Pattern.compile(Pattern.quote(name).replace(PIXEL_KEY, "\\E(.+)\\Q"));
			var match = pattern.matcher(key);
			if (match.matches() && pixelKeys.contains(match.group(1))) {
				return new Slot(name, templates.get(name), pixelKeys.indexOf(match.group(1)) + 1);
			}
		}
		return new Slot(key, null, 0);
	}

	private List<Slot> matrixSlots(JsonNode insert, String modeName) {
		var pixels = new ArrayList<String>();
		var repeatFor = insert.path("repeatFor");
		if (repeatFor.isArray()) {
			repeatFor.forEach(key -> pixels.add(key.asString()));
		} else {
			pixels.addAll(pixelKeys);
			if (repeatFor.asString().startsWith("eachPixelGroup")) {
				warnings.add("Mode '" + modeName + "': pixel groups are imported as single pixels");
			}
		}
		var templates = new ArrayList<String>();
		insert.path("templateChannels").forEach(name -> templates.add(name.asString()));
		var result = new ArrayList<Slot>();
		var perChannel = "perChannel".equals(insert.path("channelOrder").asString());
		var outer = perChannel ? templates.size() : pixels.size();
		var inner = perChannel ? pixels.size() : templates.size();
		for (int o = 0; o < outer; o++) {
			for (int i = 0; i < inner; i++) {
				var template = templates.get(perChannel ? o : i);
				var pixel = perChannel ? i : o;
				var head = pixelKeys.indexOf(pixels.get(pixel)) + 1;
				result.add(new Slot(template, root.path("templateChannels").get(template),
						head > 0 ? head : pixel + 1));
			}
		}
		return result;
	}

	/** The coarse channel a fine channel belongs to, if the key is a fine channel alias. */
	private Optional<String> fineOf(String key) {
		if (key == null) {
			return Optional.empty();
		}
		for (var section : List.of("availableChannels", "templateChannels")) {
			var channels = root.path(section);
			for (var name : channels.propertyNames()) {
				for (var alias : channels.get(name).path("fineChannelAliases")) {
					if (alias.asString().equals(key)) {
						return Optional.of(name);
					}
				}
			}
		}
		return Optional.empty();
	}

	// --- channels ----------------------------------------------------------------------------------------------

	private ChannelDefinition channel(Slot slot, Set<String> used) {
		var definition = slot.definition();
		channelKey = displayKey(slot.key());
		var capabilities = capabilities(definition);
		var function = function(slot.key(), capabilities);
		var name = Optional.<String>empty();
		if (function == ChannelFunction.GENERIC || !used.add(function.id() + "@" + slot.head())) {
			// a function twice in one head would be the same attribute: the second one becomes generic
			function = ChannelFunction.GENERIC;
			name = Optional.of(uniqueName(displayKey(slot.key()), slot.head(), used));
		}
		var ranges = capabilities.size() > 1 ? ranges(capabilities) : List.<ChannelRange>of();
		var invert = capabilities.size() == 1 && "Intensity".equals(type(capabilities.getFirst()))
				&& "off".equalsIgnoreCase(capabilities.getFirst().path("brightnessEnd").asString(""));
		return new ChannelDefinition(function, name, ChannelDefinition.RESOLUTION_8_BIT, defaultValue(definition),
				invert, ranges, Optional.empty(), slot.head());
	}

	private ChannelDefinition generic(String name, int head, Set<String> used) {
		return new ChannelDefinition(ChannelFunction.GENERIC, Optional.of(uniqueName(name, head, used)),
				ChannelDefinition.RESOLUTION_8_BIT, 0, false, List.of(), Optional.empty(), head);
	}

	private static String uniqueName(String name, int head, Set<String> used) {
		var candidate = name;
		for (int i = 2; !used.add("generic:" + candidate + "@" + head); i++) {
			candidate = name + " " + i;
		}
		return candidate;
	}

	private static ChannelDefinition sixteenBit(ChannelDefinition coarse) {
		return new ChannelDefinition(coarse.function(), coarse.name(), ChannelDefinition.RESOLUTION_16_BIT,
				coarse.defaultValue() * 257, coarse.invert(), coarse.ranges(), coarse.fadeBehavior(), coarse.head());
	}

	private static List<JsonNode> capabilities(JsonNode definition) {
		var result = new ArrayList<JsonNode>();
		if (definition.has("capability")) {
			result.add(definition.get("capability"));
		}
		definition.path("capabilities").forEach(result::add);
		return result;
	}

	/** The function of the capability that covers most of the DMX range, ignoring "no function". */
	private ChannelFunction function(String key, List<JsonNode> capabilities) {
		JsonNode main = null;
		var widest = -1;
		for (var capability : capabilities) {
			if ("NoFunction".equals(type(capability))) {
				continue;
			}
			var range = capability.path("dmxRange");
			var width = range.isArray() ? range.get(1).asInt() - range.get(0).asInt() : 255;
			if (width > widest) {
				widest = width;
				main = capability;
			}
		}
		if (main == null) {
			return ChannelFunction.GENERIC;
		}
		var kinds = capabilities.stream().map(OflConverter::type).filter(t -> !t.equals("NoFunction")).distinct()
				.toList();
		var function = functionOf(main, capabilities);
		if (kinds.size() > 1 && function != ChannelFunction.GENERIC) {
			warnings.add("Channel '" + displayKey(key) + "' does several things (" + String.join(", ", kinds)
					+ ") – imported as " + function.id() + ", the rest shows as ranges");
		}
		return function;
	}

	private ChannelFunction functionOf(JsonNode capability, List<JsonNode> all) {
		return switch (type(capability)) {
			case "Intensity" -> ChannelFunction.DIMMER;
			case "ColorIntensity" -> COLORS.getOrDefault(capability.path("color").asString(""),
					ChannelFunction.GENERIC);
			case "ColorPreset" -> ChannelFunction.COLOR_WHEEL;
			case "ColorTemperature" -> ChannelFunction.CTO;
			case "WheelSlot", "WheelShake", "WheelRotation" -> wheelFunction(capability);
			case "WheelSlotRotation" -> ChannelFunction.GOBO_ROTATION;
			case "Pan", "PanContinuous" -> ChannelFunction.PAN;
			case "Tilt", "TiltContinuous" -> ChannelFunction.TILT;
			case "PanTiltSpeed", "Speed", "EffectSpeed", "EffectDuration" -> ChannelFunction.SPEED;
			case "ShutterStrobe" -> all.stream()
					.filter(c -> "ShutterStrobe".equals(type(c)))
					.allMatch(c -> Set.of("Open", "Closed").contains(c.path("shutterEffect").asString("")))
					? ChannelFunction.SHUTTER : ChannelFunction.STROBE;
			case "StrobeSpeed", "StrobeDuration" -> ChannelFunction.STROBE;
			case "Prism", "PrismRotation" -> ChannelFunction.PRISM;
			case "Focus" -> ChannelFunction.FOCUS;
			case "Zoom" -> ChannelFunction.ZOOM;
			case "Iris", "IrisEffect" -> ChannelFunction.IRIS;
			case "Fog", "FogOutput", "FogType" -> ChannelFunction.FOG;
			case "Effect" -> ChannelFunction.PROGRAM;
			case "Maintenance" -> all.stream().anyMatch(c -> c.path("comment").asString("")
					.toLowerCase(Locale.ROOT).contains("reset")) ? ChannelFunction.RESET : ChannelFunction.MACRO;
			default -> ChannelFunction.GENERIC;
		};
	}

	/** Color wheel, gobo wheel or prism, by the kind of slots of the wheel. */
	private ChannelFunction wheelFunction(JsonNode capability) {
		var slots = wheel(capability).path("slots");
		for (var slot : slots) {
			switch (slot.path("type").asString("")) {
				case "Color" -> {
					return ChannelFunction.COLOR_WHEEL;
				}
				case "Gobo", "AnimationGoboStart" -> {
					return ChannelFunction.GOBO;
				}
				case "Prism" -> {
					return ChannelFunction.PRISM;
				}
				default -> {
				}
			}
		}
		return ChannelFunction.GENERIC;
	}

	private JsonNode wheel(JsonNode capability) {
		var name = capability.path("wheel").asString(channelKey);
		var wheels = root.path("wheels");
		if (name != null && wheels.has(name)) {
			return wheels.get(name);
		}
		return wheels.size() == 1 ? wheels.iterator().next() : JSON.createObjectNode();
	}

	private List<ChannelRange> ranges(List<JsonNode> capabilities) {
		var result = new ArrayList<ChannelRange>();
		for (var capability : capabilities) {
			var range = capability.path("dmxRange");
			if (!range.isArray() || range.size() != 2) {
				continue;
			}
			var from = range.get(0).asInt();
			var to = range.get(1).asInt();
			if (from < 0 || to > 255 || from > to) {
				continue; // 16-bit ranges have no place in the 8-bit ranges of Lumen
			}
			result.add(new ChannelRange(from, to, label(capability), color(capability)));
		}
		return result;
	}

	private String label(JsonNode capability) {
		var comment = capability.path("comment").asString("");
		var type = type(capability);
		var base = switch (type) {
			case "WheelSlot" -> slotName(capability);
			case "ShutterStrobe" -> capability.path("shutterEffect").asString(type);
			case "ColorPreset", "NoFunction" -> comment.isEmpty() ? (type.equals("NoFunction") ? "No function"
					: "Color") : comment;
			default -> words(type);
		};
		return comment.isEmpty() || comment.equals(base) ? base : base + " – " + comment;
	}

	private String slotName(JsonNode capability) {
		var slots = wheel(capability).path("slots");
		var number = capability.path("slotNumber").asDouble(0);
		var index = (int) Math.floor(number) - 1;
		if (index < 0 || index >= slots.size()) {
			return "Slot " + number;
		}
		var slot = slots.get(index);
		var name = slot.path("name").asString(null);
		return name != null ? name : words(slot.path("type").asString("Slot"));
	}

	private Optional<String> color(JsonNode capability) {
		var colors = capability.path("colors");
		if (colors.isArray() && !colors.isEmpty()) {
			return Optional.of(colors.get(0).asString().toLowerCase(Locale.ROOT));
		}
		if ("WheelSlot".equals(type(capability))) {
			var slots = wheel(capability).path("slots");
			var index = (int) Math.floor(capability.path("slotNumber").asDouble(0)) - 1;
			if (index >= 0 && index < slots.size()) {
				var slot = slots.get(index);
				if (slot.path("colors").isArray() && !slot.path("colors").isEmpty()) {
					return Optional.of(slot.path("colors").get(0).asString().toLowerCase(Locale.ROOT));
				}
				if ("Open".equals(slot.path("type").asString(""))) {
					return Optional.of("#ffffff");
				}
			}
		}
		return Optional.empty();
	}

	private static int defaultValue(JsonNode definition) {
		var value = definition.path("defaultValue");
		if (value.isNumber()) {
			return Math.clamp(value.asInt(), 0, 255);
		}
		var text = value.asString("");
		if (text.endsWith("%")) {
			try {
				return (int) Math.round(Double.parseDouble(text.substring(0, text.length() - 1)) * 2.55);
			} catch (NumberFormatException e) {
				return 0;
			}
		}
		return 0;
	}

	// --- fixture -----------------------------------------------------------------------------------------------

	private FixtureCategory category() {
		var categories = new ArrayList<String>();
		root.path("categories").forEach(c -> categories.add(c.asString()));
		for (var category : categories) {
			for (var mapping : CATEGORIES) {
				if (mapping.getKey().equals(category)) {
					return mapping.getValue();
				}
			}
		}
		return FixtureCategory.OTHER;
	}

	private PhysicalProperties physical() {
		var focus = root.path("physical").path("focus");
		return new PhysicalProperties(degrees(focus.path("panMax")), degrees(focus.path("tiltMax")));
	}

	private static OptionalInt degrees(JsonNode value) {
		return value.isNumber() && value.asInt() > 0 ? OptionalInt.of(value.asInt()) : OptionalInt.empty();
	}

	/** Pixel keys of the matrix in order (z, y, x), or the numbers of {@code pixelCount}. */
	private static List<String> pixelKeys(JsonNode matrix) {
		var keys = new ArrayList<String>();
		if (matrix.path("pixelKeys").isArray()) {
			for (var layer : matrix.path("pixelKeys")) {
				for (var row : layer) {
					for (var key : row) {
						if (!key.isNull()) {
							keys.add(key.asString());
						}
					}
				}
			}
		} else if (matrix.path("pixelCount").isArray()) {
			var count = 1;
			for (var dimension : matrix.path("pixelCount")) {
				count *= Math.max(1, dimension.asInt());
			}
			for (int i = 1; i <= count; i++) {
				keys.add(String.valueOf(i));
			}
		}
		return keys;
	}

	// --- helpers -----------------------------------------------------------------------------------------------

	private static String type(JsonNode capability) {
		return capability.path("type").asString("");
	}

	/** A channel key for display: without the pixel key placeholder. */
	private static String displayKey(String key) {
		return key == null ? "Channel" : key.replace(PIXEL_KEY, "").strip();
	}

	/** "ShutterStrobe" → "Shutter strobe". */
	private static String words(String camelCase) {
		var spaced = camelCase.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
		return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	/** "Cameo Flat PAR 1.0" → "cameo-flat-par-1-0". */
	static String slug(String text) {
		var ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}
}
