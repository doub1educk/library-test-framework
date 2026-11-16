package ru.study.testfw.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.ResponseSpecification;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public final class RestAssuredSpecs {
    public static final RestAssuredConfig CONFIG = RestAssuredConfig.config()
            .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                    .jackson2ObjectMapperFactory((type, charset) -> new ObjectMapper()
                            .findAndRegisterModules()
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)));

    private RestAssuredSpecs() {
    }

    public static RequestSpecBuilder base(int port, String basePath) {
        return new RequestSpecBuilder()
                .setBaseUri("http://localhost")
                .setPort(port)
                .setBasePath(basePath)
                .setContentType(ContentType.JSON)
                .setConfig(CONFIG)
                .addFilter(new AllureRestAssured())
                .log(LogDetail.METHOD)
                .log(LogDetail.URI);
    }

    public static ResponseSpecification status(int statusCode) {
        return new ResponseSpecBuilder()
                .expectStatusCode(statusCode)
                .build();
    }

    public static ResponseSpecification problem(int statusCode) {
        return new ResponseSpecBuilder()
                .expectStatusCode(statusCode)
                .expectHeader("Content-Type", containsString("json"))
                .expectBody("status", equalTo(statusCode))
                .expectBody("title", notNullValue())
                .build();
    }
}
