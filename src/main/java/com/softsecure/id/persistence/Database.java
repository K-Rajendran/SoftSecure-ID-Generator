package com.softsecure.id.persistence;

import com.softsecure.id.config.AppConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

/** One short-lived connection per operation; no connections are held by Swing screens. */
public final class Database {
    private final String url;
    private final Properties properties = new Properties();

    public Database(AppConfig config) {
        url = config.required("db.url");
        // URL options cannot silently override the centrally enforced TLS policy.
        if (!url.matches("jdbc:mariadb://[A-Za-z0-9.\\-]+(?::[0-9]{1,5})?/[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Use a single-host MariaDB URL without query options.");
        }
        boolean insecure = config.flag("db.allowInsecureLocal");
        if (insecure && !url.matches("jdbc:mariadb://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?/.*")) {
            throw new IllegalArgumentException("Insecure database access is limited to localhost.");
        }
        properties.setProperty("user", config.required("db.user"));
        properties.setProperty("password", config.required("db.password"));
        properties.setProperty("sslMode", insecure ? "disable" : "verify-full");
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "30000");
        properties.setProperty("allowLocalInfile", "false");
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, properties);
    }

    @FunctionalInterface
    public interface Work<T> {
        T perform(Connection connection) throws Exception;
    }

    public <T> T transaction(Work<T> work) throws Exception {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.perform(connection);
                connection.commit();
                return result;
            } catch (Exception exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        }
    }

    public static PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            statement.setQueryTimeout(15);
            return statement;
        } catch (SQLException exception) {
            statement.close();
            throw exception;
        }
    }

    public static void audit(Connection connection, String actor, String action, String target) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                "INSERT INTO ss_audit(actor_id,action,target_id) VALUES(?,?,?)")) {
            statement.setString(1, actor);
            statement.setString(2, action);
            statement.setString(3, target);
            statement.executeUpdate();
        }
    }
}
