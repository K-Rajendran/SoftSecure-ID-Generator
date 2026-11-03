package com.softsecure.id.persistence;

import com.softsecure.id.domain.IdRequest;
import com.softsecure.id.domain.RequestSummary;
import com.softsecure.id.domain.Role;
import com.softsecure.id.domain.Status;
import com.softsecure.id.security.Crypto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RequestRepository {
    private final Crypto crypto;

    public RequestRepository(Crypto crypto) {
        this.crypto = crypto;
    }

    public IdRequest byOwner(Connection connection, String owner) throws Exception {
        return find(connection, "r.user_id", owner);
    }

    public IdRequest byId(Connection connection, String id) throws Exception {
        return find(connection, "r.id", id);
    }

    private IdRequest find(Connection connection, String column, String value) throws Exception {
        try (PreparedStatement statement = Database.prepare(connection,
                "SELECT r.*,u.role FROM ss_requests r JOIN ss_users u ON u.id=r.user_id WHERE " + column + "=? FOR UPDATE")) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                String id = rows.getString("id");
                byte[] plaintext = crypto.decrypt(rows.getBytes("profile_enc"), "profile:" + id);
                try {
                    String reason = rows.getString("reason_enc");
                    String token = rows.getString("token_enc");
                    return new IdRequest(id, rows.getString("user_id"), Role.valueOf(rows.getString("role")),
                            ProfileCodec.decode(plaintext), Status.valueOf(rows.getString("status")), rows.getInt("version"),
                            reason == null ? "" : crypto.decryptText(reason, "reason:" + id),
                            token == null ? "" : crypto.decryptText(token, "token:" + id));
                } finally {
                    Arrays.fill(plaintext, (byte) 0);
                }
            }
        }
    }

    public List<RequestSummary> queue(Connection connection, Status status, int offset) throws Exception {
        List<RequestSummary> result = new ArrayList<>();
        try (PreparedStatement statement = Database.prepare(connection,
                "SELECT r.id,u.role,r.status,r.version,r.submitted_at FROM ss_requests r "
                        + "JOIN ss_users u ON u.id=r.user_id WHERE r.status=? ORDER BY r.submitted_at,r.id LIMIT 50 OFFSET ?")) {
            statement.setString(1, status.name());
            statement.setInt(2, offset);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new RequestSummary(rows.getString(1), Role.valueOf(rows.getString(2)),
                            Status.valueOf(rows.getString(3)), rows.getInt(4), rows.getTimestamp(5).toInstant()));
                }
            }
        }
        return List.copyOf(result);
    }
}
