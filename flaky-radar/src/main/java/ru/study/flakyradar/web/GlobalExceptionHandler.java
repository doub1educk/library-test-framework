package ru.study.flakyradar.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import ru.study.flakyradar.ingest.IngestExceptions.EmptyReportException;
import ru.study.flakyradar.ingest.IngestExceptions.NoReportsException;
import ru.study.flakyradar.project.ProjectExceptions.InvalidAdminTokenException;
import ru.study.flakyradar.project.ProjectExceptions.InvalidApiKeyException;
import ru.study.flakyradar.project.ProjectExceptions.ProjectAlreadyExistsException;
import ru.study.flakyradar.project.ProjectExceptions.ProjectNotFoundException;
import ru.study.flakyradar.quarantine.QuarantineExceptions.AlreadyQuarantinedException;
import ru.study.flakyradar.quarantine.QuarantineExceptions.QuarantineNotFoundException;
import ru.study.flakyradar.report.ReportParseException;
import ru.study.flakyradar.run.RunExceptions.RunNotFoundException;
import ru.study.flakyradar.history.TestCaseExceptions.SignatureNotFoundException;
import ru.study.flakyradar.history.TestCaseExceptions.TestCaseNotFoundException;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ProjectNotFoundException.class, RunNotFoundException.class, TestCaseNotFoundException.class,
            SignatureNotFoundException.class, QuarantineNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "Не найдено", e.getMessage());
    }

    @ExceptionHandler(InvalidApiKeyException.class)
    public ProblemDetail handleInvalidApiKey(InvalidApiKeyException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Нет доступа к проекту", e.getMessage());
    }

    @ExceptionHandler(InvalidAdminTokenException.class)
    public ProblemDetail handleInvalidAdminToken(InvalidAdminTokenException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Нужны права администратора", e.getMessage());
    }

    @ExceptionHandler({ProjectAlreadyExistsException.class, AlreadyQuarantinedException.class})
    public ProblemDetail handleConflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, "Конфликт", e.getMessage());
    }

    @ExceptionHandler(ReportParseException.class)
    public ProblemDetail handleParse(ReportParseException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Отчёт не удалось разобрать", e.getMessage());
    }

    @ExceptionHandler(EmptyReportException.class)
    public ProblemDetail handleEmptyReport(EmptyReportException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Пустой отчёт", e.getMessage());
    }

    @ExceptionHandler(NoReportsException.class)
    public ProblemDetail handleNoReports(NoReportsException e) {
        return problem(HttpStatus.BAD_REQUEST, "Нет файла отчёта", e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Слишком большой отчёт", "Превышен допустимый размер загрузки");
    }

    @ExceptionHandler({MultipartException.class, UncheckedIOException.class})
    public ProblemDetail handleUpload(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Некорректная загрузка", "Не удалось прочитать multipart-запрос");
    }

    @ExceptionHandler(BindException.class)
    public ProblemDetail handleValidation(BindException e) {
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> Objects.requireNonNullElse(fe.getDefaultMessage(), "некорректное значение"),
                        (first, second) -> first,
                        TreeMap::new));
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Ошибка валидации", "Запрос содержит некорректные поля");
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "Некорректный JSON", "Тело запроса не удалось разобрать");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "Некорректный параметр",
                "Параметр '" + e.getName() + "' имеет неверный формат");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException e) {
        log.warn("Нарушение ограничения БД: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Конфликт данных",
                "Данные изменились параллельно с запросом, повторите его");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return problemDetail;
    }
}
