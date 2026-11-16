package ru.study.testfw.db;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * При падении теста прикладывает к Allure-отчёту содержимое перечисленных таблиц.
 * Можно ставить на тест-класс, базовый класс или мета-аннотацию.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(TableSnapshotOnFailureExtension.class)
public @interface SnapshotTablesOnFailure {

    /** Имена таблиц. */
    String[] value();

    /** Сколько строк каждой таблицы прикладывать. */
    int maxRows() default 50;
}
