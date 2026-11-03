package com.softsecure.id.persistence;

import com.softsecure.id.domain.Role;
import com.softsecure.id.security.Crypto;
import com.softsecure.id.security.Session;
import com.softsecure.id.service.UserException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

public final class UserRepository {
    public record Account(String id, String username, String passwordHash, Role role, boolean enabled, int sessionVersion,
                          int failures, Instant lockedUntil) {
        @Override public String toString() { return "Account[redacted]"; }
    }

    private final Crypto crypto;

    public UserRepository(Crypto crypto) {
        this.crypto = crypto;
    }

    public Account findByUsername(Connection connection, String username, boolean lock) throws Exception {
        return find(connection, "username_tag", crypto.usernameTag(username), lock);
    }

    public Account findById(Connection connection, String id, boolean lock) throws Exception {
        return find(connection, "id", id, lock);
    }

    private Account find(Connection connection, String column, String value, boolean lock) throws Exception {
        // column and lock suffix are internal constants, never caller-provided SQL.
        String sql = "SELECT id,username_enc,password_hash,role,enabled,session_version,failed_attempts,locked_until "
                + "FROM ss_users WHERE " + column + "=?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = Database.prepare(connection, sql)) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                String id = rows.getString("id");
                var locked = rows.getTimestamp("locked_until");
                return new Account(id, crypto.decryptText(rows.getString("username_enc"), "user:" + id),
                        rows.getString("password_hash"), Role.valueOf(rows.getString("role")),
                        rows.getBoolean("enabled"), rows.getInt("session_version"), rows.getInt("failed_attempts"), locked == null ? null : locked.toInstant());
            }
        }
    }

    public String insert(Connection connection, String username, String hash, Role role) throws Exception {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement statement = Database.prepare(connection,
                "INSERT INTO ss_users(id,username_tag,username_enc,password_hash,role) VALUES(?,?,?,?,?)")) {
            statement.setString(1, id);
            statement.setString(2, crypto.usernameTag(username));
            statement.setString(3, crypto.encryptText(username, "user:" + id));
            statement.setString(4, hash);
            statement.setString(5, role.name());
            statement.executeUpdate();
        }
        return id;
    }

    public Account authorize(Connection connection, Session session, Role... allowed) throws Exception {
        session.requireActive();
        Account account = findById(connection, session.userId(), true);
        if (account == null || !account.enabled() || account.role() != session.role() || account.sessionVersion() != session.version()) {
            session.close();
            throw new UserException("Your account access has changed. Sign in again.");
        }
        if (allowed.length > 0 && java.util.Arrays.stream(allowed).noneMatch(role -> role == account.role())) {
            throw new UserException("Your role does not permit this operation.");
        }
        return account;
    }
}
