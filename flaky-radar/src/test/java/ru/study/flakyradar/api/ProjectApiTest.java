package ru.study.flakyradar.api;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ru.study.flakyradar.framework.BaseRadarApiTest;
import ru.study.flakyradar.framework.RadarTestData;
import ru.study.flakyradar.framework.RunUpload;
import ru.study.flakyradar.project.ProjectDtos.CreateProjectRequest;
import ru.study.flakyradar.project.ProjectDtos.ProjectCredentials;
import ru.study.testfw.http.RestAssuredSpecs;
import ru.study.testfw.junitxml.JUnitXmlBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@Epic("Flaky Radar")
@Feature("Проекты и доступ")
@DisplayName("API: проекты и API-ключи")
class ProjectApiTest extends BaseRadarApiTest {

    @Test
    @Tag("smoke")
    @Story("Создание проекта")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Админ создаёт проект -> 201, Location и API-ключ с префиксом frk_")
    void createProject() {
        String key = RadarTestData.projectKey();

        ProjectCredentials created = client.createProject(RadarTestData.ADMIN_TOKEN, new CreateProjectRequest(key, "Магазин"))
                .then().spec(RestAssuredSpecs.status(201))
                .header("Location", endsWith("/api/v1/projects/" + key))
                .extract().as(ProjectCredentials.class);

        assertThat(created.apiKey()).startsWith("frk_").hasSizeGreaterThan(30);
        client.projects().then().spec(RestAssuredSpecs.status(200)).body("key", hasItem(key));
    }

    @Test
    @Story("Создание проекта")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Без токена администратора или с неверным токеном -> 401")
    void createRequiresAdmin() {
        CreateProjectRequest request = new CreateProjectRequest(RadarTestData.projectKey(), "x");

        client.createProject(null, request).then().spec(RestAssuredSpecs.problem(401));
        client.createProject("wrong", request).then().spec(RestAssuredSpecs.problem(401));
    }

    @Test
    @Story("Создание проекта")
    @DisplayName("Ключ уже занят -> 409")
    void duplicateKey() {
        client.createProject(RadarTestData.ADMIN_TOKEN, new CreateProjectRequest(project.key(), "дубль"))
                .then().spec(RestAssuredSpecs.problem(409));
    }

    @Test
    @Story("Создание проекта")
    @DisplayName("Некорректный ключ проекта -> 400 со списком полей")
    void invalidKey() {
        client.createProject(RadarTestData.ADMIN_TOKEN, new CreateProjectRequest("Bad Key!", ""))
                .then().spec(RestAssuredSpecs.problem(400))
                .body("errors.key", notNullValue())
                .body("errors.name", notNullValue());
    }

    @Test
    @Story("API-ключ")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Загрузка с чужим или пустым ключом -> 401, в несуществующий проект -> 404")
    void uploadRequiresProjectKey() {
        RunUpload upload = RunUpload.of(JUnitXmlBuilder.report().passed(RadarTestData.SUITE, "ok"));

        client.upload(project.key(), null, upload).then().spec(RestAssuredSpecs.problem(401));
        client.upload(project.key(), "frk_wrong", upload).then().spec(RestAssuredSpecs.problem(401));
        client.upload("no-such-project", project.apiKey(), upload).then().spec(RestAssuredSpecs.problem(404));
    }

    @Test
    @Story("API-ключ")
    @DisplayName("Перевыпуск ключа: старый перестаёт работать, новый работает")
    void rotateApiKey() {
        ProjectCredentials rotated = client.rotateApiKey(RadarTestData.ADMIN_TOKEN, project.key())
                .then().spec(RestAssuredSpecs.status(200))
                .extract().as(ProjectCredentials.class);
        RunUpload upload = RunUpload.of(JUnitXmlBuilder.report().passed(RadarTestData.SUITE, "ok"));

        client.upload(project.key(), project.apiKey(), upload).then().spec(RestAssuredSpecs.problem(401));
        client.upload(project.key(), rotated.apiKey(), upload).then().spec(RestAssuredSpecs.status(201));
    }
}
