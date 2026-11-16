package ru.study.flakyradar.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Превращает текст ошибки в устойчивую подпись: одно и то же падение в разных прогонах
 * должно давать одинаковый хэш, хотя порты, id, время и адреса объектов каждый раз разные.
 *
 * <p>Подпись = тип исключения + первая строка сообщения + до 3 первых кадров стека не из фреймворков.
 * Номера строк в кадрах отбрасываются: иначе любая правка файла теста порождала бы новую подпись.
 */
public final class SignatureNormalizer {

    static final int MAX_FRAMES = 3;
    private static final int MAX_MESSAGE_LENGTH = 500;

    /** Правила применяются по порядку: сначала самые специфичные (UUID раньше чисел, дата раньше времени). */
    private static final List<Rule> RULES = List.of(
            new Rule("\\u001B\\[[;\\d]*[A-Za-z]", ""),                                       // ANSI-цвета Playwright/pytest
            new Rule("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", "<UUID>"),
            new Rule("\\b\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2}([.,]\\d+)?)?(Z|[+-]\\d{2}:?\\d{2})?", "<TS>"),
            new Rule("\\b\\d{4}-\\d{2}-\\d{2}\\b", "<DATE>"),
            new Rule("\\b\\d{1,2}:\\d{2}:\\d{2}([.,]\\d+)?\\b", "<TIME>"),
            new Rule("(?i)\\b0x[0-9a-f]+\\b", "<HEX>"),
            new Rule("@[0-9a-f]{5,}\\b", "@<HEX>"),                                          // Object.toString(): Foo@1b6d3586
            new Rule("(?i)\\b(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|\\[::1\\]|host\\.docker\\.internal):\\d{2,5}\\b", "$1:<PORT>"),
            new Rule("(?i)(://[\\w.-]+):\\d{2,5}\\b", "$1:<PORT>"),
            new Rule("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", "<IP>"),
            new Rule("(?i)(/tmp/|/var/folders/|[a-z]:\\\\users\\\\[^\\\\\\s]+\\\\appdata\\\\local\\\\temp\\\\)\\S*", "<TMP>"),
            new Rule("(?i)\\b\\d+([.,]\\d+)?\\s?(ms|milliseconds?|sec|seconds?|s)\\b", "<DUR>"),
            new Rule("\\b\\d{2,}\\b", "<N>"),
            new Rule("\\s+", " "));

