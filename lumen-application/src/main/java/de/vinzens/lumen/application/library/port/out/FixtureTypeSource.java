package de.vinzens.lumen.application.library.port.out;

/**
 * Quelle der Gerätebeschreibungen, z.B. ein Ordner mit YAML-Dateien.
 */
public interface FixtureTypeSource {

	/**
	 * Lädt alle Gerätetypen. Fehlerhafte Einträge werden nicht geworfen, sondern als Issues zurückgegeben.
	 */
	LoadResult loadAll();
}
