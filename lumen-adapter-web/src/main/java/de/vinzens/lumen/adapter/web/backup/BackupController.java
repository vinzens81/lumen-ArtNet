package de.vinzens.lumen.adapter.web.backup;

import de.vinzens.lumen.application.backup.port.in.ManageBackupsUseCase;
import de.vinzens.lumen.application.backup.port.out.DatabaseBackups.Backup;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Database backups. They are created automatically when show mode starts and when another project is activated;
 * to restore one, stop Lumen and copy it over the database file.
 */
@RestController
@RequestMapping("/api/backups")
class BackupController {

	private final ManageBackupsUseCase backups;

	BackupController(ManageBackupsUseCase backups) {
		this.backups = backups;
	}

	@GetMapping
	BackupsResponse list() {
		return new BackupsResponse(backups.location(), backups.backups().stream().map(BackupResponse::from).toList());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	BackupResponse create() {
		return BackupResponse.from(backups.createBackup());
	}

	/** @param directory where the backup files are stored */
	record BackupsResponse(String directory, List<BackupResponse> backups) {
	}

	record BackupResponse(String name, Instant createdAt, long sizeBytes) {

		static BackupResponse from(Backup backup) {
			return new BackupResponse(backup.name(), backup.createdAt(), backup.sizeBytes());
		}
	}
}
