package ru.study.library.framework.annotation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ru.study.library.TestcontainersConfiguration;
import ru.study.library.framework.extension.DbSnapshotOnFailureExtension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Составная (meta-) аннотация фреймворка: одна строчка над классом вместо пяти.
 * Поднимает всё приложение на случайном порту, реальную БД в Testcontainers
 * и подключает расширение, которое при падении прикладывает в Allure снимок БД.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("api")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(DbSnapshotOnFailureExtension.class)
public @interface ApiTest {
}
