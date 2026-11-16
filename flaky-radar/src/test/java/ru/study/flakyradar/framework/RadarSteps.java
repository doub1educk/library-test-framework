package ru.study.flakyradar.framework;

import io.qameta.allure.Step;
import ru.study.flakyradar.project.ProjectDtos.CreateProjectRequest;
import ru.study.flakyradar.project.ProjectDtos.ProjectCredentials;
import ru.study.flakyradar.quarantine.QuarantineDtos.QuarantineRequest;
import ru.study.flakyradar.quarantine.QuarantineDtos.QuarantineResponse;
import ru.study.flakyradar.run.RunDtos.GateResponse;
import ru.study.flakyradar.run.RunDtos.ResultView;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.flakyradar.testcase.TestCaseDtos.SignatureView;
import ru.study.flakyradar.testcase.TestCaseDtos.TestCaseView;
import ru.study.flakyradar.testcase.TestCaseDtos.TestHistoryView;
import ru.study.testfw.http.RestAssuredSpecs;
import ru.study.testfw.junitxml.JUnitXmlBuilder;

import java.util.List;

public class RadarSteps {
    private final RadarClient client;

    public RadarSteps(RadarClient client) {
        this.client = client;
    }

    @Step("Создать проект «{key}»")
    public TestProject createProject(String key) {
        ProjectCredentials credentials = client.createProject(RadarTestData.ADMIN_TOKEN,
                        new CreateProjectRequest(key, "Проект " + key))
                .then().spec(RestAssuredSpecs.status(201))
                .extract().as(ProjectCredentials.class);
        return new TestProject(credentials.key(), credentials.apiKey());
    }

    @Step("CI загружает прогон: ветка {upload.branch}, попытка {upload.attempt}")
    public RunDetails upload(TestProject project, RunUpload upload) {
        return client.upload(project.key(), project.apiKey(), upload)
                .then().spec(RestAssuredSpecs.status(201))
                .extract().as(RunDetails.class);
    }

    public RunDetails upload(TestProject project, JUnitXmlBuilder report) {
        return upload(project, RunUpload.of(report));
    }

    @Step("Запросить решение gate для прогона {runId}")
    public GateResponse gate(TestProject project, long runId) {
        return client.gate(project.key(), runId)
                .then().spec(RestAssuredSpecs.status(200))
                .extract().as(GateResponse.class);
    }

    @Step("Получить прогон {runId}")
    public RunDetails run(TestProject project, long runId) {
        return client.run(project.key(), runId)
                .then().spec(RestAssuredSpecs.status(200))
                .extract().as(RunDetails.class);
    }

    @Step("Получить список нестабильных тестов")
    public List<TestCaseView> flakyTests(TestProject project) {
        return List.of(client.tests(project.key(), true)
                .then().spec(RestAssuredSpecs.status(200))
                .extract().as(TestCaseView[].class));
    }

    @Step("Получить все тесты проекта")
    public List<TestCaseView> allTests(TestProject project) {
        return List.of(client.tests(project.key(), false)
                .then().spec(RestAssuredSpecs.status(200))
                .extract().as(TestCaseView[].class));
    }

    @Step("Найти тест «{fullName}»")
    public TestCaseView test(TestProject project, String fullName) {
        return allTests(project).stream()
                .filter(t -> t.fullName().equals(fullName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Тест " + fullName + " не найден в проекте"));
    }

    @Step("Получить историю теста {testId}")
    public TestHistoryView history(TestProject project, long testId) {
        return client.history(project.key(), testId)
                .then().spec(RestAssuredSpecs.status(200))
                .extract().as(TestHistoryView.class);
    }

    @Step("Получить группы ошибок")
    public List<SignatureView> signatures(TestProject project) {
        return List.of(client.signatures(project.key())
                .then().spec(RestAssuredSpecs.status(200))
                .extract().as(SignatureView[].class));
    }

    @Step("Отправить в карантин тест «{testName}»")
    public QuarantineResponse quarantine(TestProject project, String testName, String reason) {
        return client.addQuarantine(project.key(), project.apiKey(), new QuarantineRequest(testName, reason, "qa", null))
                .then().spec(RestAssuredSpecs.status(201))
                .extract().as(QuarantineResponse.class);
    }

    @Step("Снять с карантина запись {id}")
    public void removeQuarantine(TestProject project, long id) {
        client.removeQuarantine(project.key(), project.apiKey(), id).then().spec(RestAssuredSpecs.status(204));
    }

    public static ResultView problem(RunDetails details, String fullName) {
        return details.problems().stream()
                .filter(p -> p.testName().equals(fullName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Среди проблем прогона нет теста " + fullName
                        + ", есть: " + details.problems().stream().map(ResultView::testName).toList()));
    }
}
