package ru.study.library.framework.client;

import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import ru.study.testfw.http.RestAssuredSpecs;

public final class Specs {
    public static final String BOOKS_PATH = "/api/v1/books";

    private Specs() {
    }

    public static RequestSpecification booksApi(int port) {
        return RestAssuredSpecs.base(port, BOOKS_PATH).build();
    }

    public static ResponseSpecification status(int statusCode) {
        return RestAssuredSpecs.status(statusCode);
    }

    public static ResponseSpecification problem(int statusCode) {
        return RestAssuredSpecs.problem(statusCode);
    }
}
