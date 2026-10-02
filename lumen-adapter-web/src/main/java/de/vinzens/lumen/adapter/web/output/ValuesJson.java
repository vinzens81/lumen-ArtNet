package de.vinzens.lumen.adapter.web.output;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * JSON form of attribute values: {@code {"dimmer": 1, "red": 0.5, "generic:Program speed": 0.2}}.
 */
public final class ValuesJson {

	private ValuesJson() {
	}

	public static FixtureValues fromJson(Map<String, Double> body) {
		var values = new HashMap<Attribute, Double>();
		var errors = new ArrayList<String>();
		(body == null ? Map.<String, Double>of() : body).forEach((id, value) -> {
			try {
				values.put(Attribute.parse(id), value);
			} catch (DomainValidationException e) {
				errors.addAll(e.errors());
			}
		});
		if (!errors.isEmpty()) {
			throw new DomainValidationException(errors);
		}
		return new FixtureValues(values);
	}

	public static Map<String, Double> toJson(FixtureValues values) {
		var result = new TreeMap<String, Double>();
		values.values().forEach((attribute, value) -> result.put(attribute.id(), value));
		return result;
	}

	public static Map<Long, Map<String, Double>> toJson(Map<Long, FixtureValues> values) {
		var result = new TreeMap<Long, Map<String, Double>>();
		values.forEach((fixtureId, fixtureValues) -> result.put(fixtureId, toJson(fixtureValues)));
		return result;
	}
}
