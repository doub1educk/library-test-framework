package ru.study.flakyradar.quarantine;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** DTO карантина. */
public final class QuarantineDtos {

    private QuarantineDtos() {
    }

    /**
     * @param testName  полное имя теста ({@code classname.name}), как оно пришло в отчёте
     * @param reason    почему тест отправлен в карантин (ссылка на задачу и т.п.)
     * @param createdBy кто отправил (необязательно)
     * @param expiresAt когда карантин снимется сам (необязательно)
     */
    public record QuarantineRequest(
            @NotBlank @Size(max = 2048) String testName,
            @NotBlank @Size(max = 1024) String reason,
            @Size(max = 255) String createdBy,
            @Future Instant expiresAt) {
    }

    public record QuarantineResponse(
            Long id,
            Long testCaseId,
            String testName,
            String reason,
            String createdBy,
            Instant createdAt,
            Instant expiresAt) {

        static QuarantineResponse from(Quarantine q) {
            return new QuarantineResponse(q.getId(), q.getTestCase().getId(), q.getTestCase().getFullName(),
                    q.getReason(), q.getCreatedBy(), q.getCreatedAt(), q.getExpiresAt());
        }
    }
}
