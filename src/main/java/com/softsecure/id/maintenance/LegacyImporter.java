package com.softsecure.id.maintenance;

import com.softsecure.id.AppServices;
import com.softsecure.id.domain.Profile;
import com.softsecure.id.domain.Role;
import com.softsecure.id.export.ImageFiles;
import com.softsecure.id.persistence.Database;
import com.softsecure.id.persistence.ProfileCodec;
import com.softsecure.id.service.UserException;
import com.softsecure.id.service.Validation;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Isolated read-only legacy CBC decoder. New application writes never use CBC. */
public final class LegacyImporter {
    private final AppServices services;

    public LegacyImporter(AppServices services) {
        this.services = services;
    }

    public void importOne(Role role, String username, Path keyPath, Path ivPath, Charset charset, Path portraitPath) throws Exception {
        if (role != Role.STUDENT && role != Role.FACULTY) {
            throw new UserException("Only student and faculty records can be imported. Create administrators separately.");
        }
        Validation.identifier(username);
        if (Files.size(keyPath) != 32 || Files.size(ivPath) != 16) {
            throw new UserException("Legacy recovery requires the original 32-byte key and 16-byte IV.");
        }
        byte[] key = Files.readAllBytes(keyPath);
        byte[] iv = Files.readAllBytes(ivPath);
        try {
            SecretKeySpec secret = new SecretKeySpec(key, "AES");
            Cipher encoder = Cipher.getInstance("AES/CBC/PKCS5Padding");
            encoder.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(iv));
            String lookup = Base64.getEncoder().encodeToString(encoder.doFinal(username.getBytes(charset)));
            Cipher decoder = Cipher.getInstance("AES/CBC/PKCS5Padding");
            decoder.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
            String[] columns = role == Role.STUDENT
                    ? new String[] {"regno1", "name1", "mode1", "gender1", "blood1", "address1", "phone1", "parentno1", "email1", "bday1", "issuedate1", "pass1"}
                    : new String[] {"facid2", "name2", "dept2", "gender2", "blood2", "address2", "phone2", "altno2", "email2", "bday2", "issuedate2", "pass2"};
            String table = role == Role.STUDENT ? "student" : "faculty";
            String userColumn = role == Role.STUDENT ? "username1" : "username2";
            String[] fields = new String[columns.length];
            // All interpolated identifiers are fixed constants above, and lookup is bound.
            try (var connection = services.database().open(); PreparedStatement query = Database.prepare(connection,
                    "SELECT " + String.join(",", columns) + " FROM " + table + " WHERE " + userColumn + "=? LIMIT 2")) {
                query.setString(1, lookup);
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) { throw new UserException("No matching legacy record was found."); }
                    for (int i = 0; i < fields.length; i++) {
                        byte[] plaintext = decoder.doFinal(Base64.getDecoder().decode(rows.getString(i + 1)));
                        try { fields[i] = new String(plaintext, charset); }
                        finally { Arrays.fill(plaintext, (byte) 0); }
                    }
                    if (rows.next()) { throw new UserException("Duplicate legacy usernames require manual reconciliation."); }
                }
            }
            if (!username.equals(fields[0])) {
                throw new UserException("Legacy username and card identifier differ. Reconcile the source record before import.");
            }
            byte[] photo = portraitPath == null ? new byte[0] : ImageFiles.portrait(portraitPath);
            Profile profile = new Profile(fields[0], fields[1], fields[2], fields[3], blood(fields[4]), fields[5], fields[6],
                    fields[7], fields[8], date(fields[9]), date(fields[10]), photo);
            char[] password = fields[11].toCharArray();
            fields[11] = null;
            String hash;
            try {
                if (password.length == 0 || password.length > 128) {
                    throw new UserException("Legacy password cannot be imported; provision the account with a new password.");
                }
                hash = services.passwords().hash(password);
            } finally { Arrays.fill(password, '\0'); }
            services.database().transaction(connection -> {
                if (services.users().findByUsername(connection, username, false) != null) {
                    throw new UserException("This user ID is already imported or provisioned. Nothing was overwritten.");
                }
                String owner = services.users().insert(connection, username, hash, role);
                String id = UUID.randomUUID().toString();
                byte[] plaintext = ProfileCodec.encode(profile);
                try (PreparedStatement insert = Database.prepare(connection,
                        "INSERT INTO ss_requests(id,user_id,profile_enc,submitted_by) VALUES(?,?,?,?)")) {
                    insert.setString(1, id);
                    insert.setString(2, owner);
                    insert.setBytes(3, services.crypto().encrypt(plaintext, "profile:" + id));
                    insert.setString(4, owner);
                    insert.executeUpdate();
                } finally { Arrays.fill(plaintext, (byte) 0); }
                Database.audit(connection, owner, "LEGACY_IMPORT_OPERATOR", id);
                return null;
            });
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(iv, (byte) 0);
        }
    }

    private static String blood(String value) {
        int start = value.indexOf('(');
        int end = value.indexOf(')');
        return start >= 0 && end > start ? value.substring(start + 1, end) : value.strip();
    }

    private static LocalDate date(String value) {
        for (String pattern : new String[] {"uuuu-MM-dd", "d MMM uuuu", "dd-MMM-uuuu", "MMM d, uuuu"}) {
            try {
                return LocalDate.parse(value.strip(), DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT));
            } catch (DateTimeParseException ignored) {
                // Try only explicit, unambiguous formats; do not guess day/month ordering.
            }
        }
        throw new UserException("Unsupported legacy date format. Normalize source dates in a recovery copy before importing.");
    }
}
