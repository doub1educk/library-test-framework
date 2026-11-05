package ru.study.library.book;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookRequest(
        @NotBlank(message = "Название обязательно")
        @Size(max = 255, message = "Название не длиннее 255 символов")
        String title,

        @NotBlank(message = "Автор обязателен")
        @Size(max = 255, message = "Имя автора не длиннее 255 символов")
        String author,

        @NotBlank(message = "ISBN обязателен")
        @Pattern(regexp = "\\d{13}", message = "ISBN должен состоять из 13 цифр")
        String isbn,

        @NotNull(message = "Год издания обязателен")
        @Min(value = 1450, message = "Год издания не раньше 1450")
        @Max(value = 2100, message = "Год издания не позже 2100")
        Integer publishedYear
) {
}
