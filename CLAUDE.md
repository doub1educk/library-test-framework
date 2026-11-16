# CLAUDE.md — Flaky Radar

Учебный проект по курсу «Современные технологии серверной разработки» (Spring Boot). Команда из двух человек.
Защита примерно **21.11.2026**: ядро уже работает от начала до конца, теперь важнее стабильность и демо, чем новые фичи.

## Продукт

**Flaky Radar** — сервис анализа результатов автотестов.

1. **Приём:** CI любой команды отправляет JUnit XML (Playwright, JUnit/Surefire, pytest) вместе с веткой, коммитом,
   номером попытки и id CI-прогона.
2. **История:** результаты каждого теста хранятся в PostgreSQL.
3. **Флаки:** (а) тест прошёл только после повтора внутри прогона; (б) на одном коммите он и проходил, и падал;
   (в) в последних 30 результатах (без SKIPPED) у него ≥ 3 переключений pass↔fail при n ≥ 5.
   Пороги задаются в `flaky-radar.detection.*`.
4. **Подписи ошибок:** падения группируются по нормализованному тексту ошибки, ключ — SHA-256.
5. **Вердикт:** у каждого упавшего результата `KNOWN_FLAKY` / `LONG_BROKEN` / `NEW_FAILURE`, у прогона решение
   gate `PASS` / `WARN` / `FAIL`.
6. **Карантин** через API: тесты в карантине не блокируют gate.
7. **Каналы:** дашборд на Thymeleaf, `summary.md` для комментария в PR и Job Summary, Telegram
   (простой текст из тех же данных).

`library-demo` («Библиотека») — демо-проект: в CI его тесты отправляются в Flaky Radar (джоб `dogfood`).

## Стек

Java 25, Spring Boot 3.5 (Web MVC, Data JPA, Validation, Actuator, Thymeleaf), PostgreSQL 17, Flyway,
springdoc-openapi, Maven. Тесты: JUnit 5, Mockito, AssertJ, RestAssured, Testcontainers, WireMock, Datafaker,
ArchUnit, Allure (+ AspectJ-агент), JaCoCo (≥ 70%), PIT. CI: GitHub Actions, отчёты публикуются на GitHub Pages.
ОС разработчиков: Windows.

## Модули (монорепо, Maven multi-module)

```
pom.xml            parent (artifactId flaky-radar-parent): версии, pluginManagement (surefire+javaagent, jacoco, allure, pitest)
├── test-framework/  jar с общей тестовой инфраструктурой (ru.study.testfw), зависимости в compile scope
├── flaky-radar/     сервис (ru.study.flakyradar)
├── library-demo/    «Библиотека» (ru.study.library)
└── scripts/         flaky-radar-upload.sh — загрузка отчётов из CI + проверка gate
```

`flaky-radar` и `library-demo` подключают `test-framework` в scope `test` и не зависят друг от друга.
Плагины объявлены в `pluginManagement` родителя; модуль включает нужные, перечисляя их в `<build><plugins>`.

### Пакеты `flaky-radar` (по фичам)

| Пакет | Содержимое |
|---|---|
| `report` | `JUnitXmlParser` (StAX) → `ParsedReport`/`ParsedTestCase`/`ParsedFailure`, `TestStatus`. **Чистая Java** |
| `analysis` | `SignatureNormalizer`, `FlakinessDetector`, `VerdictCalculator`, `GateEvaluator`, `DetectionPolicy`. **Чистая Java** |
| `config` | `FlakyRadarProperties`, сборка бинов анализа (`AnalysisConfiguration`), OpenAPI |
| `project` | проект, API-ключи (`ApiKeys`: генерация, SHA-256, сравнение за постоянное время) |
| `history` | сущности `TestRun`, `TestCase`, `TestResult`, `FailureSignature`, репозитории, `TestHistoryQueries` (JdbcClient + оконная функция), `TestCaseExceptions` |
| `ingest` | `IngestController` (multipart), `IngestService` (вся загрузка в одной транзакции), `RunIngestedEvent` |
| `run` | чтение прогонов, gate (пересчитывается с текущим карантином), `SummaryRenderer` (markdown + текст) |
| `testcase` | списки тестов, история теста, группы ошибок |
| `quarantine` | API карантина |
| `notify` | `TelegramNotifier` (`@TransactionalEventListener(AFTER_COMMIT)`, ошибки только логируются) |
| `dashboard` | `DashboardController` + шаблоны в `resources/templates`, стили в `static/css/radar.css` |
| `web` | `GlobalExceptionHandler` (только для `@RestController`; у дашборда свой обработчик 404) |

Допустимые зависимости: `analysis → report`; `history → analysis, report`; фичи → `history`, `project`, `config`;
`web` и `dashboard` → фичи. Циклы между пакетами запрещены (ArchUnit `slices().beFreeOfCycles()`).

## Доменная модель

