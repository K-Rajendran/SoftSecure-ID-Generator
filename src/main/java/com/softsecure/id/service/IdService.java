package com.softsecure.id.service;

import com.softsecure.id.config.AppConfig;
import com.softsecure.id.domain.IdRequest;
import com.softsecure.id.domain.Profile;
import com.softsecure.id.domain.RequestSummary;
import com.softsecure.id.domain.Role;
import com.softsecure.id.domain.Status;
import com.softsecure.id.persistence.Database;
import com.softsecure.id.persistence.ProfileCodec;
import com.softsecure.id.persistence.RequestRepository;
import com.softsecure.id.persistence.UserRepository;
import com.softsecure.id.security.Crypto;
import com.softsecure.id.security.Session;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Authoritative workflow boundary. Hiding a button is never an authorization check. */
public final class IdService {
    private final Database database;
    private final UserRepository users;
    private final RequestRepository requests;
    private final Crypto crypto;
    private final boolean facultyLookup;

    public IdService(Database database, UserRepository users, RequestRepository requests, Crypto crypto, AppConfig config) {
        this.database = database;
        this.users = users;
        this.requests = requests;
        this.crypto = crypto;
        facultyLookup = config.flag("privacy.facultyLookup");
    }

    public boolean facultyLookupEnabled() { return facultyLookup; }

    public IdRequest own(Session session) throws Exception {
        return database.transaction(connection -> {
            users.authorize(connection, session, Role.STUDENT, Role.FACULTY);
            return requests.byOwner(connection, session.userId());
        });
    }

    public IdRequest inspect(Session session, String requestId) throws Exception {
        return database.transaction(connection -> {
            users.authorize(connection, session);
            IdRequest request = requests.byId(connection, requestId);
            if (request == null || (session.role() != Role.ADMIN && !request.userId().equals(session.userId()))) {
                throw new UserException("Request is not available to your account.");
            }
            return request;
        });
    }

    public IdRequest lookupStudent(Session session, String identifier) throws Exception {
        String name = Validation.identifier(identifier);
        return database.transaction(connection -> {
            users.authorize(connection, session, Role.ADMIN, Role.FACULTY);
            if (session.role() == Role.FACULTY && !facultyLookup) {
                throw new UserException("Faculty student lookup is disabled by institutional policy.");
            }
            var account = users.findByUsername(connection, name, false);
            IdRequest request = account == null || account.role() != Role.STUDENT || !account.enabled()
                    ? null : requests.byOwner(connection, account.id());
            if (request == null || request.status() != Status.APPROVED) {
                throw new UserException("No approved student card was found.");
            }
            Database.audit(connection, session.userId(), "STUDENT_LOOKUP", request.id());
            // Faculty may view the card, but do not receive the QR bearer token.
            return new IdRequest(request.id(), request.userId(), request.role(), request.profile(),
                    request.status(), request.version(), "", "");
        });
    }

