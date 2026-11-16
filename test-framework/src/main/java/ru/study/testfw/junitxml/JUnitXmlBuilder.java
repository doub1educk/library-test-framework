package ru.study.testfw.junitxml;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Генератор отчётов JUnit XML для тестов. Тест описывает только суть («этот тест упал с такой ошибкой»),
 * а builder собирает корректный XML в формате Surefire.
 *
 * <pre>{@code
 * String xml = JUnitXmlBuilder.report()
 *         .passed("ru.demo.LoginTest", "opensPage")
 *         .failed("ru.demo.LoginTest", "logsIn", "java.lang.AssertionError", "expected 200 but was 500")
 *         .flakyPassed("ru.demo.CartTest", "addsItem", "java.net.SocketTimeoutException", "Read timed out")
 *         .toXml();
 * }</pre>
 */
public final class JUnitXmlBuilder {

    private final Map<String, List<Case>> suites = new LinkedHashMap<>();
    private double defaultDuration = 0.05;

    private JUnitXmlBuilder() {
    }

    public static JUnitXmlBuilder report() {
        return new JUnitXmlBuilder();
    }

    public JUnitXmlBuilder withDefaultDuration(double seconds) {
        this.defaultDuration = seconds;
        return this;
    }

    public JUnitXmlBuilder passed(String suite, String name) {
        return add(suite, new Case(name, Kind.PASSED, null, null, defaultDuration));
    }

    public JUnitXmlBuilder failed(String suite, String name, String type, String message) {
        return add(suite, new Case(name, Kind.FAILED, type, message, defaultDuration));
    }

    public JUnitXmlBuilder error(String suite, String name, String type, String message) {
        return add(suite, new Case(name, Kind.ERROR, type, message, defaultDuration));
    }

    public JUnitXmlBuilder skipped(String suite, String name) {
        return add(suite, new Case(name, Kind.SKIPPED, null, "skipped", defaultDuration));
    }

    /** Surefire с rerunFailingTestsCount: сначала упал, при повторе прошёл ({@code <flakyFailure>}). */
    public JUnitXmlBuilder flakyPassed(String suite, String name, String type, String message) {
        return add(suite, new Case(name, Kind.FLAKY, type, message, defaultDuration));
    }

    /** Surefire с rerunFailingTestsCount: упал и при всех повторах ({@code <failure>} + {@code <rerunFailure>}). */
    public JUnitXmlBuilder rerunFailed(String suite, String name, String type, String message) {
        return add(suite, new Case(name, Kind.RERUN_FAILED, type, message, defaultDuration));
    }

    private JUnitXmlBuilder add(String suite, Case testCase) {
        suites.computeIfAbsent(suite, s -> new ArrayList<>()).add(testCase);
        return this;
    }

    public byte[] toBytes() {
        return toXml().getBytes(StandardCharsets.UTF_8);
    }

    public String toXml() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuites>\n");
        suites.forEach((suite, cases) -> {
            long failures = cases.stream().filter(c -> c.kind == Kind.FAILED || c.kind == Kind.RERUN_FAILED).count();
            long errors = cases.stream().filter(c -> c.kind == Kind.ERROR).count();
            long skipped = cases.stream().filter(c -> c.kind == Kind.SKIPPED).count();
            xml.append("  <testsuite name=\"").append(escape(suite))
                    .append("\" tests=\"").append(cases.size())
                    .append("\" failures=\"").append(failures)
                    .append("\" errors=\"").append(errors)
                    .append("\" skipped=\"").append(skipped).append("\">\n");
            cases.forEach(c -> appendCase(xml, suite, c));
            xml.append("  </testsuite>\n");
        });
        return xml.append("</testsuites>\n").toString();
    }

    private static void appendCase(StringBuilder xml, String suite, Case c) {
        xml.append("    <testcase name=\"").append(escape(c.name))
                .append("\" classname=\"").append(escape(suite))
                .append("\" time=\"").append(String.format(Locale.ROOT, "%.3f", c.duration)).append("\"");
        if (c.kind == Kind.PASSED) {
            xml.append("/>\n");
            return;
        }
        xml.append(">\n");
        switch (c.kind) {
            case FAILED -> appendProblem(xml, "failure", suite, c);
            case ERROR -> appendProblem(xml, "error", suite, c);
            case SKIPPED -> xml.append("      <skipped message=\"").append(escape(c.message)).append("\"/>\n");
            case FLAKY -> appendNested(xml, "flakyFailure", suite, c);
            case RERUN_FAILED -> {
                appendProblem(xml, "failure", suite, c);
                appendNested(xml, "rerunFailure", suite, c);
            }
            default -> throw new IllegalStateException(c.kind.name());
        }
        xml.append("    </testcase>\n");
    }

    private static void appendProblem(StringBuilder xml, String tag, String suite, Case c) {
        xml.append("      <").append(tag).append(" message=\"").append(escape(c.message))
                .append("\" type=\"").append(escape(c.type)).append("\">")
                .append(escape(stackTrace(suite, c))).append("</").append(tag).append(">\n");
    }

    private static void appendNested(StringBuilder xml, String tag, String suite, Case c) {
        xml.append("      <").append(tag).append(" message=\"").append(escape(c.message))
                .append("\" type=\"").append(escape(c.type)).append("\">\n")
                .append("        <stackTrace>").append(escape(stackTrace(suite, c))).append("</stackTrace>\n")
                .append("      </").append(tag).append(">\n");
    }

    /** Стек как у настоящего Surefire: исключение, затем кадр тестового метода. */
    private static String stackTrace(String suite, Case c) {
        String simpleName = suite.substring(suite.lastIndexOf('.') + 1);
        return c.type + ": " + c.message
                + "\n\tat " + suite + "." + c.name.replaceAll("\\W", "_") + "(" + simpleName + ".java:42)";
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }

    private enum Kind { PASSED, FAILED, ERROR, SKIPPED, FLAKY, RERUN_FAILED }

    private record Case(String name, Kind kind, String type, String message, double duration) {
    }
}
