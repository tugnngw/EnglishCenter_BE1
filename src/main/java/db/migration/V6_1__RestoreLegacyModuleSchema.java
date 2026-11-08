package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/** Restores module migrations skipped by databases that already used the old V2-V5 numbering. */
public class V6_1__RestoreLegacyModuleSchema extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (var statement = connection.createStatement();
             var history = statement.executeQuery("""
                     SELECT EXISTS (
                         SELECT 1 FROM flyway_schema_history
                         WHERE version = '4' AND success
                         AND script = 'V4__add_must_change_password_and_mentor_fields.sql'
                     )
                     """)) {
            history.next();
            if (!history.getBoolean(1)) return;
        }

        execute(connection, "V2__add_user_role.sql");
        execute(connection, "V3__d2_course_schema.sql");
        // V4 creates tables and a sequence, so replay it only when its order table is absent.
        try (var statement = connection.createStatement();
             var tables = statement.executeQuery("SELECT to_regclass('payment_orders') IS NULL")) {
            tables.next();
            if (tables.getBoolean(1)) execute(connection, "V4__sepay_payment_enrollment.sql");
        }
        execute(connection, "V5__d3_exam_schema.sql");
    }

    private void execute(Connection connection, String resourceName) throws Exception {
        try (var resource = getClass().getResourceAsStream("/db/migration/" + resourceName)) {
            if (resource == null) throw new IllegalStateException("Missing migration " + resourceName);
            String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            // PostgreSQL executes the complete script, preserving dollar-quoted function bodies.
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }
}
