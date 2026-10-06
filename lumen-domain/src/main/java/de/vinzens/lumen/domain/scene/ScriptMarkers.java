package de.vinzens.lumen.domain.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Light markers in the script of a scene: a line {@code //## Name (info)##} starts a new part of the scene with light
 * of its own. The same rule as the script view of the web UI ({@code lib/script.ts}).
 */
public final class ScriptMarkers {

	private static final Pattern MARKER = Pattern.compile("^\\s*//\\s*##\\s*(.*?)\\s*##\\s*$");
	private static final Pattern INFO = Pattern.compile("^(.*?)\\s*\\((.*)\\)$");

	/** A marker; {@code info} is the text in brackets, empty if there is none. */
	public record Marker(String name, String info) {
	}

	private ScriptMarkers() {
	}

	/** The markers of the script in their order (names may repeat). */
	public static List<Marker> parse(String script) {
		var result = new ArrayList<Marker>();
		for (var line : script.split("\n")) {
			var marker = MARKER.matcher(line);
			if (!marker.matches() || marker.group(1).isBlank()) {
				continue;
			}
			var text = marker.group(1);
			var info = INFO.matcher(text);
			result.add(info.matches() && !info.group(1).isBlank()
					? new Marker(info.group(1).strip(), info.group(2).strip())
					: new Marker(text.strip(), ""));
		}
		return List.copyOf(result);
	}
}
