package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.legacy.LegacyMigrationService;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.composition.PlayMode;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.FixtureCategory;
import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.LibraryIssue;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.library.PhysicalProperties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A database of the model before iteration S (schema V18 with scenes, compositions and extra keys) is migrated and
 * converted at startup; so is a converted database with faders playing scenes outside the cue list (schema V20).
 */
class LegacyConversionTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Mode ONE_CHANNEL = new Mode("1ch", List.of(new ChannelDefinition(ChannelFunction.DIMMER,
			Optional.empty(), 8, 0, false, List.of(), Optional.empty())));
	private static final FixtureType DIMMER_TYPE = new FixtureType("generic-dimmer", "Generic", "Dimmer",
			FixtureCategory.DIMMER, PhysicalProperties.NONE, List.of(ONE_CHANNEL));

	@TempDir
	Path dir;

	@Test
	void anOldShowBecomesLooksCompositionsAndScenes() {
		var file = dir.resolve("lumen.db");
		oldShow(file);

		var repos = new SqliteRepositories(SqliteDatabase.open(file));
		var reports = migration(repos).convertPending();

		assertThat(reports).containsOnlyKeys(1L);
		var scenes = repos.scenes().findByProject(1);
		assertThat(scenes).as("the scene only for keys is gone").extracting(s -> s.spec().name())
				.containsExactly("Dawn", "Fair");
		var dawn = scenes.get(0);
		var fair = scenes.get(1);
		assertThat(repos.faders().findByProject(1)).singleElement().satisfies(fader -> {
			assertThat(fader.name()).contains("Storm look");
			assertThat(composition(repos, fader.compositionId()).spec().name()).isEqualTo("3 Storm look");
		});

		var dawnLook = composition(repos, dawn.compositions().getFirst().compositionId());
		assertThat(dawnLook.spec().name()).isEqualTo("1 Dawn");
		var lookId = dawnLook.steps().getFirst().lookId();
		assertThat(repos.looks().findById(lookId).orElseThrow().look().values().get(10L).get(DIMMER)).contains(0.8);
		assertThat(repos.looks().findRevisions(lookId)).singleElement()
				.satisfies(revision -> assertThat(revision.reason()).isEqualTo("Update from programmer"));

		var chaseInDawn = dawn.compositions().get(1);
		var chaseInFair = fair.compositions().get(1);
		assertThat(chaseInFair.compositionId()).as("the same library chase in both").isEqualTo(
				chaseInDawn.compositionId());
		assertThat(chaseInFair.level()).isEqualTo(0.5);
		var chase = composition(repos, chaseInDawn.compositionId());
		assertThat(chase.steps()).as("one step per fixture of the group").hasSize(2);
		assertThat(chase.spec().generator()).isPresent();

		var steps = composition(repos, fair.compositions().get(2).compositionId());
		assertThat(steps.spec().playMode()).isEqualTo(PlayMode.PING_PONG);
		assertThat(steps.steps()).hasSize(2);

		assertThat(dawn.keys()).singleElement().satisfies(key -> {
			assertThat(key.slot()).isEqualTo(1);
			assertThat(key.mode()).isEqualTo(KeyMode.FLASH);
		});
		assertThat(fair.keys()).extracting(k -> k.slot()).containsExactly(1, 2);
		assertThat(composition(repos, fair.keys().get(1).compositionId()).spec().name())
				.isEqualTo("3 Storm look");

		assertThat(migration(new SqliteRepositories(SqliteDatabase.open(file))).convertPending())
				.as("converted only once").isEmpty();
	}

	@Test
	void faderOfAConvertedShowPlaysTheFirstCompositionOfItsScene() {
		var file = dir.resolve("lumen.db");
		var jdbc = migrateTo(file, "20");
		sql(jdbc, "INSERT INTO project (id, name, active, created_at, updated_at) VALUES (1, 'Show', 1, '"
				+ Instant.EPOCH + "', '" + Instant.EPOCH + "')");
		sql(jdbc, "INSERT INTO composition (id, project_id, name, fade_ms, hold_ms, play_mode, intensity_mode) VALUES "
				+ "(1, 1, 'Dawn', 0, 1000, 'loop', 'absolute'), (2, 1, 'House full', 0, 1000, 'loop', 'absolute'), "
				+ "(3, 1, 'Glow', 0, 1000, 'loop', 'absolute')");
		sql(jdbc, "INSERT INTO scene (id, project_id, number, name, outside_cue_list) VALUES (1, 1, '1', 'Dawn', 0), "
				+ "(50, 1, '50', 'House', 1), (51, 1, '51', 'Empty', 1)");
		sql(jdbc, "INSERT INTO scene_composition (scene_id, composition_id, position, level, speed_factor, restart) "
				+ "VALUES (1, 1, 0, 1, 1, 0), (50, 3, 1, 1, 1, 0), (50, 2, 0, 1, 1, 0)");
		sql(jdbc, "INSERT INTO fader (project_id, slot, name, scene_id) VALUES (1, 1, NULL, 50), (1, 2, 'Own', 51)");

		var repos = new SqliteRepositories(SqliteDatabase.open(file));

		assertThat(repos.scenes().findByProject(1)).extracting(s -> s.spec().name()).containsExactly("Dawn");
		assertThat(repos.faders().findByProject(1)).as("a fader of an empty scene is gone").singleElement()
				.satisfies(fader -> {
					assertThat(fader.slot()).isEqualTo(1);
					assertThat(fader.name()).contains("House");
					assertThat(fader.compositionId()).isEqualTo(2);
				});
		assertThat(repos.compositions().findByProject(1)).as("compositions stay").hasSize(3);
		assertThat(repos.legacyShows().pendingProjects()).isEmpty();
	}

	private static Composition composition(SqliteRepositories repos, long id) {
		return repos.compositions().findById(id).orElseThrow();
	}

	private static LegacyMigrationService migration(SqliteRepositories repos) {
		return new LegacyMigrationService(repos.legacyShows(), repos.projects(), repos.fixtures(), repos.groups(),
				repos.looks(), repos.compositions(), repos.scenes(), repos.faders(), library(),
				Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC));
	}

	/** Scenes 1 and 2 of the cue list, scene 3 only for keys; a chase, a step sequence and two extra keys. */
	private static void oldShow(Path file) {
		var jdbc = migrateTo(file, "18");
		sql(jdbc, "INSERT INTO project (id, name, active, created_at, updated_at) VALUES (1, 'Show', 1, '"
				+ Instant.EPOCH + "', '" + Instant.EPOCH + "')");
		sql(jdbc, "INSERT INTO universe (id, project_id, number, target) VALUES (1, 1, 1, 'broadcast')");
		sql(jdbc, "INSERT INTO fixture (id, project_id, name, fixture_type_id, mode_name, universe_id, address) "
				+ "VALUES (10, 1, 'Left', 'generic-dimmer', '1ch', 1, 1), (11, 1, 'Right', 'generic-dimmer', '1ch', 1, 2)");
		sql(jdbc, "INSERT INTO fixture_group (id, project_id, name, sort_order) VALUES (5, 1, 'Ramp', 0)");
		sql(jdbc, "INSERT INTO fixture_group_member (group_id, fixture_id, position) VALUES (5, 10, 0), (5, 11, 1)");
		sql(jdbc, "INSERT INTO scene (id, project_id, number, name, key_only) VALUES (1, 1, '1', 'Dawn', 0), "
				+ "(2, 1, '2', 'Fair', 0), (3, 1, '3', 'Storm look', 1)");
		sql(jdbc, "INSERT INTO scene_value (scene_id, fixture_id, attribute, value) VALUES (1, 10, 'dimmer', 0.8), "
				+ "(2, 11, 'dimmer', 0.6), (3, 10, 'dimmer', 1.0)");
		sql(jdbc, "INSERT INTO scene_revision (id, scene_id, created_at, reason) VALUES (1, 1, '"
				+ Instant.EPOCH + "', 'Update from programmer')");
		sql(jdbc, "INSERT INTO scene_revision_value (revision_id, fixture_id, attribute, value) VALUES (1, 10, 'dimmer', 0.2)");
		var chase = "{\"stepSeconds\":0.5,\"width\":1,\"direction\":\"FORWARD\",\"softness\":0.0,\"min\":0.0,\"max\":1.0}";
		sql(jdbc, "INSERT INTO composition (id, project_id, scene_id, source_id, name, kind, params, target, group_id, "
				+ "position, level) VALUES "
				+ "(20, 1, NULL, NULL, 'Chase', 'chase', '" + chase + "', 'group', 5, 0, 1), "
				+ "(21, 1, 1, 20, 'Chase', 'chase', '" + chase + "', 'group', 5, 0, 1), "
				+ "(22, 1, 2, 20, 'Chase', 'chase', '" + chase + "', 'group', 5, 0, 0.5), "
				+ "(23, 1, 2, NULL, 'Blink', 'steps', '{\"playMode\":\"PING_PONG\"}', 'steps', NULL, 1, 1)");
		sql(jdbc, "INSERT INTO composition_step (composition_id, step_index, hold_ms, fade_ms) VALUES "
				+ "(23, 0, 1000, 0), (23, 1, 1000, 0)");
		sql(jdbc, "INSERT INTO composition_step_value (composition_id, step_index, fixture_id, attribute, value) VALUES "
				+ "(23, 0, 10, 'dimmer', 1.0), (23, 1, 11, 'dimmer', 1.0)");
		sql(jdbc, "INSERT INTO composition (id, project_id, key_slot, key_mode, key_fade_ms, name, kind, params, target, "
				+ "group_id) VALUES (30, 1, 1, 'flash', 0, 'Lightning', 'chase', '" + chase + "', 'group', 5)");
		sql(jdbc, "INSERT INTO extra_key_scene (project_id, key_slot, scene_id, key_mode, key_fade_ms) "
				+ "VALUES (1, 2, 3, 'toggle', 500)");
		sql(jdbc, "INSERT INTO scene_extra_key (scene_id, key_slot) VALUES (1, 1), (2, 1), (2, 2)");
		sql(jdbc, "INSERT INTO fader (project_id, slot, name, scene_id) VALUES (1, 1, NULL, 3)");
	}

	private static JdbcClient migrateTo(Path file, String version) {
		var config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		var dataSource = new SQLiteDataSource(config);
		dataSource.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(version).load().migrate();
		return JdbcClient.create(dataSource);
	}

	private static void sql(JdbcClient jdbc, String statement) {
		jdbc.sql(statement).update();
	}

	private static QueryFixtureLibraryUseCase library() {
		return new QueryFixtureLibraryUseCase() {
			public List<FixtureType> fixtureTypes() {
				return List.of(DIMMER_TYPE);
			}

			public Optional<FixtureType> fixtureType(String id) {
				return Optional.of(DIMMER_TYPE).filter(type -> type.id().equals(id));
			}

			public List<LibraryIssue> issues() {
				return List.of();
			}
		};
	}
}
