package com.softsecure.id.security;

import com.softsecure.id.domain.Role;
import com.softsecure.id.service.UserException;
import java.time.Duration;

/** Constructor is package-private: sessions are issued only by authentication. */
public final class Session {
    private final String userId;
    private final String username;
    private final Role role;
    private final int version;
    private final long created = System.nanoTime();
    private final long idleNanos;
    private final long maxNanos;
    private long lastActivity = created;
    private boolean active = true;

    Session(String userId, String username, Role role, int version, Duration idle, Duration maximum) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.version = version;
        idleNanos = idle.toNanos();
        maxNanos = maximum.toNanos();
    }

    public synchronized boolean isActive() {
        long now = System.nanoTime();
        active &= now - lastActivity < idleNanos && now - created < maxNanos;
        return active;
    }

    public synchronized void requireActive() {
        if (!isActive()) {
            throw new UserException("Your session has expired. Sign in again.");
        }
    }

    public synchronized void touch() {
        requireActive();
        lastActivity = System.nanoTime();
    }

    public synchronized void close() {
        active = false;
    }

    public String userId() { return userId; }
    public String username() { return username; }
    public Role role() { return role; }
    public int version() { return version; }
}