    /** Кадры фреймворков и рантайма не говорят, где именно сломалось, и шумят при обновлении библиотек. */
    private static final List<String> FRAMEWORK_FRAME_PREFIXES = List.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.", "kotlin.", "scala.",
            "org.junit.", "junit.", "org.opentest4j.", "org.assertj.", "org.hamcrest.", "org.mockito.",
            "org.springframework.", "io.restassured.", "org.apache.", "org.codehaus.groovy.", "groovy.",
            "org.gradle.", "worker.org.gradle.", "org.testng.", "net.bytebuddy.", "org.aspectj.", "io.qameta.",
            "node:internal", "node_modules", "_pytest", "pluggy", "unittest");

    private static final Pattern JAVA_FRAME = Pattern.compile("^\\s*at\\s+(.+?)\\s*$");
    private static final Pattern PYTHON_FRAME = Pattern.compile("^(\\S+\\.py):\\d+:?\\s.*$");
    private static final Pattern LINE_NUMBERS = Pattern.compile("(:\\d+)+(?=\\)?$)");
    /** Строка вида «java.lang.IllegalStateException: ...», «Error: ...» или pytest-овская «E   AssertionError: ...». */
    private static final Pattern TYPE_AT_LINE_START = Pattern.compile(
            "^\\s*(?:E\\s+)?((?:[A-Za-z_$][\\w$]*\\.)*[\\w$]*(?:Error|Exception|Failure|Throwable))(?::|$)");
    private static final Pattern PYTEST_PREFIX = Pattern.compile("^\\s*E\\s+");

    public FailureFingerprint fingerprint(String type, String message, String stackTrace) {
        return fingerprint(type, message, stackTrace, null);
    }

    /**
     * @param testClass класс (classname) упавшего теста: его кадры в подпись не входят. Иначе одна и та же
     *                  причина («стенд недоступен») у разных тестов давала бы разные подписи из-за кадра
     *                  с именем тестового метода.
     */
    public FailureFingerprint fingerprint(String type, String message, String stackTrace, String testClass) {
        String safeType = nullToEmpty(type).strip();
        String safeMessage = nullToEmpty(message);
        String safeStack = nullToEmpty(stackTrace);

        String exceptionType;
        String messageSource;
        String typeLine = firstLineWithType(safeStack);
        if (isClassName(safeType)) {
            // Surefire/Gradle: type = класс исключения, message = его сообщение
            exceptionType = safeType;
            messageSource = safeMessage.isBlank() ? firstMeaningfulLine(safeStack) : safeMessage;
        } else if (typeLine != null) {
            // Playwright (type="FAILURE", message = «файл:строка заголовок») и pytest: суть ошибки в теле
            exceptionType = typeOf(typeLine);
            messageSource = PYTEST_PREFIX.matcher(typeLine).replaceFirst("");
        } else {
            String messageType = typeOf(firstMeaningfulLine(safeMessage));
            exceptionType = messageType != null ? messageType : safeType;
            messageSource = safeMessage.isBlank() ? firstMeaningfulLine(safeStack) : safeMessage;
        }

        String messageLine = normalize(truncate(firstMeaningfulLine(messageSource)));
        // сообщение часто начинается с имени исключения: убираем, чтобы не дублировать тип
        if (!exceptionType.isEmpty() && messageLine.startsWith(exceptionType)) {
            messageLine = messageLine.substring(exceptionType.length()).replaceFirst("^:\\s*", "");
        }
        List<String> frames = significantFrames(safeStack, testClass);

        StringBuilder text = new StringBuilder(exceptionType).append('\n').append(messageLine);
        frames.forEach(frame -> text.append("\n  at ").append(frame));
        String normalizedText = text.toString().strip();
        return new FailureFingerprint(exceptionType, normalizedText, sha256(normalizedText));
    }

    /** Нормализация одной строки: применяет все правила по порядку. */
    public String normalize(String text) {
        return applyRules(text);
    }

    private static String applyRules(String text) {
        String result = text;
        for (Rule rule : RULES) {
            result = rule.pattern.matcher(result).replaceAll(rule.replacement);
        }
        return result.strip();
    }

    /**
     * Похоже ли значение атрибута type на класс исключения. Playwright пишет type="FAILURE",
     * pytest пишет type="pytest.skip": это не классы.
     */
    static boolean isClassName(String type) {
        return !type.isBlank()
                && !type.equals(type.toUpperCase(Locale.ROOT))
                && !type.contains(" ")
                && TYPE_AT_LINE_START.matcher(type).find();
    }

    private static String firstLineWithType(String text) {
        for (String line : text.split("\\R")) {
            if (TYPE_AT_LINE_START.matcher(line).find()) {
                return line.strip();
            }
        }
        return null;
    }

    private static String typeOf(String line) {
        Matcher matcher = TYPE_AT_LINE_START.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    static List<String> significantFrames(String stackTrace, String testClass) {
        List<String> frames = new ArrayList<>();
        for (String line : stackTrace.split("\\R")) {
            String frame = extractFrame(line);
            if (frame == null || isFrameworkFrame(frame) || isTestClassFrame(frame, testClass)) {
                continue;
            }
            frames.add(applyRules(frame));
            if (frames.size() == MAX_FRAMES) {
                break;
            }
        }
        return frames;
    }

    private static String extractFrame(String line) {
        Matcher java = JAVA_FRAME.matcher(line);
        if (java.matches()) {
            String frame = java.group(1);
            // «Foo.bar(Foo.java:42)» -> «Foo.bar(Foo.java)», «file.ts:12:5» -> «file.ts»
            frame = frame.replaceAll(":\\d+(?=\\))", "");
            return LINE_NUMBERS.matcher(frame).replaceAll("");
        }
        Matcher python = PYTHON_FRAME.matcher(line);
        if (python.matches()) {
            return python.group(1);
        }
        return null;
    }

    private static boolean isTestClassFrame(String frame, String testClass) {
        if (testClass == null || testClass.isBlank()) {
            return false;
        }
        return frame.startsWith(testClass + ".") || frame.startsWith(testClass + "$");
    }

    private static boolean isFrameworkFrame(String frame) {
        String stripped = frame.startsWith("async ") ? frame.substring(6) : frame;
        for (String prefix : FRAMEWORK_FRAME_PREFIXES) {
            if (stripped.startsWith(prefix) || stripped.contains("(" + prefix) || stripped.contains("/" + prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String firstMeaningfulLine(String text) {
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                return line.strip();
            }
        }
        return "";
    }

    private static String truncate(String text) {
        return text.length() <= MAX_MESSAGE_LENGTH ? text : text.substring(0, MAX_MESSAGE_LENGTH);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 обязателен в любой JVM", e);
        }
    }

    private record Rule(Pattern pattern, String replacement) {
        Rule(String regex, String replacement) {
            this(Pattern.compile(regex), replacement);
        }
    }
}
