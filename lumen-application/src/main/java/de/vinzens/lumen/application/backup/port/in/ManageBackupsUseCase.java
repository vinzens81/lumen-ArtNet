package de.vinzens.lumen.application.backup.port.in;

import de.vinzens.lumen.application.backup.port.out.DatabaseBackups.Backup;

import java.util.List;

public interface ManageBackupsUseCase {

	/** Newest first. */
	List<Backup> backups();

	String location();

	Backup createBackup();
}
