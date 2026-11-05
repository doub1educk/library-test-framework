# Library Service + Test Automation Framework

[![tests](https://github.com/doubleduck1/library-test-framework/actions/workflows/tests.yml/badge.svg)](https://github.com/doubleduck1/library-test-framework/actions/workflows/tests.yml)
[![coverage](https://doubleduck1.github.io/library-test-framework/badges/coverage.svg)](https://doubleduck1.github.io/library-test-framework/jacoco/)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![Allure](https://img.shields.io/badge/report-Allure-yellow)

REST-сервис «Библиотека» на Spring Boot и тестовый фреймворк вокруг него:
unit, slice, API и архитектурные тесты, реальная БД в Docker, эмуляция внешнего сервиса
и подробный Allure-отчёт, который обновляется автоматически при каждом коммите.

## Посмотреть без запуска

| Что | Ссылка |
|---|---|
| **Allure-отчёт**: все тесты, шаги, HTTP-запросы и ответы | **https://doubleduck1.github.io/library-test-framework/** |
| Покрытие кода (JaCoCo) | https://doubleduck1.github.io/library-test-framework/jacoco/ |
| История прогонов в CI | [GitHub Actions](https://github.com/doubleduck1/library-test-framework/actions) |

### Как выглядит отчёт

**Обзор прогона:** сколько тестов, тренд по прошлым запускам, окружение.

![Allure: обзор](docs/images/allure-overview.png)

**Один тест изнутри:** шаги, отправленный запрос и полученный ответ.

![Allure: шаги теста](docs/images/allure-test-steps.png)

**Упавший тест:** категория ошибки, все расхождения и снимок БД во вложении.

![Allure: падение](docs/images/allure-failure.png)

---

## Уровни тестирования

Чем выше уровень, тем больше частей системы участвует в тесте и тем он медленнее.
Чем ниже, тем тест быстрее и точнее указывает на место ошибки.

```mermaid
flowchart TB
    ARCH["<b>Архитектурные тесты</b><br/>ArchUnit<br/>проверяют структуру кода: слои, зависимости, именование"]
    API["<b>API-тесты</b><br/>@SpringBootTest + RestAssured + Testcontainers + WireMock<br/>весь сервис целиком через настоящий HTTP"]
    SLICE["<b>Slice-тесты</b><br/>@WebMvcTest, @DataJpaTest<br/>отдельный слой Spring: только веб или только БД"]
    UNIT["<b>Unit-тесты</b><br/>JUnit 5 + Mockito<br/>бизнес-логика без Spring, за миллисекунды"]

    ARCH ~~~ API
    API ~~~ SLICE
    SLICE ~~~ UNIT

    classDef arch fill:#ede7f6,stroke:#5e35b1,color:#311b92
    classDef api fill:#e3f2fd,stroke:#1e88e5,color:#0d47a1
    classDef slice fill:#e8f5e9,stroke:#43a047,color:#1b5e20
    classDef unit fill:#fff8e1,stroke:#f9a825,color:#e65100
    class ARCH arch
    class API api
    class SLICE slice
    class UNIT unit
```

| Уровень | Тестов | Что проверяется |
|---|---|---|
| Unit | 6 | правила сервиса: дубликаты ISBN, порядок проверок, ошибки «не найдено» |
| Slice: веб | 3 | валидация запросов, коды ответов, формат ошибок |
| Slice: БД | 3 | миграции Flyway, поиск без учёта регистра, уникальность ISBN в PostgreSQL |
| API | 20 | CRUD, 8 видов некорректных данных, отказы и таймауты внешнего сервиса |
| Архитектура | 6 | контроллеры не ходят в репозитории, сущности не утекают в API и т.д. |

## Устройство тестового фреймворка

Тест описывает **что** проверяется, а фреймворк берёт на себя **как**: запуск окружения,
HTTP, данные, проверки и отчётность.

```mermaid
flowchart LR
    subgraph TESTS["Тесты"]
        TEST["BookCrudApiTest<br/>BookValidationApiTest<br/>IsbnIntegrationApiTest"]
    end

    subgraph FW["Тестовый фреймворк"]
        BASE["@ApiTest + BaseApiTest<br/>запуск Spring, очистка БД"]
        STEPS["BookSteps<br/>бизнес-шаги с @Step"]
        CLIENT["BookClient + Specs<br/>HTTP-запросы на RestAssured"]
        DATA["BookDataFactory<br/>генерация тестовых данных"]
        ASSERT["BookAssert<br/>читаемые проверки"]
        MOCK["IsbnServiceMock<br/>сценарии внешнего сервиса"]
        EXT["DbSnapshotOnFailureExtension<br/>снимок БД при падении"]
    end

    subgraph SUT["Тестируемая система"]
        APP["Library Service<br/>Spring Boot"]
        DB[("PostgreSQL<br/>в Docker")]
        WM["WireMock<br/>вместо ISBN-сервиса"]
    end

    ALLURE[["Allure-отчёт"]]

    TEST --> BASE
    TEST --> STEPS
    TEST --> DATA
    TEST --> ASSERT
    TEST --> MOCK
    STEPS --> CLIENT
    CLIENT -->|HTTP| APP
    APP --> DB
    APP -->|HTTP| WM
    MOCK -.->|настраивает| WM
    BASE -.-> EXT
    STEPS -.->|шаги| ALLURE
    CLIENT -.->|запросы и ответы| ALLURE
    EXT -.->|вложение| ALLURE
```

| Компонент | Зачем нужен |
|---|---|
| `@ApiTest` | одна аннотация вместо пяти: Spring на случайном порту, БД в Docker, расширения |
| `BaseApiTest` | перед каждым тестом чистая БД и сброшенный мок, тесты не влияют друг на друга |
| `BookClient`, `Specs` | URL, порт, заголовки и логирование в Allure описаны один раз |
| `BookSteps` | действия на языке предметной области; каждое видно в отчёте как шаг |
| `BookDataFactory` | каждый тест получает уникальные данные; наборы негативных кейсов |
| `BookAssert` | `assertThat(book).hasGeneratedId().matches(request)`, показывает все расхождения сразу |
| `IsbnServiceMock` | одной строкой: «сервис говорит невалидно», «отвечает 500», «тормозит» |
| `DbSnapshotOnFailureExtension` | при падении прикладывает к отчёту содержимое таблицы |

## Что происходит при запуске API-теста

На примере теста «внешний сервис считает ISBN невалидным».

```mermaid
sequenceDiagram
    autonumber
    participant J as Maven и JUnit
    participant TC as Testcontainers
    participant PG as PostgreSQL
    participant WM as WireMock
    participant APP as Spring Boot
    participant T as Тест
    participant AL as Allure

    rect rgb(240, 245, 255)
    Note over J,APP: Подготовка окружения, один раз на все тесты
    J->>TC: нужен PostgreSQL
    TC->>PG: запустить контейнер postgres:17
    J->>WM: запустить на свободном порту
    J->>APP: поднять приложение на случайном порту
    APP->>PG: Flyway создаёт таблицу books
    Note over APP,WM: адрес ISBN-сервиса подменён на WireMock
    end

    rect rgb(245, 255, 245)
    Note over T,WM: Перед каждым тестом
    T->>PG: очистить таблицу
    T->>WM: сбросить сценарии
    end

    T->>WM: сценарий: этот ISBN невалиден
    T->>APP: POST /api/v1/books
    APP->>PG: есть ли уже такой ISBN?
    PG-->>APP: нет
    APP->>WM: GET /api/isbn/9781234567890
    WM-->>APP: valid = false
    APP-->>T: 422 Unprocessable Entity
    T->>WM: проверить, что был ровно 1 вызов
    T->>PG: проверить, что книга не сохранилась
    T->>AL: шаги, запрос и ответ, результат

    opt если тест упал
        T->>AL: вложение со снимком таблицы books
    end
```

---

## Быстрый старт

Нужно: JDK 25, Maven 3.9+, запущенный Docker.

```bash
mvn test              # все тесты
mvn allure:serve      # открыть отчёт в браузере
```

Запуск самого приложения:

```bash
docker compose up -d  # PostgreSQL + заглушка ISBN-сервиса
mvn spring-boot:run   # Swagger: http://localhost:8080/swagger-ui.html
```

### Наборы тестов

```bash
mvn test -Dgroups=unit
mvn test -Dgroups=smoke
mvn test -Dgroups=api
mvn test -Dgroups=demo -DexcludedGroups=none   # намеренно падающий демо-тест
mvn verify                                     # тесты + проверка покрытия (>= 70%)
mvn -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage   # мутационное тестирование
```

## Стек

| Часть | Технологии |
|---|---|
| Приложение | Java 25, Spring Boot 3.5 (Web MVC, Data JPA, Validation, Actuator), PostgreSQL, Flyway, springdoc-openapi |
| Тесты | JUnit 5, Mockito, AssertJ, RestAssured, JSON Schema, Testcontainers, WireMock, Datafaker, ArchUnit |
| Отчёты | Allure, JaCoCo, PIT |
| CI | GitHub Actions, GitHub Pages |

## Структура

```
src/main/java/ru/study/library/
├── book/           контроллер, сервис, репозиторий, сущность, DTO
├── isbn/           клиент внешнего сервиса проверки ISBN
└── web/            единая обработка ошибок (Problem Details)

src/test/java/ru/study/library/
├── framework/      тестовый фреймворк (см. схему выше)
├── unit/           тесты бизнес-логики на моках
├── slice/          тесты веб-слоя и слоя данных
├── api/            сквозные тесты через HTTP
└── architecture/   архитектурные правила
```

## Запуск из IntelliJ IDEA

1. File → Open → `pom.xml` → Open as Project.
2. File → Project Structure → SDK: 25.
3. Docker должен быть запущен.
4. Чтобы `@Step` попадали в Allure, запускай тесты через Maven (панель Maven → Lifecycle → test)
   или добавь в шаблон JUnit-конфигурации VM option
   `-javaagent:<~/.m2>/repository/org/aspectj/aspectjweaver/1.9.25.1/aspectjweaver-1.9.25.1.jar`.
