package com.softsecure.id.maintenance;

import com.softsecure.id.AppServices;
import com.softsecure.id.domain.Role;
import com.softsecure.id.persistence.Database;
import com.softsecure.id.service.UserException;
import com.softsecure.id.service.Validation;
import java.io.Console;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.Arrays;

/** Trusted operator tool. Requires a real console and explicitly provisioned DB credentials. */
public final class Maintenance {
    private Maintenance() { }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Use a real terminal. Password input is never accepted as a command-line argument.");
            return;
        }
        if (args.length != 1 || !java.util.Set.of("create-admin", "reset-password", "disable-user", "enable-user", "import-legacy").contains(args[0])) {
            console.printf("Usage: Maintenance create-admin | reset-password | disable-user | enable-user | import-legacy%n");
            return;
        }
        try {
            AppServices services = AppServices.load();
            String username = Validation.identifier(line(console, "User ID: "));
            switch (args[0]) {
                case "create-admin" -> createAdmin(services, console, username);
                case "reset-password" -> resetPassword(services, console, username);
                case "disable-user", "enable-user" -> setEnabled(services, username, args[0].equals("enable-user"));
                case "import-legacy" -> {
                    Role role = Role.valueOf(line(console, "Legacy role (STUDENT/FACULTY): ").toUpperCase(java.util.Locale.ROOT));
                    Path key = Path.of(line(console, "Original key file path: "));
                    Path iv = Path.of(line(console, "Original IV file path: "));
                    Charset charset = Charset.forName(line(console, "Original Java default charset (for example windows-1252 or UTF-8): "));
                    String photo = line(console, "Portrait file path (blank to import without portrait): ");
                    new LegacyImporter(services).importOne(role, username, key, iv, charset, photo.isEmpty() ? null : Path.of(photo));
                }
                default -> throw new IllegalStateException("Unsupported operation");
            }
            console.printf("Operation committed successfully.%n");
        } catch (Exception exception) {
            if (exception instanceof UserException) { console.printf("%s%n", exception.getMessage()); }
            else { console.printf("Operation failed (%s). Check configuration, keys and schema; inspect database state before retrying.%n", exception.getClass().getSimpleName()); }
        }
    }

    private static String line(Console console, String prompt) {
        String value = console.readLine("%s", prompt);
        if (value == null) { throw new UserException("Operation cancelled."); }
        return value.strip();
    }

    private static char[] password(Console console) {
        char[] first = console.readPassword("New password (12–128 characters): ");
        char[] second = console.readPassword("Confirm password: ");
        boolean accepted = false;
        try {
            Validation.password(first);
            if (!Arrays.equals(first, second)) { throw new UserException("Passwords do not match."); }
            accepted = true;
            return first;
        } finally {
            if (second != null) { Arrays.fill(second, '\0'); }
            if (!accepted && first != null) { Arrays.fill(first, '\0'); }
        }
    }

    private static void createAdmin(AppServices services, Console console, String username) throws Exception {
        char[] password = password(console);
        try {
            String hash = services.passwords().hash(password);
            services.database().transaction(connection -> {
                if (services.users().findByUsername(connection, username, false) != null) {
                    throw new UserException("User ID already exists.");
                }
                String id = services.users().insert(connection, username, hash, Role.ADMIN);
                Database.audit(connection, id, "CREATE_ADMIN_OPERATOR", id);
                return null;
            });
        } finally { Arrays.fill(password, '\0'); }
    }

    private static void resetPassword(AppServices services, Console console, String username) throws Exception {
        char[] password = password(console);
        try {
            String hash = services.passwords().hash(password);
            services.database().transaction(connection -> {
                var account = services.users().findByUsername(connection, username, true);
                if (account == null) { throw new UserException("Account not found."); }
                try (PreparedStatement update = Database.prepare(connection,
                        "UPDATE ss_users SET password_hash=?,session_version=session_version+1,failed_attempts=0,locked_until=NULL WHERE id=?")) {
                    update.setString(1, hash);
                    update.setString(2, account.id());
                    update.executeUpdate();
                }
                Database.audit(connection, account.id(), "RESET_PASSWORD_OPERATOR", account.id());
                return null;
            });
        } finally { Arrays.fill(password, '\0'); }
    }

    private static void setEnabled(AppServices services, String username, boolean enabled) throws Exception {
        services.database().transaction(connection -> {
            var account = services.users().findByUsername(connection, username, true);
            if (account == null) { throw new UserException("Account not found."); }
            try (PreparedStatement update = Database.prepare(connection,
                    "UPDATE ss_users SET enabled=?,session_version=session_version+1 WHERE id=?")) {
                update.setBoolean(1, enabled);
                update.setString(2, account.id());
                update.executeUpdate();
            }
            Database.audit(connection, account.id(), enabled ? "ENABLE_USER_OPERATOR" : "DISABLE_USER_OPERATOR", account.id());
            return null;
        });
    }
}
