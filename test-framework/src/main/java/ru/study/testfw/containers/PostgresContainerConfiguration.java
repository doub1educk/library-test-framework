package ru.study.testfw.containers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL в Docker для любого Spring Boot приложения проекта.
 * {@code @ServiceConnection} сам подставляет url, логин и пароль в DataSource.
 * Spring кэширует контекст, поэтому контейнер поднимается один раз на все тесты с одинаковой конфигурацией.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfiguration {

    public static final String IMAGE = "postgres:17-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse(IMAGE));
    }
}
