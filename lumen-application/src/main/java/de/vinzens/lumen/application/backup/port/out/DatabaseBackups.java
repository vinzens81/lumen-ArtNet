package de.vinzens.lumen.application.backup.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Consistent copies of the whole database; old copies are removed automatically.
 */
public interface DatabaseBackups {

	/** @param reason short word for the file name, e.g. {@code startup} or {@code show} */
	Backup create(String reason);

	/** Existing backups, newest first. */
	List<Backup> list();

	/** Where backups are stored, for display. */
	String location();

	record Backup(String name, Instant createdAt, long sizeBytes) {
	}
}
