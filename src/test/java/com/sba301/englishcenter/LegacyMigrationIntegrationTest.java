package com.sba301.englishcenter;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/** Destructive fixtures are restricted to the explicitly named disposable regression database. */
@EnabledIfEnvironmentVariable(named = "LEGACY_MIGRATION_TEST_DB_URL", matches = ".+")
class LegacyMigrationIntegrationTest {
    private final String url = System.getenv("LEGACY_MIGRATION_TEST_DB_URL");
    private final String password = System.getenv("LEGACY_MIGRATION_TEST_DB_PASSWORD");

    private Flyway migration(String target) {
        assertTrue(url.endsWith("/englishcenter_legacy_migration_test"), "Use the dedicated disposable database");
        var configuration = Flyway.configure().dataSource(url, "postgres", password)
                .locations("classpath:db/migration").validateOnMigrate(false).cleanDisabled(false);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    @BeforeEach
    void resetDisposableDatabase() {
        migration(null).clean();
    }

    @Test
    void restoresSkippedModulesBeforeVersionSevenWithoutReplacingLegacyHistory() throws Exception {
        migration("1").migrate();
        try (var connection = DriverManager.getConnection(url, "postgres", password);
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO courses(course_id,created_by_user_id,title,price,status)
                    SELECT '10000000-0000-0000-0000-000000000001', user_id, 'Preserve legacy course', 10000, 'active'
                    FROM users WHERE email='admin@gmail.com';
                    INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,installed_by,execution_time,success)
                    VALUES
                    (2,'2','remove default admin','SQL','V2__remove_default_admin.sql','postgres',0,true),
                    (3,'3','fix user view remove password','SQL','V3__fix_user_view_remove_password.sql','postgres',0,true),
                    (4,'4','add must change password and mentor fields','SQL','V4__add_must_change_password_and_mentor_fields.sql','postgres',0,true),
                    (5,'5','add notification read status','SQL','V5__add_notification_read_status.sql','postgres',0,true);
                    """);
        }
        migration(null).migrate();
        assertCurrentSchema();
        try (var connection = DriverManager.getConnection(url, "postgres", password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT
                     (SELECT count(*) FROM courses WHERE title='Preserve legacy course'),
                     (SELECT script FROM flyway_schema_history WHERE version='4'),
                     (SELECT count(*) FROM users WHERE email='admin@gmail.com')
                     """)) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
            assertEquals("V4__add_must_change_password_and_mentor_fields.sql", result.getString(2));
            assertEquals(1, result.getInt(3));
        }
    }

    @Test
    void freshDatabaseStillMigratesNormally() throws Exception {
        migration(null).migrate();
        assertCurrentSchema();
    }

    private void assertCurrentSchema() throws Exception {
        try (var connection = DriverManager.getConnection(url, "postgres", password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT
                     to_regclass('payment_orders') IS NOT NULL,
                     to_regclass('sepay_webhook_events') IS NOT NULL,
                     to_regclass('exam_sections') IS NOT NULL,
                     EXISTS (SELECT 1 FROM roles WHERE role_name='USER'),
                     EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='payment_orders' AND column_name='booking_id'),
                     EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='exam_attempts' AND column_name='expires_at'),
                     EXISTS (SELECT 1 FROM flyway_schema_history WHERE version='6.1' AND success),
                     EXISTS (SELECT 1 FROM flyway_schema_history WHERE version='10' AND success)
                     """)) {
            assertTrue(result.next());
            for (int column = 1; column <= 8; column++) assertTrue(result.getBoolean(column), "Schema check " + column);
        }
    }
}
