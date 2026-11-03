package com.softsecure.id.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    private static final int ITERATIONS = 600_000;
    private final SecureRandom random = new SecureRandom();

    public String hash(char[] password) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS);
        try {
            return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                    + "$" + Base64.getEncoder().encodeToString(hash);
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    public boolean verify(char[] password, String encoded) throws GeneralSecurityException {
        try {
            String[] parts = encoded.split("\\$", -1);
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (iterations < ITERATIONS || iterations > 2_000_000 || salt.length != 16 || expected.length != 32) {
                return false;
            }
            byte[] actual = derive(password, salt, iterations);
            try {
                return MessageDigest.isEqual(actual, expected);
            } finally {
                Arrays.fill(actual, (byte) 0);
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }

    private byte[] derive(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec specification = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).getEncoded();
        } finally {
            specification.clearPassword();
        }
    }
}
