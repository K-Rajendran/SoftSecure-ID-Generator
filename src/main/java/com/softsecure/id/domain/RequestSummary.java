package com.softsecure.id.domain;

import java.time.Instant;

/** Queue rows deliberately contain no decrypted profiles or photo blobs. */
public record RequestSummary(String id, Role role, Status status, int version, Instant submittedAt) { }
