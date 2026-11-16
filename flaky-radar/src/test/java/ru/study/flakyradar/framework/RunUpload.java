package ru.study.flakyradar.framework;

import ru.study.testfw.junitxml.JUnitXmlBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;

public record RunUpload(List<byte[]> reports, String branch, String commit, Integer attempt, String ciRunId,
                        String buildUrl) {
    public static RunUpload of(JUnitXmlBuilder report) {
        return new RunUpload(List.of(report.toBytes()), "main", RadarTestData.commitSha(), 1, null, null);
    }

    public static RunUpload ofXml(String xml) {
        return new RunUpload(List.of(xml.getBytes(StandardCharsets.UTF_8)), "main", RadarTestData.commitSha(), 1, null, null);
    }

    public static RunUpload ofFiles(List<byte[]> files) {
        return new RunUpload(List.copyOf(files), "main", RadarTestData.commitSha(), 1, null, null);
    }

    public RunUpload branch(String value) {
        return new RunUpload(reports, value, commit, attempt, ciRunId, buildUrl);
    }

    public RunUpload commit(String value) {
        return new RunUpload(reports, branch, value, attempt, ciRunId, buildUrl);
    }

    public RunUpload attempt(Integer value) {
        return new RunUpload(reports, branch, commit, value, ciRunId, buildUrl);
    }

    public RunUpload ciRunId(String value) {
        return new RunUpload(reports, branch, commit, attempt, value, buildUrl);
    }

    public RunUpload buildUrl(String value) {
        return new RunUpload(reports, branch, commit, attempt, ciRunId, value);
    }
}