    public void submit(Session session, Profile profile, int expectedVersion) throws Exception {
        Validation.profile(profile);
        database.transaction(connection -> {
            users.authorize(connection, session);
            var owner = users.findByUsername(connection, profile.identifier(), true);
            if (owner == null || !owner.enabled() || owner.role() == Role.ADMIN) {
                throw new UserException("Create a student or faculty account for this ID before submitting.");
            }
            if (session.role() != Role.ADMIN && !session.userId().equals(owner.id())) {
                throw new UserException("You may submit only your own details.");
            }
            IdRequest previous = requests.byOwner(connection, owner.id());
            if ((previous == null ? 0 : previous.version()) != expectedVersion) {
                throw new UserException("This request changed. Refresh it before submitting again.");
            }
            if (previous != null && previous.status() != Status.REJECTED) {
                throw new UserException("Only rejected requests can be edited. Ask an administrator to revoke an approved card first.");
            }
            String id = previous == null ? UUID.randomUUID().toString() : previous.id();
            byte[] plaintext = ProfileCodec.encode(profile);
            byte[] encrypted;
            try {
                encrypted = crypto.encrypt(plaintext, "profile:" + id);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
            String sql = previous == null
                    ? "INSERT INTO ss_requests(profile_enc,submitted_by,id,user_id) VALUES(?,?,?,?)"
                    : "UPDATE ss_requests SET profile_enc=?,submitted_by=?,status='PENDING',version=version+1,"
                        + "reviewed_by=NULL,reason_enc=NULL,token_hash=NULL,token_enc=NULL,submitted_at=CURRENT_TIMESTAMP,"
                        + "reviewed_at=NULL WHERE id=? AND user_id=?";
            try (PreparedStatement statement = Database.prepare(connection, sql)) {
                statement.setBytes(1, encrypted);
                statement.setString(2, session.userId());
                statement.setString(3, id);
                statement.setString(4, owner.id());
                statement.executeUpdate();
            }
            session.requireActive();
            Database.audit(connection, session.userId(), "SUBMIT", id);
            return null;
        });
    }

    public List<RequestSummary> queue(Session session, Status status, int offset) throws Exception {
        if (offset < 0 || offset > 1_000_000 || status == null) {
            throw new UserException("Invalid queue page.");
        }
        return database.transaction(connection -> {
            users.authorize(connection, session, Role.ADMIN);
            return requests.queue(connection, status, offset);
        });
    }

    /** Approval/rejection/revocation, audit, and token lifecycle commit together. */
    public void review(Session session, String id, int expectedVersion, boolean approve, String reason) throws Exception {
        String explanation = approve ? "Approved" : Validation.text(reason, "Review reason", 500);
        database.transaction(connection -> {
            users.authorize(connection, session, Role.ADMIN);
            IdRequest request = requests.byId(connection, id);
            if (request == null || request.version() != expectedVersion) {
                throw new UserException("This request changed. Refresh the queue and review it again.");
            }
            if (request.status() != Status.PENDING && (approve || request.status() != Status.APPROVED)) {
                throw new UserException("Only pending requests can be approved or rejected; approved cards may be revoked.");
            }
            try (PreparedStatement statement = Database.prepare(connection, "SELECT submitted_by FROM ss_requests WHERE id=?")) {
                statement.setString(1, id);
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    if (approve && session.userId().equals(rows.getString(1))) {
                        throw new UserException("A second administrator must approve a request you submitted.");
                    }
                }
            }
            if (approve) {
                // Imported records may need corrected dates, fields or a portrait before approval.
                Validation.profile(request.profile());
            }
            String token = approve ? crypto.randomToken() : null;
            try (PreparedStatement update = Database.prepare(connection,
                    "UPDATE ss_requests SET status=?,version=version+1,reviewed_by=?,reason_enc=?,token_hash=?,"
                            + "token_enc=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND version=?")) {
                update.setString(1, approve ? "APPROVED" : "REJECTED");
                update.setString(2, session.userId());
                update.setString(3, crypto.encryptText(explanation, "reason:" + id));
                update.setString(4, token == null ? null : Crypto.tokenHash(token));
                update.setString(5, token == null ? null : crypto.encryptText(token, "token:" + id));
                update.setString(6, id);
                update.setInt(7, expectedVersion);
                if (update.executeUpdate() != 1) {
                    throw new UserException("The review was not saved. Refresh and retry.");
                }
            }
            session.requireActive();
            Database.audit(connection, session.userId(), approve ? "APPROVE"
                    : request.status() == Status.APPROVED ? "REVOKE" : "REJECT", id);
            return null;
        });
    }

    public IdRequest approvedForExport(Session session, String id) throws Exception {
        return database.transaction(connection -> {
            users.authorize(connection, session);
            IdRequest request = requests.byId(connection, id);
            if (request == null || request.status() != Status.APPROVED
                    || (session.role() != Role.ADMIN && !session.userId().equals(request.userId()))) {
                throw new UserException("Only an approved card owned by you, or reviewed by an administrator, can be exported.");
            }
            var owner = users.findById(connection, request.userId(), false);
            if (owner == null || !owner.enabled()) {
                throw new UserException("This card's account is inactive.");
            }
            Database.audit(connection, session.userId(), "AUTHORIZE_EXPORT", id);
            return request;
        });
    }

    public String verify(Session session, String payload) throws Exception {
        if (payload == null || !payload.matches("SSID1\\.[A-Za-z0-9_-]{43}")) {
            throw new UserException("This is not a supported SoftSecure QR code.");
        }
        String hash = Crypto.tokenHash(payload.substring(6));
        return database.transaction(connection -> {
            users.authorize(connection, session);
            try (PreparedStatement query = Database.prepare(connection,
                    "SELECT r.id,u.role FROM ss_requests r JOIN ss_users u ON u.id=r.user_id "
                            + "WHERE r.token_hash=? AND r.status='APPROVED' AND u.enabled=TRUE")) {
                query.setString(1, hash);
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) {
                        throw new UserException("Card is unknown, revoked, or no longer approved.");
                    }
                    String id = rows.getString(1);
                    Database.audit(connection, session.userId(), "VERIFY", id);
                    IdRequest request = requests.byId(connection, id);
                    if (request == null || request.status() != Status.APPROVED || request.token().isEmpty()
                            || !Crypto.tokenHash(request.token()).equals(hash)) {
                        throw new UserException("Card is no longer approved. Request a fresh verification.");
                    }
                    return "Approved " + rows.getString(2).toLowerCase(java.util.Locale.ROOT) + " card\n"
                            + request.profile().name() + "\nID: " + request.profile().identifier()
                            + "\nCompare this identity and the portrait with the card holder.";
                }
            }
        });
    }
}
