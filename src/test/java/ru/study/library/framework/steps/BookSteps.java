package ru.study.library.framework.steps;

import io.qameta.allure.Step;
import ru.study.library.book.BookRequest;
import ru.study.library.book.BookResponse;
import ru.study.library.framework.client.BookClient;
import ru.study.library.framework.client.Specs;
import ru.study.library.framework.data.BookDataFactory;

import java.util.List;

/**
 * Бизнес-шаги. Каждый метод с @Step становится узлом в дереве шагов Allure-отчёта.
 */
public class BookSteps {

    private final BookClient client;

    public BookSteps(BookClient client) {
        this.client = client;
    }

    @Step("Создать книгу через API")
    public BookResponse createBook(BookRequest request) {
        return client.create(request)
                .then().spec(Specs.status(201))
                .extract().as(BookResponse.class);
    }

    @Step("Создать случайную книгу")
    public BookResponse createRandomBook() {
        return createBook(BookDataFactory.validBook());
    }

    @Step("Получить книгу с id = {id}")
    public BookResponse getBook(long id) {
        return client.getById(id)
                .then().spec(Specs.status(200))
                .extract().as(BookResponse.class);
    }

    @Step("Найти книги по автору «{author}»")
    public List<BookResponse> findByAuthor(String author) {
        BookResponse[] books = client.findAll(author)
                .then().spec(Specs.status(200))
                .extract().as(BookResponse[].class);
        return List.of(books);
    }

    @Step("Удалить книгу с id = {id}")
    public void deleteBook(long id) {
        client.delete(id).then().spec(Specs.status(204));
    }

    @Step("Убедиться, что книги с id = {id} нет")
    public void assertBookAbsent(long id) {
        client.getById(id).then().spec(Specs.problem(404));
    }
}
