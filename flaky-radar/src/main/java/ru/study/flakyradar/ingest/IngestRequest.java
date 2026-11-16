package ru.study.flakyradar.ingest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IngestRequest(
        @NotBlank @Size(max = 255) String branch,
        @NotBlank @Size(max = 64) String commit,
        @Min(1) Integer attempt,
        @Size(max = 128) String ciRunId,
        @Size(max = 1024) String buildUrl) {
    public IngestRequest {
        attempt = attempt == null ? 1 : attempt;
        ciRunId = ciRunId == null || ciRunId.isBlank() ? null : ciRunId.strip();
        buildUrl = buildUrl == null || buildUrl.isBlank() ? null : buildUrl.strip();
    }
}
