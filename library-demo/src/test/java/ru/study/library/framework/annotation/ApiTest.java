package ru.study.library.framework.annotation;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ru.study.testfw.containers.PostgresContainerConfiguration;
import ru.study.testfw.db.SnapshotTablesOnFailure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Одна аннотация вместо пяти: Spring на случайном порту, PostgreSQL в Docker
 * и снимок таблицы books при падении (всё из общего test-framework).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("api")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresContainerConfiguration.class)
@SnapshotTablesOnFailure("books")
public @interface ApiTest {
}