- **Project:** `project_key` уникален; API-ключ `frk_...` показывается один раз, в БД хранится SHA-256.
  Создание проекта и перевыпуск ключа требуют `X-Admin-Token` (`flaky-radar.admin-token`).
- **TestRun:** `branch`, `commit_sha`, `attempt`, `ci_run_id`, `build_url`, счётчики, `decision` (на момент загрузки).
  Пара (`project`, `ci_run_id`, `attempt`) уникальна, это основа идемпотентности.
- **TestCase:** `full_name = classname + "." + name` (без classname берётся имя testsuite). Хранит
  денормализованные `flaky`, `flip_count`, `last_status`, которые пересчитываются при каждой загрузке.
- **TestResult:** статус, длительность, `failed_attempts` (повторы внутри отчёта), обрезанное сообщение, подпись,
  вердикт, `quarantined` на момент загрузки.
- **FailureSignature:** `hash`, `exception_type`, `normalized_text`, `sample_message`, `occurrences`.
- **Quarantine:** активна, пока `removed_at is null` и не истёк `expires_at`. Частичный уникальный индекс:
  у теста не больше одной незакрытой записи.

## API (v1)

| Метод | Путь | Доступ |
|---|---|---|
| POST | `/api/v1/projects`, `/api/v1/projects/{key}/api-key` | `X-Admin-Token` |
| GET | `/api/v1/projects`, `/api/v1/projects/{key}` | открыто |
| POST | `/api/v1/projects/{key}/runs` (multipart: `report`…, `branch`, `commit`, `attempt`, `ciRunId`, `buildUrl`) | `X-Api-Key` |
| GET | `/api/v1/projects/{key}/runs[?branch&limit]`, `/runs/{id}`, `/runs/{id}/gate`, `/runs/{id}/summary.md` | открыто |
| GET | `/api/v1/projects/{key}/tests[?flaky&limit]`, `/tests/{id}/history`, `/signatures`, `/signatures/{id}` | открыто |
| POST / DELETE | `/api/v1/projects/{key}/quarantine[/{id}]` | `X-Api-Key` |
| GET | `/api/v1/projects/{key}/quarantine` | открыто |

Коды: новый прогон 201 + `Location`, повтор той же загрузки 200; битый XML или пустой отчёт 422; нет файла или
невалидные поля 400 (`errors` по полям); неверный ключ 401; конфликт 409. Дашборд: `/`, `/projects/{key}`,
`/projects/{key}/runs/{id}`, `/projects/{key}/tests/{id}`, `/projects/{key}/signatures/{id}`.

## Ключевые решения

- **Монорепо с тремя модулями.** Так видно, что фреймворк переиспользуется, а сборка и CI остаются одни.
- **Синхронный приём в одной транзакции.** Для объёмов проекта очередь не нужна, а ответ сразу содержит вердикт.
  История тестов читается одним запросом с `row_number() over (partition by test_case_id ...)` (порциями по 1000 id).
- **Вердикт считается по истории до текущего прогона**, а флаг `flaky` теста по истории вместе с текущим.
  Если на этом коммите тест уже проходил, падение сразу получает `KNOWN_FLAKY`: код тот же.
- **Gate пересчитывается при каждом запросе**, так что карантин, добавленный после загрузки, сразу меняет решение.
  `test_run.decision` хранит решение на момент загрузки (его показывает главная дашборда).
- **StAX, DTD и внешние сущности выключены** (защита от XXE), размер ограничен `spring.servlet.multipart.*`.
- **Подпись** = тип + первая строка сообщения + до 3 кадров стека, кроме фреймворков и самого тестового класса
  (иначе одна причина у разных тестов давала бы разные подписи). Номера строк отбрасываются. У Playwright
  (`type="FAILURE"`) и pytest тип и суть берутся из строки `Error: ...` / `E   AssertionError: ...` в теле.
- **`test_case` и `test_result` используют SEQUENCE (`allocationSize=50`) и `hibernate.jdbc.batch_size=50`:**
  IDENTITY отключает батч-вставку.
- **Telegram после коммита и без влияния на ответ:** ошибки и таймауты Bot API только логируются. Токен вставляется
  в путь конкатенацией, потому что шаблон URI закодировал бы `:` в токене.
- **Зелёные прогоны в Telegram не отправляются** (`flaky-radar.telegram.notify-on-pass=false`).

## Правила кода

- Внедрение зависимостей только через конструктор, field injection запрещён (ArchUnit). Lombok не используем.
- DTO делаем через `record` и группируем во вложенные классы `*Dtos` фичи. JPA-сущности не попадают в контроллеры
  (ArchUnit). Контроллеры не обращаются к репозиториям напрямую (ArchUnit).
