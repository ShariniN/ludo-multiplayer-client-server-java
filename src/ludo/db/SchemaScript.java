package ludo.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Loads schema.sql from the classpath and executes it statement by statement. */
final class SchemaScript {

    private static final String RESOURCE = "/ludo/db/schema.sql";

    private SchemaScript() {
    }

    /** Creates any missing tables; with reset=true drops everything first (initial demo state). */
    static void apply(Connection connection, boolean reset) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            if (reset) {
                stmt.execute("DROP ALL OBJECTS");
            }
            for (String sql : statements()) {
                stmt.execute(sql);
            }
        }
    }

    static List<String> statements() {
        String script;
        try (InputStream in = SchemaScript.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is not on the classpath (run compile_all.bat)");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }

        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (!line.trim().startsWith("--")) {
                withoutComments.append(line).append('\n');
            }
        }
        List<String> result = new ArrayList<>();
        for (String sql : withoutComments.toString().split(";")) {
            if (!sql.isBlank()) {
                result.add(sql.trim());
            }
        }
        return result;
    }
}
