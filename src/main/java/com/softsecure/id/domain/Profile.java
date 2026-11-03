package com.softsecure.id.domain;

import java.time.LocalDate;

/** Personal details and a normalized PNG portrait; never put this record in logs. */
public record Profile(String identifier, String name, String department, String gender,
                      String bloodGroup, String address, String phone, String alternatePhone,
                      String email, LocalDate birthDate, LocalDate issueDate, byte[] photo) {
    public Profile {
        photo = photo == null ? new byte[0] : photo.clone();
    }

    @Override
    public byte[] photo() {
        return photo.clone();
    }

    @Override
    public String toString() {
        return "Profile[redacted]";
    }
}
