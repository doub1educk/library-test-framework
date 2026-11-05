package ru.study.library.slice;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.study.library.book.BookController;
import ru.study.library.book.BookNotFoundException;
import ru.study.library.book.BookResponse;
import ru.study.library.book.BookService;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice-тест: поднимается только веб-слой (контроллер + ControllerAdvice + Jackson + Validation).
 * Сервис подменяется моком, БД и внешние сервисы не нужны, поэтому тест очень быстрый.
 */
@Tag("slice")
@Epic("Библиотека")
@Feature("Веб-слой")
@DisplayName("Slice: BookController (@WebMvcTest)")
@WebMvcTest(BookController.class)
class BookControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @Test
    @DisplayName("Невалидное тело запроса -> 400 со списком полей, сервис не вызывается")
    void invalidBodyReturnsValidationErrors() throws Exception {
        String body = """
                {"title": "", "author": "Пушкин", "isbn": "123", "publishedYear": 2020}
                """;

        mockMvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.isbn").exists())
                .andExpect(jsonPath("$.errors.author").doesNotExist());

        verifyNoInteractions(bookService);
    }

    @Test
    @DisplayName("Создание возвращает 201 и заголовок Location")
    void createReturnsLocation() throws Exception {
        given(bookService.create(any())).willReturn(
                new BookResponse(7L, "Евгений Онегин", "А. С. Пушкин", "9785170000000", 1833, true, Instant.now()));
        String body = """
                {"title": "Евгений Онегин", "author": "А. С. Пушкин", "isbn": "9785170000000", "publishedYear": 1833}
                """;

        mockMvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/books/7")))
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("Исключение BookNotFoundException превращается в 404 Problem Details")
    void notFoundMappedToProblemDetails() throws Exception {
        given(bookService.getById(42L)).willThrow(new BookNotFoundException(42L));

        mockMvc.perform(get("/api/v1/books/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("42")));
    }
}
