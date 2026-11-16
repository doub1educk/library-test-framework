package ru.study.flakyradar.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** DTO фичи «проекты». */
public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank
            @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,63}", message = "ключ: латиница в нижнем регистре, цифры и дефис, 2-64 символа")
            String key,
            @NotBlank @Size(max = 255) String name) {
    }

    public record ProjectResponse(String key, String name, Instant createdAt) {

        static ProjectResponse from(Project project) {
            return new ProjectResponse(project.getKey(), project.getName(), project.getCreatedAt());
        }
    }

    /** Ответ на создание проекта и перевыпуск ключа: ключ показывается только здесь. */
    public record ProjectCredentials(String key, String name, String apiKey) {
    }
}
