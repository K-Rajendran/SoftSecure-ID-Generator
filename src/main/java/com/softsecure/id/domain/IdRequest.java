package com.softsecure.id.domain;

public record IdRequest(String id, String userId, Role role, Profile profile, Status status,
                        int version, String reason, String token) {
    @Override
    public String toString() {
        return "IdRequest[redacted]";
    }
}
