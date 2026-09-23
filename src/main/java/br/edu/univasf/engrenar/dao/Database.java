package br.edu.univasf.engrenar.dao;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import org.h2.tools.RunScript;

public final class Database {
    private final String url;

    public Database(String url, boolean seed) {
        this.url = url;

        try (Connection c = open()) {
            script(c, "/db/schema.sql");

            c.setAutoCommit(false);

            try (var s = c.createStatement();
                 var r = s.executeQuery("SELECT COUNT(*) FROM app_meta")) {

                r.next();

                if (r.getInt(1) == 0) {
                    if (seed) {
                        script(c, "/db/seed.sql");
                    } else {
                        s.executeUpdate(
                                "INSERT INTO app_meta(version) VALUES(1)"
                        );
                    }
                }

                // Authentication data is independent of the demonstration seed. Old
                // installations already have app_meta=1, but have no app_user rows.
                // Re-running this migration never changes an existing account.
                // Lock the existing migration marker so concurrent upgrades serialize.
                try (var versions = s.executeQuery("SELECT version FROM app_meta FOR UPDATE")) {
                    boolean needsUpgrade = versions.next() && versions.getInt(1) < 2;
                    if (needsUpgrade) {
                        if (seed) script(c, "/db/users.sql");
                        try (var migration = c.createStatement()) {
                            migration.executeUpdate("UPDATE app_meta SET version=2 WHERE version=1");
                        }
                    }
                }
                c.commit();

            } catch (Exception e) {
                c.rollback();
                throw e;
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Não foi possível inicializar o banco local. "
                            + e.getMessage(), e
            );
        }
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(
                url,
                "engrenar",
                "engrenar"
        );
    }

    private void script(Connection c, String resource)
            throws SQLException, IOException {

        try (var stream = Database.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("SQL não encontrado: " + resource);
            }

            RunScript.execute(
                    c,
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
        }
    }

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    public <T> T transaction(Work<T> work) {
        try (Connection c = open()) {
            c.setAutoCommit(false);

            try {
                T result = work.run(c);
                c.commit();
                return result;

            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Falha no banco de dados local.", e
            );
        }
    }
}
