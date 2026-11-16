package ru.study.flakyradar.report;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Потоковый разбор JUnit XML (Surefire/Gradle, Playwright, pytest и т.п.) на StAX.
 *
 * <p>Поддерживается:
 * <ul>
 *     <li>корень {@code <testsuites>} или {@code <testsuite>}, вложенные testsuite;</li>
 *     <li>{@code <failure>}, {@code <error>}, {@code <skipped>};</li>
 *     <li>Surefire-ретраи: {@code <flakyFailure>/<flakyError>} (в итоге прошёл)
 *         и {@code <rerunFailure>/<rerunError>} (упал во всех повторах);</li>
 *     <li>повтор одного и того же теста внутри отчёта: выполнения сливаются, итог по последнему.</li>
 * </ul>
 *
 * <p>DTD и внешние сущности выключены, поэтому XXE-атаки не работают. Класс без состояния и потокобезопасен.
 */
public final class JUnitXmlParser {

    private static final XMLInputFactory FACTORY = createFactory();

    public ParsedReport parse(InputStream input) {
        try {
            XMLStreamReader reader = FACTORY.createXMLStreamReader(input);
            try {
                return read(reader);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new ReportParseException("Отчёт не является корректным XML: " + firstLine(e.getMessage()), e);
        }
    }

    private ParsedReport read(XMLStreamReader reader) throws XMLStreamException {
        Deque<String> suites = new ArrayDeque<>();
        Map<String, ParsedTestCase> cases = new LinkedHashMap<>();
        boolean rootSeen = false;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String element = reader.getLocalName();
                if (!rootSeen && !element.equals("testsuites") && !element.equals("testsuite")) {
                    throw new ReportParseException(
                            "Корневой элемент должен быть <testsuites> или <testsuite>, а не <" + element + ">");
                }
                rootSeen = true;
                switch (element) {
                    case "testsuite" -> suites.push(attr(reader, "name"));
                    case "testcase" -> {
                        ParsedTestCase tc = readTestCase(reader, suites.isEmpty() ? "" : suites.peek());
                        cases.merge(tc.fullName(), tc, ParsedReport::combine);
                    }
                    default -> {
                        // testsuites, properties и прочее: идём глубже
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals("testsuite")) {
                suites.pop();
            }
        }
        if (!rootSeen) {
            throw new ReportParseException("Отчёт пуст");
        }
        return new ParsedReport(new ArrayList<>(cases.values()));
    }

    private ParsedTestCase readTestCase(XMLStreamReader reader, String enclosingSuite) throws XMLStreamException {
        String name = attr(reader, "name");
        if (name.isEmpty()) {
            throw new ReportParseException("У элемента <testcase> нет атрибута name (строка "
                    + reader.getLocation().getLineNumber() + ")");
        }
        String classname = attr(reader, "classname");
        String suite = classname.isEmpty() ? enclosingSuite : classname;
        long durationMs = parseDurationMs(attr(reader, "time"));

        ParsedFailure finalFailure = null;
        TestStatus failureStatus = null;
        ParsedFailure retryFailure = null;
        boolean skipped = false;
        int failedAttempts = 0;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals("testcase")) {
                break;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (reader.getLocalName()) {
                case "failure" -> {
                    failureStatus = TestStatus.FAILED;
                    finalFailure = readProblem(reader);
                }
                case "error" -> {
                    failureStatus = TestStatus.ERROR;
                    finalFailure = readProblem(reader);
                }
                case "skipped" -> {
                    skipped = true;
                    skipElement(reader);
                }
                case "flakyFailure", "flakyError", "rerunFailure", "rerunError" -> {
                    failedAttempts++;
                    retryFailure = readProblem(reader);
                }
                default -> skipElement(reader);
            }
        }

        TestStatus status;
        if (failureStatus != null) {
            status = failureStatus;
        } else if (skipped) {
            status = TestStatus.SKIPPED;
        } else {
            status = TestStatus.PASSED;
        }
        ParsedFailure failure = finalFailure != null ? finalFailure : retryFailure;
        return new ParsedTestCase(suite, name, status, durationMs, failure, failedAttempts);
    }

    /**
     * Читает элемент падения: атрибуты type и message плюс текст. Текст берётся из самого элемента
     * и из дочернего {@code <stackTrace>} (так пишет Surefire во flakyFailure), а system-out и прочее пропускается.
     */
    private ParsedFailure readProblem(XMLStreamReader reader) throws XMLStreamException {
        String type = attr(reader, "type");
        String message = attr(reader, "message");
        String element = reader.getLocalName();
        StringBuilder text = new StringBuilder();
        int depth = 0;
        boolean collecting = true;

        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    depth++;
                    collecting = depth == 1 && reader.getLocalName().equals("stackTrace");
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if (depth == 0 && reader.getLocalName().equals(element)) {
                        return new ParsedFailure(type, message, text.toString());
                    }
                    depth--;
                    collecting = depth == 0;
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    if (collecting) {
                        text.append(reader.getText());
                    }
                }
                default -> {
                    // комментарии и инструкции обработки игнорируем
                }
            }
        }
        throw new ReportParseException("Элемент <" + element + "> не закрыт");
    }

    private static void skipElement(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    /** Время в секундах: «1.5», «0,25» (десятичная запятая), «1,234.5» (разделитель тысяч). */
    static long parseDurationMs(String seconds) {
        if (seconds.isEmpty()) {
            return 0;
        }
        String normalized = seconds.contains(".") ? seconds.replace(",", "") : seconds.replace(',', '.');
        try {
            double value = Double.parseDouble(normalized);
            return value > 0 ? Math.round(value * 1000) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String attr(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        return value == null ? "" : value.strip();
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static XMLInputFactory createFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory;
    }
}
