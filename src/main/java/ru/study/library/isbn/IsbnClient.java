package ru.study.library.isbn;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Клиент внешнего сервиса проверки ISBN.
 * В тестах вместо реального сервиса поднимается WireMock.
 */
@Component
public class IsbnClient {

    private final RestClient restClient;

    public IsbnClient(RestClient.Builder builder, IsbnServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public boolean isValid(String isbn) {
        try {
            IsbnCheckResponse response = restClient.get()
                    .uri("/api/isbn/{isbn}", isbn)
                    .retrieve()
                    .body(IsbnCheckResponse.class);
            return response != null && response.valid();
        } catch (RestClientException e) {
            throw new IsbnServiceUnavailableException("Сервис проверки ISBN недоступен", e);
        }
    }
}
