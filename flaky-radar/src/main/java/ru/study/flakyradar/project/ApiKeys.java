package ru.study.flakyradar.project;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class ApiKeys {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int KEY_BYTES = 24;

    private ApiKeys() {
    }

    public static String generate() {
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return "frk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 обязателен в любой JVM", e);
        }
    }

    public static boolean matches(String candidate, String expectedHash) {
        if (candidate == null || candidate.isEmpty() || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(candidate).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    public static boolean secretEquals(String candidate, String expected) {
        if (candidate == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