- Классы `@RestController` называются `*Controller`, классы `@Service` называются `*Service`.
- Исключения фичи — вложенные классы в `*Exceptions`; маппинг на HTTP в `GlobalExceptionHandler`, `title` по-русски.
- Сервисы помечаем `@Transactional(readOnly = true)` на классе и `@Transactional` на изменяющих методах.
- `ddl-auto: validate`, `open-in-view: false`. Схема меняется **только новой миграцией** Flyway
  (`V<n>__<описание>.sql`). Уже смёрженные миграции не редактируем. Для хэшей используем `varchar`, а не `char`,
  иначе Hibernate validate падает.
- `report` и `analysis` пишем на чистой Java: никаких Spring, JPA и `jakarta.*` (ArchUnit). Бины собираются
  в `config/AnalysisConfiguration`.
- Конфигурация задаётся через `@ConfigurationProperties` + record.
- Время берём из бина `Clock`, а не из `Instant.now()`.
- В шаблонах Thymeleaf вызываем методы record явно (`${run.total()}`) и не используем `T(...)`: в Thymeleaf 3.1
  он ограничен. Для подписей есть `@summaryRenderer.verdictText(...)`.
- Комментарии в коде пишем по-русски и только там, где неочевидно «почему».

## Правила тестов

- Теги: `unit`, `slice`, `api`, `architecture`, `smoke`, `demo` (намеренно падают), `flaky-demo` (нестабилен
  нарочно, для dogfooding). `demo` и `flaky-demo` исключены по умолчанию (`excludedGroups` в родительском pom).
- API-тесты Flaky Radar наследуют `BaseRadarApiTest` (`@RadarApiTest`): перед каждым тестом `DatabaseCleaner`,
  сброс `TelegramMock` и свежий проект с ключом. Запросы идут **только через `RadarClient` и `RadarSteps`**.
  Загрузка описывается через `RunUpload.of(JUnitXmlBuilder...)` с методами `.commit()`, `.attempt()`,
  `.branch()`, `.ciRunId()`.
- Отчёты для тестов собираем через `JUnitXmlBuilder` (test-framework). Захардкоженные XML допустимы только как
  эталонные фикстуры парсера в `flaky-radar/src/test/resources/reports/` (surefire, playwright, pytest, xxe).
- Все API-тесты разделяют один Spring-контекст (одни и те же свойства), поэтому новые свойства нужно добавлять
  в `@RadarApiTest` или `BaseRadarApiTest`, а не в отдельный тест, иначе поднимется второй контейнер.
- `@DisplayName`, `@Epic/@Feature/@Story`, `@Step` пишем по-русски.
- Каждое правило вердикта, флаки и gate покрыто unit-тестом и API-сценарием. Добавляешь правило — добавь оба.

## Команды

Нужны JDK 25, Maven 3.9+ и запущенный Docker (Testcontainers).

```bash
mvn verify                                        # всё: тесты + покрытие
mvn -pl flaky-radar -am test -Dgroups=unit        # один модуль, один тег (-am нужен, чтобы собрать test-framework)
mvn -pl flaky-radar -am test -Dtest=QuarantineGateApiTest -Dsurefire.failIfNoSpecifiedTests=false
docker compose up -d radar-db                     # PostgreSQL для сервиса на порту 5433
mvn -pl flaky-radar -am spring-boot:run           # дашборд http://localhost:8080, Swagger /swagger-ui.html
mvn -pl flaky-radar allure:serve                  # отчёт
mvn -pl flaky-radar -am -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
```

- Без `-javaagent:aspectjweaver` `@Step` не попадает в Allure (агент подключён в surefire). `aop.xml` в каждом
  модуле включает `ru.study..*` и `io.qameta.allure..*`.
- Путь к проекту содержит кириллицу, поэтому на Windows JaCoCo пишет `jacoco.exec` в каталог с искажённым именем,
  и `jacoco:check` пропускается. Чтобы честно проверить покрытие локально, скопируй проект в путь только из ASCII.
- Логи с кириллицей в консоли Maven на Windows выглядят как `����`: это кодировка консоли, а не ошибка.

## Чего не делать

- Не редактировать уже применённые миграции Flyway.
- Не добавлять зависимости без явной причины (особенно Lombok, MapStruct и брокеры сообщений).
- Не коммитить `target/`, `ajcore.*.txt`, `.allure/`.
- Не включать тесты `demo` и `flaky-demo` в обычный прогон и в CI-gate.
- Не ломать формат ответа `/gate` и `/summary.md`: на них завязаны `scripts/flaky-radar-upload.sh` и CI.

## Состояние и что дальше

Сделано: модули, сервис целиком (приём, история, флаки, подписи, вердикты, gate, карантин, дашборд, `summary.md`,
Telegram), тесты всех уровней, CI с dogfooding и комментарием в PR, Docker-образ и `docker-compose`.

Возможные следующие шаги (после защиты или если останется время): графики трендов на дашборде, удаление старой
истории по сроку, вебхук в Slack, учёт длительности (медленные тесты), авторизация на чтение.
