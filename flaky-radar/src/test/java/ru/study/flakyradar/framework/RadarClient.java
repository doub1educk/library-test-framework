package ru.study.flakyradar.framework;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import ru.study.testfw.http.RestAssuredSpecs;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * HTTP-клиент API Flaky Radar на RestAssured. Возвращает сырой {@link Response}:
 * проверки статусов делают шаги ({@link RadarSteps}) или сам тест для негативных сценариев.
 */
public class RadarClient {

    public static final String API = "/api/v1";
    private static final String API_KEY = "X-Api-Key";
    private static final String ADMIN_TOKEN = "X-Admin-Token";

    private final RequestSpecification json;
    private final RequestSpecification multipart;
    private final RequestSpecification plain;

    public RadarClient(int port) {
        this.json = RestAssuredSpecs.base(port, API).build();
        this.multipart = RestAssuredSpecs.base(port, API).setContentType("multipart/form-data").build();
        this.plain = RestAssuredSpecs.base(port, "").build();
    }

    // ===== проекты =====

    public Response createProject(String adminToken, Object body) {
        return withHeader(given(json), ADMIN_TOKEN, adminToken).body(body).post("/projects");
    }

    public Response rotateApiKey(String adminToken, String key) {
        return withHeader(given(json), ADMIN_TOKEN, adminToken).post("/projects/{key}/api-key", key);
    }

    public Response projects() {
        return given(json).get("/projects");
    }

    // ===== прогоны =====

    public Response upload(String key, String apiKey, RunUpload upload) {
        RequestSpecification request = withHeader(given(multipart), API_KEY, apiKey);
        int index = 0;
        for (byte[] report : upload.reports()) {
            request.multiPart("report", "TEST-report-" + index++ + ".xml", report, "application/xml");
        }
        formField(request, "branch", upload.branch());
        formField(request, "commit", upload.commit());
        formField(request, "attempt", upload.attempt() == null ? null : upload.attempt().toString());
        formField(request, "ciRunId", upload.ciRunId());
        formField(request, "buildUrl", upload.buildUrl());
        return request.post("/projects/{key}/runs", key);
    }

    public Response runs(String key, Map<String, ?> query) {
        return given(json).queryParams(query).get("/projects/{key}/runs", key);
    }

    public Response run(String key, long runId) {
        return given(json).get("/projects/{key}/runs/{id}", key, runId);
    }

    public Response gate(String key, long runId) {
        return given(json).get("/projects/{key}/runs/{id}/gate", key, runId);
    }

    public Response summary(String key, long runId) {
        return given(json).get("/projects/{key}/runs/{id}/summary.md", key, runId);
    }

    // ===== тесты и подписи =====

    public Response tests(String key, boolean flakyOnly) {
        return given(json).queryParam("flaky", flakyOnly).get("/projects/{key}/tests", key);
    }

    public Response history(String key, long testId) {
        return given(json).get("/projects/{key}/tests/{id}/history", key, testId);
    }

    public Response signatures(String key) {
        return given(json).get("/projects/{key}/signatures", key);
    }

    public Response signature(String key, long signatureId) {
        return given(json).get("/projects/{key}/signatures/{id}", key, signatureId);
    }

    // ===== карантин =====

    public Response addQuarantine(String key, String apiKey, Object body) {
        return withHeader(given(json), API_KEY, apiKey).body(body).post("/projects/{key}/quarantine", key);
    }

    public Response quarantine(String key) {
        return given(json).get("/projects/{key}/quarantine", key);
    }

    public Response removeQuarantine(String key, String apiKey, long id) {
        return withHeader(given(json), API_KEY, apiKey).delete("/projects/{key}/quarantine/{id}", key, id);
    }

    // ===== дашборд =====

    public Response page(String path) {
        return given(plain).accept("text/html").get(path);
    }

    private static RequestSpecification withHeader(RequestSpecification request, String name, String value) {
        return value == null ? request : request.header(name, value);
    }

    private static void formField(RequestSpecification request, String name, String value) {
        if (value != null) {
            request.multiPart(name, value);
        }
    }
}
