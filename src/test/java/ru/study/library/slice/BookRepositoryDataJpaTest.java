package ru.study.library.slice;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import ru.study.library.TestcontainersConfiguration;
import ru.study.library.book.Book;
import ru.study.library.book.BookRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Slice-тест слоя данных: JPA + Flyway-миграции на настоящем PostgreSQL (Testcontainers).
 * Каждый тест выполняется в транзакции и откатывается.
 */
@Tag("slice")
@Epic("Библиотека")
@Feature("Слой данных")
@DisplayName("Slice: BookRepository (@DataJpaTest + PostgreSQL)")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class BookRepositoryDataJpaTest {

    @Autowired
    private BookRepository repository;

    @Test
    @DisplayName("Миграция Flyway создала таблицу, книга сохраняется и получает id и дату")
    void savesBookWithGeneratedFields() {
        Book saved = repository.saveAndFlush(new Book("Мастер и Маргарита", "М. Булгаков", "9785389000001", 1967));

        assertThat(saved.getId()).isPositive();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(repository.existsByIsbn("9785389000001")).isTrue();
    }

    @Test
    @DisplayName("Поиск по автору регистронезависимый и по подстроке")
    void findsByAuthorIgnoringCase() {
        repository.save(new Book("Война и мир", "Лев Толстой", "9785000000001", 1869));
        repository.save(new Book("Анна Каренина", "Лев Толстой", "9785000000002", 1877));
        repository.save(new Book("Преступление и наказание", "Фёдор Достоевский", "9785000000003", 1866));

        assertThat(repository.findByAuthorContainingIgnoreCaseOrderByIdAsc("толст"))
                .extracting(Book::getTitle)
                .containsExactly("Война и мир", "Анна Каренина");
    }

    @Test
    @DisplayName("Ограничение UNIQUE на ISBN работает на уровне БД")
    void uniqueIsbnConstraint() {
        repository.saveAndFlush(new Book("Книга 1", "Автор", "9785000000010", 2000));

        assertThatThrownBy(() -> repository.saveAndFlush(new Book("Книга 2", "Автор", "9785000000010", 2001)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
