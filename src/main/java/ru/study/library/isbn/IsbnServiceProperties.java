package ru.study.library.isbn;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "isbn-service")
public record IsbnServiceProperties(String baseUrl, Duration timeout) {
}
