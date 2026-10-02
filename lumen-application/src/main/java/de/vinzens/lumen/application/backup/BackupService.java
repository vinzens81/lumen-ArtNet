package de.vinzens.lumen.application.backup;

import de.vinzens.lumen.application.backup.port.in.ManageBackupsUseCase;
import de.vinzens.lumen.application.backup.port.out.DatabaseBackups;
import de.vinzens.lumen.application.backup.port.out.DatabaseBackups.Backup;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.domain.output.OperatingMode;
import de.vinzens.lumen.domain.project.Project;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Backs up the database automatically when show mode starts and when another project is activated, plus on
 * request. Automatic backups run on the given executor and never get in the way of the show.
 */
public class BackupService implements ManageBackupsUseCase {

	private static final System.Logger LOG = System.getLogger(BackupService.class.getName());

	private final DatabaseBackups backups;
	private final ControlModeUseCase mode;
	private final ProjectRepository projects;
	private final Executor executor;

	private OperatingMode lastMode;
	private Optional<Long> lastActiveProject;

	public BackupService(DatabaseBackups backups, ControlModeUseCase mode, ProjectRepository projects,
			Executor executor, StateEvents events) {
		this.backups = Objects.requireNonNull(backups, "backups");
		this.mode = Objects.requireNonNull(mode, "mode");
		this.projects = Objects.requireNonNull(projects, "projects");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.lastMode = mode.mode();
		this.lastActiveProject = activeProject();
		events.subscribe(this::changed);
	}

	private synchronized void changed(StateTopic topic) {
		if (topic == StateTopic.MODE) {
			var current = mode.mode();
			if (current == OperatingMode.SHOW && lastMode != OperatingMode.SHOW) {
				backupInBackground("show");
			}
			lastMode = current;
		} else if (topic == StateTopic.PROJECTS) {
			var current = activeProject();
			if (current.isPresent() && !current.equals(lastActiveProject)) {
				backupInBackground("project");
			}
			lastActiveProject = current;
		}
	}

	/** Creates a backup without waiting; failures are only logged. */
	public void backupInBackground(String reason) {
		executor.execute(() -> {
			try {
				var backup = backups.create(reason);
				LOG.log(Level.INFO, "Database backup {0} created", backup.name());
			} catch (RuntimeException e) {
				LOG.log(Level.WARNING, "Database backup (" + reason + ") failed", e);
			}
		});
	}

	@Override
	public List<Backup> backups() {
		return backups.list();
	}

	@Override
	public String location() {
		return backups.location();
	}

	@Override
	public Backup createBackup() {
		return backups.create("manual");
	}

	private Optional<Long> activeProject() {
		return projects.findAll().stream().filter(Project::active).map(Project::id).findFirst();
	}
}
