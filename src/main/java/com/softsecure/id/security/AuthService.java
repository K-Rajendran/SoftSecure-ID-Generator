package com.softsecure.id.security;

import com.softsecure.id.config.AppConfig;
import com.softsecure.id.domain.Role;
import com.softsecure.id.persistence.Database;
import com.softsecure.id.persistence.UserRepository;
import com.softsecure.id.service.UserException;
import com.softsecure.id.service.Validation;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.Arrays;

public final class AuthService {
    private static final String FAILURE = "Sign-in failed. Check your credentials or try again in 15 minutes.";
    private final Database database;
    private final UserRepository users;
    private final PasswordHasher passwords;
    private final Duration idle;
    private final Duration maximum;
    private final String dummyHash;
    private long lastAttempt;
    private boolean attempted;

    public AuthService(Database database, UserRepository users, PasswordHasher passwords, AppConfig config) throws Exception {
        this.database = database;
        this.users = users;
        this.passwords = passwords;
        idle = Duration.ofMinutes(config.number("session.idleMinutes", 15, 1, 60));
        maximum = Duration.ofHours(config.number("session.maxHours", 8, 1, 24));
        char[] dummy = new char[32];
        Arrays.fill(dummy, 'x');
        try {
            dummyHash = passwords.hash(dummy);
        } finally {
            Arrays.fill(dummy, '\0');
        }
    }

    /** Serializes attempts in this process; row locks serialize persistent per-account counters. */
    public synchronized Session login(String username, char[] password) throws Exception {
        try {
            long now = System.nanoTime();
            if (attempted && now - lastAttempt < Duration.ofSeconds(1).toNanos()) {
                throw new UserException(FAILURE);
            }
            lastAttempt = now;
            attempted = true;
            String name = Validation.identifier(username);
            if (password.length == 0 || password.length > 128) {
                throw new UserException(FAILURE);
            }
            Session session = database.transaction(connection -> {
                var account = users.findByUsername(connection, name, true);
                boolean valid = passwords.verify(password, account == null ? dummyHash : account.passwordHash());
                if (account == null) {
                    return null;
                }
                // Database time avoids client clock differences in durable lockouts.
                boolean locked;
                try (PreparedStatement query = Database.prepare(connection,
                        "SELECT locked_until > CURRENT_TIMESTAMP FROM ss_users WHERE id=?")) {
                    query.setString(1, account.id());
                    try (var rows = query.executeQuery()) {
                        rows.next();
                        locked = rows.getBoolean(1);
                    }
                }
                if (!account.enabled() || locked) {
                    return null;
                }
                if (!valid) {
                    // Reset a previous lockout's failure window once its interval has elapsed.
                    int failures = account.lockedUntil() == null ? account.failures() + 1 : 1;
                    String sql = "UPDATE ss_users SET failed_attempts=?,locked_until="
                            + (failures >= 5 ? "DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 15 MINUTE)" : "NULL") + " WHERE id=?";
                    try (PreparedStatement update = Database.prepare(connection, sql)) {
                        update.setInt(1, failures);
                        update.setString(2, account.id());
                        update.executeUpdate();
                    }
                    Database.audit(connection, account.id(), "LOGIN_FAILED", account.id());
                    return null; // Commit the counter; do not throw inside this transaction.
                }
                try (PreparedStatement update = Database.prepare(connection,
                        "UPDATE ss_users SET failed_attempts=0,locked_until=NULL WHERE id=?")) {
                    update.setString(1, account.id());
                    update.executeUpdate();
                }
                Database.audit(connection, account.id(), "LOGIN", account.id());
                return new Session(account.id(), account.username(), account.role(), account.sessionVersion(), idle, maximum);
            });
            if (session == null) {
                throw new UserException(FAILURE);
            }
            return session;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public void createMember(Session session, String username, char[] password, Role role) throws Exception {
        try {
            Validation.password(password);
            String name = Validation.identifier(username);
            if (role != Role.STUDENT && role != Role.FACULTY) {
                throw new UserException("Only student and faculty accounts can be created here.");
            }
            String hash = passwords.hash(password);
            database.transaction(connection -> {
                users.authorize(connection, session, Role.ADMIN);
                if (users.findByUsername(connection, name, false) != null) {
                    throw new UserException("That user ID already exists.");
                }
                String id = users.insert(connection, name, hash, role);
                session.requireActive();
                Database.audit(connection, session.userId(), "CREATE_ACCOUNT", id);
                return null;
            });
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public void changePassword(Session session, char[] current, char[] replacement) throws Exception {
        try {
            Validation.password(replacement);
            if (current.length > 128) {
                throw new UserException("Current password is incorrect.");
            }
            database.transaction(connection -> {
                var account = users.authorize(connection, session);
                if (!passwords.verify(current, account.passwordHash())) {
                    throw new UserException("Current password is incorrect.");
                }
                try (PreparedStatement update = Database.prepare(connection,
                        "UPDATE ss_users SET password_hash=?,session_version=session_version+1 WHERE id=?")) {
                    update.setString(1, passwords.hash(replacement));
                    update.setString(2, account.id());
                    update.executeUpdate();
                }
                Database.audit(connection, account.id(), "CHANGE_PASSWORD", account.id());
                session.requireActive();
                return null;
            });
            // The version change invalidates all sessions at the next service operation.
            // The UI signs out this session after displaying the successful result.
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(replacement, '\0');
        }
    }
}
