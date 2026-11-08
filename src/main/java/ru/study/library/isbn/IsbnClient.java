package ru.study.library.isbn;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;

@Component
public class IsbnClient {

    private final RestClient restClient;

    public IsbnClient(RestClient.Builder builder, IsbnServiceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
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