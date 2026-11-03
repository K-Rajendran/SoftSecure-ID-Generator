package com.softsecure.id.security;

import com.softsecure.id.config.AppConfig;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Versioned AES-256-GCM envelope: version byte, random 96-bit nonce, ciphertext/tag. */
public final class Crypto {
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec lookupKey;
    private final SecureRandom random = new SecureRandom();

    public Crypto(AppConfig config) {
        byte[] encryption = Base64.getDecoder().decode(config.required("security.encryptionKey"));
        byte[] lookup = Base64.getDecoder().decode(config.required("security.lookupKey"));
        try {
            if (encryption.length != 32 || lookup.length != 32 || MessageDigest.isEqual(encryption, lookup)) {
                throw new IllegalArgumentException("Configure two distinct random 32-byte keys.");
            }
            encryptionKey = new SecretKeySpec(encryption, "AES");
            lookupKey = new SecretKeySpec(lookup, "HmacSHA256");
        } finally {
            Arrays.fill(encryption, (byte) 0);
            Arrays.fill(lookup, (byte) 0);
        }
    }

    public byte[] encrypt(byte[] plaintext, String context) throws GeneralSecurityException {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(plaintext);
        return ByteBuffer.allocate(13 + ciphertext.length).put((byte) 1).put(nonce).put(ciphertext).array();
    }

    public byte[] decrypt(byte[] envelope, String context) throws GeneralSecurityException {
        if (envelope == null || envelope.length < 29 || envelope[0] != 1) {
            throw new GeneralSecurityException("Invalid encrypted record");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, envelope, 1, 12));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(envelope, 13, envelope.length - 13);
    }

    public String encryptText(String plaintext, String context) throws GeneralSecurityException {
        byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
        try {
            return Base64.getEncoder().encodeToString(encrypt(bytes, context));
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public String decryptText(String encoded, String context) throws GeneralSecurityException {
        byte[] bytes = decrypt(Base64.getDecoder().decode(encoded), context);
        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public String usernameTag(String username) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(lookupKey);
        return HexFormat.of().formatHex(mac.doFinal(("username:" + username).getBytes(StandardCharsets.UTF_8)));
    }

    public String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String tokenHash(String token) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
    }
}
