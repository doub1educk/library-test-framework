package ru.study.library.framework.client;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import ru.study.library.book.BookRequest;

import static io.restassured.RestAssured.given;

/**
 * Низкоуровневый HTTP-клиент: только отправка запросов, никаких проверок.
 * Проверки живут в тестах и шагах, чтобы клиент можно было использовать и для негативных сценариев.
 */
public class BookClient {

    private final RequestSpecification spec;

    public BookClient(int port) {
        this.spec = Specs.booksApi(port);
    }

    public Response create(BookRequest body) {
        return given(spec).body(body).post();
    }

    public Response createRaw(String rawJson) {
        return given(spec).body(rawJson).post();
    }

    public Response getById(long id) {
        return given(spec).get("/{id}", id);
    }

    public Response findAll(String author) {
        RequestSpecification request = given(spec);
        if (author != null) {
            request.queryParam("author", author);
        }
        return request.get();
    }

    public Response update(long id, BookRequest body) {
        return given(spec).body(body).put("/{id}", id);
    }

    public Response delete(long id) {
        return given(spec).delete("/{id}", id);
    }
}
