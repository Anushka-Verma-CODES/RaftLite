package com.raftlite.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Opens the SQLite database and makes sure the schema exists.
 *
 * SQLite stores the whole database in one file, so nothing has to be
 * installed: the file is created the first time it is opened. The schema
 * lives in {@code src/main/resources/db/schema.sql}.
 */
public final class DatabaseManager implements AutoCloseable {

    /** Database file used by the demo when no other path is given. */
    public static final String DEFAULT_DATABASE_FILE = "raftlite.db";

    private static final String SCHEMA_RESOURCE = "/db/schema.sql";

    private final String jdbcUrl;
    private final Connection connection;

    /**
     * Opens (or creates) the database file at the given path.
     *
     * @throws PersistenceException if the file cannot be opened or the
     *                              schema cannot be created
     */
    public DatabaseManager(String databaseFile) {

        Objects.requireNonNull(databaseFile, "databaseFile must not be null");

        if (databaseFile.isBlank()) {
            throw new IllegalArgumentException("databaseFile must not be blank");
        }

        this.jdbcUrl = "jdbc:sqlite:" + databaseFile;

        try {
            this.connection = DriverManager.getConnection(jdbcUrl);

            try (Statement statement = connection.createStatement()) {
                // SQLite only enforces foreign keys when asked to.
                statement.execute("PRAGMA foreign_keys = ON");
            }

            createSchema();

        } catch (SQLException e) {
            throw new PersistenceException(
                    "Could not open database " + databaseFile, e);
        }
    }

    /** An in-memory database that disappears when closed (used by tests). */
    public static DatabaseManager inMemory() {
        return new DatabaseManager(":memory:");
    }

    public Connection getConnection() {
        return connection;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    private void createSchema() throws SQLException {

        String script = stripComments(readSchemaScript());

        try (Statement statement = connection.createStatement()) {

            for (String sql : script.split(";")) {

                String trimmed = sql.trim();

                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        }
    }

    private static String readSchemaScript() {

        try (InputStream in =
                     DatabaseManager.class.getResourceAsStream(SCHEMA_RESOURCE)) {

            if (in == null) {
                throw new PersistenceException(
                        "Schema resource not found: " + SCHEMA_RESOURCE);
            }

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new PersistenceException("Could not read the database schema", e);
        }
    }

    /** Removes "-- ..." line comments so they are not sent as SQL. */
    private static String stripComments(String sql) {

        StringBuilder result = new StringBuilder();

        for (String line : sql.split("\\R")) {

            int comment = line.indexOf("--");
            result.append(comment >= 0 ? line.substring(0, comment) : line)
                  .append('\n');
        }

        return result.toString();
    }

    @Override
    public void close() {

        try {
            connection.close();
        } catch (SQLException e) {
            throw new PersistenceException("Could not close the database", e);
        }
    }
}
