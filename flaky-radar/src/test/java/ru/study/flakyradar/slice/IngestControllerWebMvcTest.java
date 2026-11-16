package ru.study.flakyradar.slice;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.study.flakyradar.ingest.IngestController;
import ru.study.flakyradar.ingest.IngestService;
import ru.study.flakyradar.project.ProjectExceptions.InvalidApiKeyException;
import ru.study.flakyradar.report.ReportParseException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("slice")
@Epic("Flaky Radar")
@Feature("Веб-слой")
@DisplayName("Slice: IngestController (@WebMvcTest)")
@WebMvcTest(IngestController.class)
class IngestControllerWebMvcTest {

    private static final MockMultipartFile REPORT =
            new MockMultipartFile("report", "TEST-a.xml", "application/xml", "<testsuite/>".getBytes());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestService ingestService;

    @Test
    @DisplayName("Нет ветки и коммита -> 400 со списком полей, сервис не вызывается")
    void validation() throws Exception {
        mockMvc.perform(multipart("/api/v1/projects/demo/runs").file(REPORT).param("attempt", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ошибка валидации"))
                .andExpect(jsonPath("$.errors.branch").exists())
                .andExpect(jsonPath("$.errors.commit").exists())
                .andExpect(jsonPath("$.errors.attempt").exists());

        verifyNoInteractions(ingestService);
    }

    @Test
    @DisplayName("Ошибка разбора отчёта превращается в 422 Problem Details")
    void parseErrorMapped() throws Exception {
        given(ingestService.ingest(eq("demo"), anyString(), any(), any()))
                .willThrow(new ReportParseException("Отчёт не является корректным XML"));

        mockMvc.perform(multipart("/api/v1/projects/demo/runs").file(REPORT)
                        .header("X-Api-Key", "k").param("branch", "main").param("commit", "abc"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Отчёт не является корректным XML"));
    }

    @Test
    @DisplayName("Неверный API-ключ -> 401 Problem Details")
    void invalidKeyMapped() throws Exception {
        given(ingestService.ingest(eq("demo"), anyString(), any(), any())).willThrow(new InvalidApiKeyException());

        mockMvc.perform(multipart("/api/v1/projects/demo/runs").file(REPORT)
                        .header("X-Api-Key", "bad").param("branch", "main").param("commit", "abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Нет доступа к проекту"));
    }
}
