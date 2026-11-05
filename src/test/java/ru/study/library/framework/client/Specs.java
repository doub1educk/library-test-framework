package ru.study.library.framework.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Общие спецификации запросов и ответов.
 */
public final class Specs {

    public static final String BOOKS_PATH = "/api/v1/books";

    /** ObjectMapper с поддержкой java.time (Instant в ответах). */
    private static final RestAssuredConfig CONFIG = RestAssuredConfig.config()
            .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                    .jackson2ObjectMapperFactory((type, charset) -> new ObjectMapper()
                            .findAndRegisterModules()
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)));

    private Specs() {
    }

    public static RequestSpecification booksApi(int port) {
        return new RequestSpecBuilder()
                .setBaseUri("http://localhost")
                .setPort(port)
                .setBasePath(BOOKS_PATH)
                .setContentType(ContentType.JSON)
                .setConfig(CONFIG)
                // каждый запрос/ответ автоматически прикладывается к шагу в Allure
                .addFilter(new AllureRestAssured())
                .log(LogDetail.METHOD)
                .log(LogDetail.URI)
                .build();
    }

    public static ResponseSpecification status(int statusCode) {
        return new ResponseSpecBuilder()
                .expectStatusCode(statusCode)
                .build();
    }

    /** Ответ-ошибка в формате Problem Details (RFC 9457). */
    public static ResponseSpecification problem(int statusCode) {
        return new ResponseSpecBuilder()
                .expectStatusCode(statusCode)
                .expectHeader("Content-Type", containsString("json"))
                .expectBody("status", equalTo(statusCode))
                .expectBody("title", org.hamcrest.Matchers.notNullValue())
                .build();
    }
}
