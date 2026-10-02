package de.vinzens.lumen.application.library.port.out;

/**
 * Source of fixture descriptions, e.g. a directory of YAML files.
 */
public interface FixtureTypeSource {

	/**
	 * Loads all fixture types. Faulty entries are not thrown but returned as issues.
	 */
	LoadResult loadAll();
}
