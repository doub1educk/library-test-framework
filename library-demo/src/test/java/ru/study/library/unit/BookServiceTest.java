package ru.study.library.unit;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.study.library.book.Book;
import ru.study.library.book.BookNotFoundException;
import ru.study.library.book.BookRepository;
import ru.study.library.book.BookRequest;
import ru.study.library.book.BookResponse;
import ru.study.library.book.BookService;
import ru.study.library.book.DuplicateIsbnException;
import ru.study.library.framework.data.BookDataFactory;
import ru.study.library.isbn.InvalidIsbnException;
import ru.study.library.isbn.IsbnClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
@Epic("Библиотека")
@Feature("Бизнес-логика BookService")
@DisplayName("Unit: BookService")
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository repository;

    @Mock
    private IsbnClient isbnClient;

    @InjectMocks
    private BookService service;

    @Test
    @Story("Создание книги")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Валидная книга сохраняется в репозиторий")
    void createSavesValidBook() {
        BookRequest request = BookDataFactory.validBook();
        given(repository.existsByIsbn(request.isbn())).willReturn(false);
        given(isbnClient.isValid(request.isbn())).willReturn(true);
        given(repository.save(any(Book.class))).willAnswer(invocation -> invocation.getArgument(0));

        BookResponse response = service.create(request);

        ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
        then(repository).should().save(captor.capture());
        assertThat(captor.getValue().getIsbn()).isEqualTo(request.isbn());
        assertThat(response.title()).isEqualTo(request.title());
        assertThat(response.available()).isTrue();
    }

    @Test
    @Story("Создание книги")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Дубликат ISBN отклоняется без обращения к внешнему сервису")
    void duplicateIsbnRejectedWithoutExternalCall() {
        BookRequest request = BookDataFactory.validBook();
        given(repository.existsByIsbn(request.isbn())).willReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateIsbnException.class)
                .hasMessageContaining(request.isbn());

        verifyNoInteractions(isbnClient);
        then(repository).should(never()).save(any());
    }

    @Test
    @Story("Создание книги")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Невалидный по мнению внешнего сервиса ISBN не сохраняется")
    void invalidIsbnRejected() {
        BookRequest request = BookDataFactory.validBook();
        given(repository.existsByIsbn(request.isbn())).willReturn(false);
        given(isbnClient.isValid(request.isbn())).willReturn(false);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(InvalidIsbnException.class);
        then(repository).should(never()).save(any());
    }

    @Test
    @Story("Получение книги")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Запрос несуществующей книги бросает BookNotFoundException")
    void getMissingBookThrows() {
        given(repository.findById(42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(42L))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    @Story("Обновление книги")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Обновление без смены ISBN не обращается к внешнему сервису")
    void updateWithSameIsbnSkipsExternalCheck() {
        BookRequest original = BookDataFactory.validBook();
        Book stored = new Book(original.title(), original.author(), original.isbn(), original.publishedYear());
        given(repository.findById(1L)).willReturn(Optional.of(stored));
        BookRequest changed = BookDataFactory.withTitle(original, "Новое название");

        BookResponse response = service.update(1L, changed);

        assertThat(response.title()).isEqualTo("Новое название");
        verifyNoInteractions(isbnClient);
    }

    @Test
    @Story("Удаление книги")
    @Severity(SeverityLevel.MINOR)
    @DisplayName("Удаление несуществующей книги бросает BookNotFoundException")
    void deleteMissingBookThrows() {
        given(repository.existsById(7L)).willReturn(false);

        assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(BookNotFoundException.class);
        then(repository).should(never()).deleteById(any());
    }
}
