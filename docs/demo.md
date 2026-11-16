# Сценарий демонстрации Flaky Radar (около 7 минут)

## Подготовка (до защиты)

Нужны Docker, JDK 25, `curl` и `jq` (скрипт загрузки использует `jq`).

```bash
mvn -pl flaky-radar -am package -DskipTests
docker compose up -d radar-db
FLAKY_RADAR_ADMIN_TOKEN=demo-admin java -jar flaky-radar/target/flaky-radar.jar
mvn -pl library-demo -am test            # свежие surefire-отчёты Библиотеки
```

Открыть вкладки: дашборд `http://localhost:8080`, Swagger `http://localhost:8080/swagger-ui.html`,
GitHub Actions (последний прогон джоба `dogfood`) и Allure-отчёт на GitHub Pages.

> На Windows передавай JSON с кириллицей через файл (`--data-binary @file.json`), а пути к отчётам
> в `-F report=@...` пиши относительными: `curl.exe` из Git Bash не понимает пути вида `/tmp/...`.

## 1. Проблема (30 секунд)

«Тест упал. Это мы что-то сломали или он опять моргнул? Сейчас это решают вручную: перезапускают CI
и смотрят историю. Flaky Radar отвечает на этот вопрос автоматически.»

## 2. Подключение проекта (1 минута)

```bash
curl -X POST localhost:8080/api/v1/projects -H "X-Admin-Token: demo-admin" \
  -H "Content-Type: application/json" -d '{"key":"library-demo","name":"Library demo"}'
export RADAR_URL=http://localhost:8080 RADAR_PROJECT=library-demo RADAR_API_KEY=frk_...
```

Показать: ключ выдаётся один раз, в БД лежит только его хэш.

## 3. Перезапуск CI на одном коммите (2 минуты)

Подготовить два каталога отчётов: в `rep1` демо-тест упал, в `rep2` прошёл
(можно запустить `mvn -pl library-demo -am test -DexcludedGroups=demo` с `LIBRARY_DEMO_FLAKY=fail` и без неё,
копируя `library-demo/target/surefire-reports`).

```bash
RADAR_COMMIT=c0ffee1 RADAR_CI_RUN=1 RADAR_ATTEMPT=1 RADAR_ENFORCE=false scripts/flaky-radar-upload.sh rep1   # FAIL
RADAR_COMMIT=c0ffee1 RADAR_CI_RUN=1 RADAR_ATTEMPT=1 RADAR_ENFORCE=false scripts/flaky-radar-upload.sh rep1   # тот же прогон
RADAR_COMMIT=c0ffee1 RADAR_CI_RUN=1 RADAR_ATTEMPT=2 scripts/flaky-radar-upload.sh rep2                       # PASS
```

Показать на дашборде: тест в списке «Нестабильные тесты», полоска истории красный → зелёный.
Подчеркнуть: повторная загрузка не создала дубль (идемпотентность по `ciRunId + attempt`).

## 4. Следующий коммит: флаки больше не блокирует (1 минута)

```bash
RADAR_COMMIT=beef002 RADAR_CI_RUN=2 RADAR_ATTEMPT=1 scripts/flaky-radar-upload.sh rep1   # WARN, exit 0
cat radar-summary.md
```

Вердикт «известный флаки», gate `WARN`, CI зелёный. Эта же сводка уходит в комментарий PR и в Telegram.

## 5. Группа ошибок и карантин (1 минута)

- Страница «Группы ошибок»: порт `localhost:45123` в подписи стал `<PORT>`, поэтому падения из разных прогонов
  попали в одну группу.
- В Swagger отправить тест в карантин (`POST /quarantine`) и показать, что gate старого прогона
  пересчитался.

## 6. Как это протестировано (1,5 минуты)

- Монорепо: `test-framework` переиспользуется в двух проектах (Testcontainers, RestAssured + Allure,
  `JUnitXmlBuilder`, снимок таблиц при падении, общие ArchUnit-правила).
- Allure-отчёт: ~145 тестов сервиса, шаги на русском, HTTP-запросы во вложениях.
- Покрытие строк ~96%, PIT на чистой логике, ArchUnit запрещает Spring в `analysis`/`report` и циклы между фичами.
- CI: джоб `dogfood` прогоняет тесты Библиотеки через Flaky Radar и комментирует PR.

## Запасные вопросы

- **Почему не очередь?** Отчёт на сотни тестов обрабатывается за доли секунды в одной транзакции, а CI сразу
  получает вердикт. Очередь добавила бы сложности без пользы на таком объёме.
- **Почему SEQUENCE, а не IDENTITY?** IDENTITY отключает батч-вставку в Hibernate.
- **XXE?** DTD и внешние сущности в StAX выключены, есть тест с вредоносным отчётом.
- **Почему кадры тестового класса не входят в подпись?** Иначе одна причина («стенд недоступен»)
  у 20 тестов дала бы 20 разных групп.
