package com.softsecure.id.service;

import com.softsecure.id.domain.Profile;
import java.time.LocalDate;
import java.util.Set;

public final class Validation {
    public static final String[] BLOOD_GROUPS = {"A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"};
    public static final String[] GENDERS = {"Female", "Male", "Other", "Prefer not to say"};

    private Validation() { }

    public static String identifier(String value) {
        String result = text(value, "User ID", 64);
        if (!result.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new UserException("User ID must use letters, numbers, dots, underscores or hyphens.");
        }
        return result;
    }

    public static String text(String value, String label, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n')) {
            throw new UserException(label + " is required and must be at most " + maximum + " characters.");
        }
        return value.strip();
    }

    public static void password(char[] password) {
        if (password == null || password.length < 12 || password.length > 128) {
            throw new UserException("Use a password or passphrase of 12 to 128 characters.");
        }
    }

    public static void profile(Profile profile) {
        identifier(profile.identifier());
        text(profile.name(), "Name", 100);
        text(profile.department(), "Department / study mode", 80);
        text(profile.address(), "Address", 500);
        if (profile.gender() == null || profile.bloodGroup() == null
                || !Set.of(GENDERS).contains(profile.gender()) || !Set.of(BLOOD_GROUPS).contains(profile.bloodGroup())) {
            throw new UserException("Choose a gender and a valid blood group.");
        }
        for (String phone : new String[] {profile.phone(), profile.alternatePhone()}) {
            if (phone == null || !phone.matches("\\+?[0-9 ()-]{7,24}")
                    || phone.replaceAll("[^0-9]", "").length() < 7) {
                throw new UserException("Phone numbers must contain 7 to 24 characters, including at least 7 digits.");
            }
        }
        if (profile.email() == null || profile.email().length() > 254
                || !profile.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new UserException("Enter a valid email address.");
        }
        LocalDate today = LocalDate.now();
        if (profile.birthDate() == null || !profile.birthDate().isBefore(today)
                || profile.birthDate().isBefore(today.minusYears(130))) {
            throw new UserException("Birth date must be in the past and within the last 130 years.");
        }
        if (profile.issueDate() == null || profile.issueDate().isBefore(profile.birthDate())
                || profile.issueDate().isAfter(today)) {
            throw new UserException("Issue date must be between birth date and today.");
        }
        if (profile.photo().length == 0 || profile.photo().length > 2_000_000) {
            throw new UserException("Select a portrait image (normalized PNG, maximum 2 MB).");
        }
    }
}
