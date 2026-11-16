package ru.study.flakyradar.framework;

import net.datafaker.Faker;

import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class RadarTestData {
    public static final String ADMIN_TOKEN = "test-admin-token";
    public static final String PUBLIC_URL = "http://radar.test";

    public static final String SUITE = "ru.demo.CheckoutTest";
    public static final String ASSERTION = "org.opentest4j.AssertionFailedError";
    public static final String TIMEOUT = "java.net.SocketTimeoutException";

    private static final Faker FAKER = new Faker(Locale.ENGLISH);

    private RadarTestData() {
    }

    public static String projectKey() {
        return "proj-" + FAKER.lorem().characters(8, false, false).toLowerCase(Locale.ROOT);
    }

    public static String commitSha() {
        byte[] bytes = new byte[20];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String ciRunId() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000, 9_999_999));
    }

    public static String fullName(String test) {
        return SUITE + "." + test;
    }
}
