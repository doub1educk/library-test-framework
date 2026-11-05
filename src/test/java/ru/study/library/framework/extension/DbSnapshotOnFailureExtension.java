package ru.study.library.framework.extension;

import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.study.library.book.BookRepository;

import java.util.stream.Collectors;

/**
 * JUnit 5 расширение: если тест упал, прикладывает к нему в Allure
 * текущее содержимое таблицы books. Сильно упрощает разбор падений.
 */
public class DbSnapshotOnFailureExtension implements AfterTestExecutionCallback {

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isEmpty()) {
            return;
        }
        BookRepository repository = SpringExtension.getApplicationContext(context).getBean(BookRepository.class);
        String snapshot = repository.findAll().stream()
                .map(b -> "%d | %s | %s | %s | %d".formatted(
                        b.getId(), b.getTitle(), b.getAuthor(), b.getIsbn(), b.getPublishedYear()))
                .collect(Collectors.joining("\n", "id | title | author | isbn | year\n", ""));
        Allure.addAttachment("Снимок таблицы books на момент падения", "text/plain", snapshot);
    }
}
