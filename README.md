# Hotel Booking — микросервисная система бронирования отелей

REST API бронирования отелей на Spring Boot 3.5 и Spring Cloud 2025.0:

- **API Gateway** — маршрутизация, проверка JWT на входе, трассировка;
- **Eureka** — обнаружение сервисов;
- **Hotel Service** — отели, номера, занятость, статистика;
- **Booking Service** — пользователи, JWT, брони и **сага** согласованности с Hotel Service.

Ключевые свойства:
- **Сага** с компенсацией и восстановлением зависших броней.
- **Устойчивые вызовы**: Timeout, Retry с экспоненциальной паузой, CircuitBreaker.
- **Идемпотентность** по `requestId`.
- **Корректная обработка параллельных броней**: блокировка строки номера.
- **Равномерная загрузка номеров**: рекомендации по `times_booked`.

## Содержание

1. [Архитектура](#архитектура)
2. [Быстрый старт](#быстрый-старт)
3. [Демо-данные и сценарий проверки](#демо-данные-и-сценарий-проверки)
4. [API](#api)
5. [Сага бронирования](#сага-бронирования)
6. [Алгоритм планирования занятости](#алгоритм-планирования-занятости)
7. [Безопасность](#безопасность)
8. [Ошибки](#ошибки)
9. [Логи и трассировка](#логи-и-трассировка)
10. [База данных](#база-данных)
11. [Тестирование](#тестирование)
12. [Конфигурация](#конфигурация)
13. [Структура репозитория и документация](#структура-репозитория-и-документация)
14. [Где что реализовано (критерии)](#где-что-реализовано)

## Архитектура

```text
                    Клиент
                      │  HTTP + JWT
                      ▼
   ┌─────────────────────────────────────┐
   │          API Gateway :8080          │ ── поиск сервисов ──► Eureka :8761
   │  проверка JWT · X-Trace-Id · lb://  │
   └──────┬───────────────────────┬──────┘
          │ /api/bookings/**      │ /api/hotels/**, /api/rooms/**
          ▼                       ▼
   ┌──────────────┐        ┌──────────────┐
   │   Booking    │  HTTP  │    Hotel     │
   │    :8081     │ ─────► │    :8082     │
   └──────┬───────┘        └──────┬───────┘
          ▼                       ▼
     H2 bookingdb             H2 hoteldb
```

- Booking → Hotel: шаги саги `confirm-availability` и `release` со служебным JWT, тайм-аутами, Retry и CircuitBreaker.
- Gateway, Booking и Hotel регистрируются в Eureka. Gateway и Booking находят сервисы по имени (`lb://hotel-service`, `http://hotel-service`), а не по адресу.

| Компонент | Порт | Ответственность |
|---|---|---|
| `eureka-server` | 8761 | реестр сервисов |
| `api-gateway` | 8080 | единая точка входа: маршруты через Eureka, проверка JWT, 404 для внутренних эндпойнтов, `X-Trace-Id`, access-лог, общий Swagger UI |
| `hotel-service` | 8082 | CRUD отелей и номеров, фильтры, свободные и рекомендованные номера, блокировки слотов (шаг саги), статистика загрузки |
| `booking-service` | 8081 | регистрация и вход (выпуск JWT), администрирование пользователей, брони, сага и фоновое восстановление |
| `common` | — | общая библиотека: `ErrorDto`, обработка ошибок, JWT, автоконфигурация resource server, OpenAPI, валидация дат |

У каждого сервиса своя база H2 в памяти (database per service). Сервисы общаются только по HTTP.

**Стек:**
- Java 21 (собирается и запускается на JDK 21–25);
- Spring Boot 3.5.16, Spring Cloud 2025.0.3 (Gateway Server WebFlux, Netflix Eureka, LoadBalancer);
- Spring Data JPA + H2, Spring Security OAuth2 Resource Server (JWT HS256);
- Resilience4j 2.4, Micrometer Tracing (Brave);
- springdoc-openapi 2.8, Lombok, MapStruct;
- JUnit 5, MockMvc, WebTestClient, WireMock, Mockito;
- Spotless + palantir-java-format.

## Быстрый старт

### Требования

- **JDK 21 или новее** (`java -version`). Maven ставить не нужно: в репозитории есть Maven Wrapper (`mvnw` / `mvnw.cmd`). Остальные библиотеки скачиваются при первой сборке, нужен интернет.
- Свободные порты 8080, 8081, 8082, 8761.

### Сборка и тесты

```bash
./mvnw clean install          # Linux/macOS
.\mvnw.cmd clean install      # Windows
```

Команда собирает все модули, проверяет форматирование (Spotless) и прогоняет 133 теста.

### Запуск

**Вариант 1 — скрипты** (после сборки):

```bash
scripts/start-all.sh          # Linux/macOS; остановка: scripts/stop-all.sh
.\scripts\start-all.ps1       # Windows;     остановка: .\scripts\stop-all.ps1
```

**Вариант 2 — IntelliJ IDEA.** Откройте корневой `pom.xml` как проект и запустите конфигурацию **All services** из папки `.run/`.

**Вариант 3 — вручную**, каждый сервис в своём терминале, Eureka первой:

```bash
java -jar eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar
java -jar hotel-service/target/hotel-service-1.0.0-SNAPSHOT.jar
java -jar booking-service/target/booking-service-1.0.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
```

Сервисы регистрируются в Eureka примерно за 10 секунд. До этого Gateway может отвечать `503`.

### Полезные адреса

| Что | Адрес |
|---|---|
| API (через Gateway) | http://localhost:8080 |
| Swagger UI всей системы | http://localhost:8080/swagger-ui.html (сервис выбирается вверху, токен — кнопкой **Authorize**) |
| Панель Eureka | http://localhost:8761 |
| Swagger отдельных сервисов | http://localhost:8081/swagger-ui.html, http://localhost:8082/swagger-ui.html |
| H2 Console | http://localhost:8081/h2-console (`jdbc:h2:mem:bookingdb`), http://localhost:8082/h2-console (`jdbc:h2:mem:hoteldb`), пользователь `sa`, пароль пустой. Только напрямую на порт сервиса: через Gateway консоль не публикуется |

## Демо-данные и сценарий проверки

При запуске активен профиль `demo`: обе базы заполняются согласованными данными. Даты отсчитываются от текущего дня, поэтому сценарии работают в любой день.

| Пользователь | Пароль | Роль |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `alice` | `alice123` | USER |
| `bob` | `bob123` | USER |

**Отели и номера:**
- 3 отеля: Moscow, Saint Petersburg, Kazan; 12 номеров с разными `times_booked`.
- Номер `105` (id 5) на ремонте (`available=false`) и никогда не предлагается.
- `GET /api/rooms/recommend?hotelId=1` на свободные даты возвращает номера в порядке 103 (0), 102 (1), 104 (1 — при равенстве решает id), 101 (3).

**Брони:**
- 1 — alice, номер 101, через 10–13 дней, CONFIRMED. Бронь номера 101 на пересекающиеся даты получает `409`.
- 2 — bob, номер 102, через 10–12 дней, CONFIRMED.
- 3 — alice, прошедшее проживание: отменить нельзя (`BOOKING_COMPLETED`).
- 4 — bob, отменена пользователем.
- 5 — alice, отменена сагой из-за недоступности Hotel (компенсация).

Полный сценарий из 21 запроса (вход, рекомендации, бронь с автоподбором, идемпотентный повтор, 409, 400, 403, отмена, 404 для внутренних путей и H2 Console, статистика) лежит в **[docs/http/demo-scenario.http](docs/http/demo-scenario.http)**. Его можно выполнить в IntelliJ IDEA HTTP Client, окружение `local`.

Тот же сценарий через `curl`:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/bookings/user/auth -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"alice123"}' | jq -r .accessToken)

# рекомендованные номера отеля 1: по возрастанию times_booked, затем id
curl -s "localhost:8080/api/rooms/recommend?hotelId=1&startDate=2030-01-10&endDate=2030-01-12" \
  -H "Authorization: Bearer $TOKEN"

# бронь с автоподбором: 201 CONFIRMED (повтор с тем же requestId вернёт 200 и ту же бронь)
curl -s -X POST localhost:8080/api/bookings/booking -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"3f1c2d4e-5b6a-4c7d-8e9f-0a1b2c3d4e5f","autoSelect":true,"hotelId":1,
       "startDate":"2030-01-10","endDate":"2030-01-12"}'

# тот же номер (103, id 3) на пересекающиеся даты другим запросом: 409 ROOM_ALREADY_BOOKED
curl -s -X POST localhost:8080/api/bookings/booking -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"9b2e1c3d-0000-4c7d-8e9f-0a1b2c3d4e5f","autoSelect":false,"roomId":3,
       "startDate":"2030-01-11","endDate":"2030-01-13"}'

curl -s "localhost:8080/api/bookings/bookings" -H "Authorization: Bearer $TOKEN"   # мои брони
```

## API

Ниже указаны внешние пути (через Gateway). Внутри Booking Service путь без префикса `/api/bookings`. Описание полей и примеры — в Swagger UI.

### Booking Service (`/api/bookings/**`)

| Метод и путь | Доступ | Описание | Коды |
|---|---|---|---|
| `POST /api/bookings/user/register` | публичный | регистрация (роль USER), ответ — токен | 201, 400, 409 |
| `POST /api/bookings/user/auth` | публичный | вход, ответ — JWT на 1 час | 200, 401 |
| `GET /api/bookings/user` · `GET /api/bookings/user/{id}` | ADMIN | пользователи (пагинация) | 200, 403, 404 |
| `POST /api/bookings/user` | ADMIN | создать пользователя с ролью | 201, 400, 409 |
| `PATCH /api/bookings/user/{id}` | ADMIN | изменить имя, пароль или роль | 200, 400, 404, 409 |
| `DELETE /api/bookings/user/{id}` | ADMIN | удалить (нельзя себя и пользователя с предстоящими бронями) | 204, 409 |
| `POST /api/bookings/booking` | USER | создать бронь: `roomId` или `autoSelect: true` | 201, 200 (повтор), 400, 404, 409, 503 |
| `GET /api/bookings/bookings?status&page&size&sort` | USER | история **своих** броней | 200 |
| `GET /api/bookings/booking/{id}` | владелец / ADMIN | бронь с историей статусов | 200, 403, 404 |
| `DELETE /api/bookings/booking/{id}` | владелец / ADMIN | отменить бронь и освободить номер, идемпотентно | 204, 403, 404, 409 |

Тело `POST /booking`:

```json
{
  "requestId": "3f1c2d4e-5b6a-4c7d-8e9f-0a1b2c3d4e5f",
  "autoSelect": false,
  "roomId": 3,
  "hotelId": null,
  "startDate": "2030-01-10",
  "endDate": "2030-01-12"
}
```

- `requestId` — ключ идемпотентности, генерирует клиент.
- При `autoSelect: true` поле `roomId` игнорируется; `hotelId` по желанию ограничивает подбор одним отелем.
- Даты задают полуинтервал `[startDate, endDate)`: день выезда свободен. Заезд не раньше сегодняшнего дня, проживание не дольше 30 ночей.

### Hotel Service (`/api/hotels/**`, `/api/rooms/**`)

| Метод и путь | Доступ | Описание |
|---|---|---|
| `GET /api/hotels?city&name&minStars&page&size&sort` | USER | отели с фильтрами |
| `GET /api/hotels/{id}` | USER | отель |
| `POST /api/hotels` · `PUT /api/hotels/{id}` · `DELETE /api/hotels/{id}` | ADMIN | CRUD отелей |
| `GET /api/rooms?hotelId&city&type&minCapacity&maxPrice&startDate&endDate&page&sort` | USER | свободные номера (без специальной сортировки) |
| `GET /api/rooms/recommend?hotelId&startDate&endDate&…` | USER | те же номера (с теми же фильтрами, даты необязательны) по возрастанию `times_booked`, при равенстве — по `id` |
| `GET /api/rooms/{id}` | USER | номер |
| `POST /api/rooms` · `PUT /api/rooms/{id}` · `DELETE /api/rooms/{id}` | ADMIN | CRUD номеров |
| `GET /api/rooms/stats?hotelId&sort` | ADMIN | `times_booked` и предстоящие брони по номерам |
| `GET /api/hotels/stats` | ADMIN | равномерность загрузки по отелям: min, max и среднее `times_booked`, разброс, простаивающие номера |
| `GET /api/hotels/{id}/occupancy?from&to` | ADMIN | загрузка за период: room-nights, %, по номерам и по дням |
| `POST /api/rooms/{id}/confirm-availability` | INTERNAL | шаг саги: блокировка слота. **Через Gateway не публикуется** |
| `POST /api/rooms/{id}/release` | INTERNAL | компенсация: снятие блокировки. **Через Gateway не публикуется** |

### Отличия от примерной структуры эндпойнтов из ТЗ

ТЗ приводит структуру эндпойнтов как примерную. Отличия и их причины:

| В ТЗ | В проекте | Почему |
|---|---|---|
| `PATCH /user`, `DELETE /user` | `PATCH /user/{id}`, `DELETE /user/{id}` | пользователь, которого меняют или удаляют, задаётся в пути, как принято в REST; без id из запроса не понять, о ком речь |
| — | `GET /user`, `GET /user/{id}`; `GET`, `PUT`, `DELETE` для отелей и номеров | полный CRUD отелей, номеров и пользователей для администратора, как в функциональных требованиях |
| маршруты Gateway `/api/bookings/**`, `/api/hotels/**` | ещё `/api/rooms/**` → Hotel Service | эндпойнты номеров из ТЗ начинаются с `/api/rooms` |
| «Booking Service возвращает список номеров, отсортированный по `times_booked`» | список строит Hotel Service (`GET /api/rooms/recommend`), Booking Service получает его при автоподборе (`BookingSaga.candidates`) | `times_booked` и занятость по датам хранятся в Hotel Service; отдельный эндпойнт в Booking только повторял бы тот же ответ |

## Сага бронирования

```text
POST /api/bookings/booking {requestId, ...}
   │
   ▼
1. Booking: бронь PENDING (отдельная транзакция)
   │
   ▼
2. autoSelect = true? ── да ──► Hotel: GET /api/rooms/recommend → кандидаты по times_booked ↑, id ↑
   │
   ▼
3. Hotel: POST /api/rooms/{id}/confirm-availability (requestId = hold-{bookingId}-{roomId})
   блокировка строки номера → проверка пересечений дат → резерв HELD, times_booked + 1
   │
   ├── 200 HELD ───────────────────► CONFIRMED, ответ 201
   │
   ├── 409 номер занят ────────────► autoSelect: следующий кандидат (заняты все → 409 NO_ROOMS_AVAILABLE);
   │                                 иначе CANCELLED, ответ 409 (компенсация не нужна)
   │
   └── тайм-аут / 5xx / CB открыт ─► CANCELLED + release с тем же requestId (компенсация),
       (после 3 попыток)             ответ 503 HOTEL_UNAVAILABLE
```

```text
PENDING ──(слот заблокирован)──► CONFIRMED ──(DELETE /booking/{id} + release)──► CANCELLED
   │                                                                                 ▲
   └──(номер занят · сбой или тайм-аут Hotel + release · зависшая сага)──────────────┘
```

**Гарантии:**

- **Последовательность локальных транзакций, без 2PC.** Бронь `PENDING` фиксируется до вызова Hotel. Оркестратор `BookingSaga` не держит транзакцию во время HTTP-вызова (ADR-0001).
- **Устойчивость вызовов** (ADR-0003):
  - тайм-ауты: соединение 1 с, ответ 2 с;
  - Retry: 3 попытки, пауза 200 → 400 мс, только при недоступности Hotel;
  - CircuitBreaker: окно 10, порог 50 %, OPEN 10 с;
  - когда попытки исчерпаны, выполняется компенсация.
- **Идемпотентность** (ADR-0002):
  - `UNIQUE (user_id, request_id)` в Booking и `UNIQUE (request_id)` в Hotel;
  - тот же `requestId` с другими датами или номером отклоняется: `422 IDEMPOTENCY_KEY_REUSED`;
  - все повторы и компенсация используют один ключ `hold-{bookingId}-{roomId}`;
  - если `release` пришёл раньше блокировки, Hotel сохраняет «надгробие», и запоздавшая блокировка отклоняется.
- **Без гонок.** Переходы статуса — условные UPDATE (`... WHERE status = :expected`) с `@Version`. Сага, отмена пользователем и фоновая задача не перетирают результаты друг друга.
- **Надёжная компенсация.** Если `release` не дошёл (Hotel недоступен), бронь остаётся `CANCELLED` с `compensation_pending = true`. `SagaRecoveryJob` (раз в 30 с) повторяет доставку и отменяет брони, зависшие в `PENDING` дольше минуты. Окончательный отказ Hotel (например, `404`: номера уже нет) означает, что освобождать нечего, и такой `release` не повторяется бесконечно. Пользователя с недоставленным `release` удалить нельзя (`409`), иначе каскадное удаление потеряло бы компенсацию.
- **Аудит.** Каждый переход пишется в `booking_status_history`; он виден в `GET /booking/{id}` и в логах.

## Алгоритм планирования занятости

1. Hotel Service ведёт счётчик `times_booked`: +1 при успешной блокировке слота, −1 при её снятии. Счётчик меняется в той же транзакции, что и блокировка, под блокировкой строки номера.
2. `GET /api/rooms/recommend` возвращает свободные на даты работающие номера, `ORDER BY times_booked ASC, id ASC`: один JPQL-запрос с `NOT EXISTS` по пересекающимся блокировкам и индекс `(times_booked, id)`.
3. При `autoSelect` Booking Service перебирает рекомендованные номера по порядку, не больше `app.saga.max-candidates` (20). Если номер только что занял параллельный запрос (409), бронируется следующий. Так нагрузка распределяется по номерам, и номер с наименьшим `times_booked` не простаивает. Параллельные запросы на одни даты получают один и тот же порядок, поэтому k-й из них бронирует k-й номер списка: лимит задаёт, сколько таких запросов обслуживается одновременно. Если заняты все кандидаты, ответ — `409 NO_ROOMS_AVAILABLE`, бронь отменяется без компенсации (ничего не блокировалось).
4. Параллельные брони одного номера сериализует `SELECT ... FOR UPDATE` (ADR-0004): из N одновременных попыток успешна ровно одна, остальные получают `409`.

Свойства подтверждены тестами (`RoomAllocationConcurrencyTest`):
- 20 параллельных броней одного номера → 1 успех и 19 ответов 409, `times_booked = 1`;
- 20 параллельных дубликатов одного запроса → одна запись;
- 30 последовательных автоподборов на 10 номеров → у каждого номера `times_booked = 3`;
- 10 параллельных автоподборов на одни даты → 10 разных номеров.

Сквозная проверка через Gateway на демо-данных: 12 параллельных броней с `autoSelect` на 12 свободных номеров → 12 × `201`, у каждой свой номер.

Насколько равномерна загрузка, показывают `GET /api/hotels/stats` (разброс `max − min times_booked`, простаивающие номера) и `GET /api/hotels/{id}/occupancy`.

## Безопасность

- **JWT HS256.** Срок жизни — 1 час. Claims: `sub`, `uid`, `roles`, `iss`. Токены выпускает Booking Service (`/user/register`, `/user/auth`), пароли хранятся как BCrypt-хэши. Пароль — от 6 до 72 символов и не длиннее 72 байт в UTF-8 (предел BCrypt; кириллический символ занимает 2 байта), иначе `400`.
- **Gateway** проверяет токен на входе. Без токена доступны только регистрация, вход, Swagger и health. Внутренние эндпойнты саги и H2 Console Booking Service (`/api/bookings/h2-console/**`) не публикуются: 404 при любом написании пути (со слешем на конце; `//`, `;` и закодированные символы отклоняет firewall Spring Security с `400`). Остальные `/actuator/**` закрыты, а заголовок `X-Booking-Id` от клиента удаляется.
- **Каждый сервис** — самостоятельный OAuth2 Resource Server и проверяет подпись, срок и издателя ещё раз.
  - Роли проверяются на методах через `@PreAuthorize`; иерархия `ADMIN > USER`.
  - Проверка «только свои брони» сделана в сервисном слое.
- **Вызовы Booking → Hotel** идут со служебным токеном с ролью `INTERNAL` (живёт 1 минуту, выпускается на каждый запрос). Пользовательский токен на внутренние эндпойнты получает 403.
- **Коды ответов:** 401 — нет токена или он неверный/просрочен (`UNAUTHORIZED` / `INVALID_TOKEN`, заголовок `WWW-Authenticate: Bearer`); 403 — недостаточно прав (`ACCESS_DENIED`).
- Подробнее — в ADR-0006. Секрет задаётся переменной `JWT_SECRET`, одинаковой для всех сервисов.

## Ошибки

Все сервисы и Gateway возвращают ошибки в едином формате `ErrorDto` (Gateway — и для своих ошибок: неизвестный путь, недоступный или медленный сервис, отклонённый URL):

```json
{
  "timestamp": "2026-10-20T01:48:36.376Z",
  "status": 409,
  "error": "Conflict",
  "code": "ROOM_ALREADY_BOOKED",
  "message": "Room 1 is already booked for the requested dates",
  "path": "/booking",
  "traceId": "6ad6c86d5b1ecf17e8071ffcde0e61e6",
  "context": { "bookingId": 101 }
}
```

При ошибке валидации добавляется `violations: [{ "field": "endDate", "message": "endDate must be after startDate" }]`.

Значение не того типа (не UUID, не число, неизвестное значение enum, дата не в ISO) тоже считается ошибкой валидации поля: `{ "field": "requestId", "message": "must be a UUID" }`, `{ "field": "type", "message": "must be one of [STANDARD, DELUXE, SUITE]" }`. Сообщения всегда на английском и не раскрывают устройство сервиса: в них нет имён Java-классов и сигнатур методов.

| Статус | Коды |
|---|---|
| 400 | `VALIDATION_FAILED` (в том числе значение не того типа), `MALFORMED_REQUEST` (тело отсутствует или не JSON; URL, отклонённый firewall), `INVALID_SORT_PROPERTY` |
| 401 | `UNAUTHORIZED`, `INVALID_TOKEN`, `INVALID_CREDENTIALS` |
| 403 | `ACCESS_DENIED` |
| 404 | `NOT_FOUND`, `HOTEL_NOT_FOUND`, `ROOM_NOT_FOUND`, `BOOKING_NOT_FOUND`, `USER_NOT_FOUND` |
| 405, 415 | `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`: у ошибок без собственного кода код — имя HTTP-статуса |
| 409 | `ROOM_ALREADY_BOOKED`, `ROOM_OUT_OF_SERVICE`, `NO_ROOMS_AVAILABLE`, `USERNAME_TAKEN`, `BOOKING_IN_PROGRESS`, `BOOKING_COMPLETED`, `USER_HAS_ACTIVE_BOOKINGS`, `HOTEL_HAS_ACTIVE_RESERVATIONS`, `ROOM_HAS_ACTIVE_RESERVATIONS`, `CONCURRENT_MODIFICATION`, … |
| 422 | `IDEMPOTENCY_KEY_REUSED` — `requestId` уже использован для брони с другими параметрами |
| 503 | `HOTEL_UNAVAILABLE` — Hotel Service недоступен, бронь отменена с компенсацией; `SERVICE_UNAVAILABLE` — Gateway не может достучаться до сервиса |
| 502, 504 | `BAD_GATEWAY` — сервис оборвал соединение; `GATEWAY_TIMEOUT` — сервис не ответил за 15 с (Gateway) |

## Логи и трассировка

- **Сквозной `traceId`** (Micrometer Tracing, W3C `traceparent`). Его создаёт Gateway и передаёт в Booking и дальше в Hotel. Он есть в каждой строке лога, в заголовке ответа `X-Trace-Id` и в поле `traceId` у ошибок.
- **Бизнес-корреляция `bookingId`.** Во время саги `bookingId` лежит в MDC Booking Service и передаётся в Hotel заголовком `X-Booking-Id`, который тоже попадает в MDC. Принимается только числовое значение, а Gateway удаляет этот заголовок из клиентских запросов, так что подделать `bookingId` в логах нельзя.
- **Структурные логи.** В консоль пишется читаемый формат, в `logs/<service>.json` — JSON в формате Elastic Common Schema: поля `traceId`, `spanId`, `bookingId` отдельные.
- **Ключевые события саги:** `SAGA_STARTED`, `AUTO_SELECT`, `HOLD_REQUESTED/CONFIRMED/REJECTED`, повторы, `PENDING -> CONFIRMED/CANCELLED`, `COMPENSATION_STARTED/DONE/DEFERRED/SKIPPED`, `RECOVERY`, смена состояния circuit breaker.

Одна бронь в логах трёх сервисов (реальный вывод, сокращено):

```
api-gateway      [6ad6c86d…,e8071ffc…] TracingFilter  : POST /api/bookings/booking -> 201 in 217 ms (route=booking-service, traceId=6ad6c86d…)
booking-service  [6ad6c86d…,cfb2b566…] BookingStateService : Booking 100 status -> PENDING (requestId=dda9d635-…)
booking-service  [6ad6c86d…,cfb2b566…] bookingId=100 BookingSaga : SAGA_STARTED: user=2, autoSelect=true, room=null, period=[2026-10-30, 2026-11-01)
booking-service  [6ad6c86d…,cfb2b566…] bookingId=100 BookingSaga : AUTO_SELECT: candidates=[3, 4]
booking-service  [6ad6c86d…,cfb2b566…] bookingId=100 BookingSaga : HOLD_REQUESTED: room=3, requestId=hold-100-3
hotel-service    [6ad6c86d…,17b35a7f…] bookingId=100 RoomAvailabilityServiceImpl : Slot HELD: room=3, period=[2026-10-30, 2026-11-01), bookingId=100, requestId=hold-100-3, timesBooked=1
booking-service  [6ad6c86d…,cfb2b566…] bookingId=100 BookingSaga : HOLD_CONFIRMED: room=3, reservation=100
booking-service  [6ad6c86d…,cfb2b566…] bookingId=100 BookingStateService : Booking 100 status PENDING -> CONFIRMED
booking-service  [6ad6c86d…,cfb2b566…] bookingId=100 BookingSaga : SAGA_FINISHED: CONFIRMED in 158 ms
```

Компенсация при сбое Hotel:

```
WARN  bookingId=34 HotelClientConfig   : Hotel Service call failed (attempt 1), retrying in 200 ms: … Request cancelled
WARN  bookingId=34 HotelClientConfig   : Hotel Service call failed after 3 attempts: …
INFO  bookingId=34 BookingStateService : Booking 34 status PENDING -> CANCELLED (reason=HOTEL_UNAVAILABLE)
INFO  bookingId=34 CompensationService : COMPENSATION_STARTED: releasing room=7 requestId=hold-34-7
INFO  bookingId=34 CompensationService : COMPENSATION_DONE: room=7 released
```

## База данных

Схемы задаются явными скриптами (`*/src/main/resources/db/schema.sql`), Hibernate их только проверяет (`ddl-auto=validate`).

**Booking Service (`bookingdb`)**

| Таблица | Поля | Ключи и ограничения |
|---|---|---|
| `users` | `id`, `username`, `password` (BCrypt-хэш), `role` (`USER` / `ADMIN`), `created_at`, `updated_at` | PK `id`; UNIQUE `username` |
| `bookings` | `id`, `user_id`, `request_id` (UUID), `room_id`, `hotel_id`, `auto_select`, `start_date`, `end_date`, `status` (`PENDING` / `CONFIRMED` / `CANCELLED`), `cancel_reason`, `compensation_pending`, `created_at`, `updated_at`, `version` | FK `user_id` → `users` (ON DELETE CASCADE); UNIQUE (`user_id`, `request_id`); CHECK `end_date > start_date`. `room_id` и `hotel_id` — идентификаторы из Hotel Service, без FK: это другая база |
| `booking_status_history` | `id`, `booking_id`, `from_status`, `to_status`, `reason`, `changed_at` | FK `booking_id` → `bookings` (ON DELETE CASCADE) |

**Hotel Service (`hoteldb`)**

| Таблица | Поля | Ключи и ограничения |
|---|---|---|
| `hotels` | `id`, `name`, `city`, `address`, `stars` (1–5), `created_at`, `updated_at` | PK `id`; CHECK `stars` от 1 до 5 |
| `rooms` | `id`, `hotel_id`, `number`, `type` (`STANDARD` / `DELUXE` / `SUITE`), `capacity`, `price_per_night`, `available` (операционная доступность), `times_booked`, `version`, `created_at`, `updated_at` | FK `hotel_id` → `hotels` (ON DELETE CASCADE); UNIQUE (`hotel_id`, `number`); CHECK `capacity > 0`, `price_per_night >= 0`, `times_booked >= 0` |
| `room_reservations` | `id`, `room_id`, `booking_id`, `request_id` (ключ идемпотентности), `start_date`, `end_date`, `status` (`HELD` / `RELEASED`), `created_at`, `released_at` | FK `room_id` → `rooms` (ON DELETE CASCADE); UNIQUE `request_id`; CHECK `end_date > start_date`. `booking_id` — идентификатор из Booking Service |

Помимо минимальной схемы из ТЗ добавлено:

- **Индексы:**
  - `bookings(user_id, created_at)` — история броней;
  - `bookings(status, created_at)` — фоновая задача;
  - `bookings(room_id, start_date, end_date)`;
  - `rooms(times_booked, id)` — рекомендации;
  - `room_reservations(room_id, status, start_date, end_date)` — проверка пересечений;
  - `hotels(city)`.
- **Ограничения:**
  - CHECK на даты, статусы, роли, вместимость и цену;
  - UNIQUE для идемпотентности и номеров комнат;
  - внешние ключи с каскадным удалением.
- **Таблица `room_reservations`** — занятость по датам на стороне Hotel (ADR-0005).
- **Таблица `booking_status_history`** — аудит переходов.
- **Поле `version`** — оптимистическая блокировка.

`bookings.room_id` и `room_reservations.booking_id` — логические ссылки между сервисами без внешнего ключа: базы разные. Согласованность обеспечивает сага.

## Тестирование

```bash
./mvnw test                               # все 133 теста
./mvnw -pl booking-service -am test       # один сервис (с зависимостями)
./mvnw spotless:apply                     # отформатировать код (проверка — в ./mvnw verify)
```

Тесты не зависят от окружения:
- у каждого контекста своя H2 со случайным именем;
- Eureka отключена;
- Hotel Service для Booking заменён **WireMock** и подключён через тот же LoadBalancer, что и в работе;
- JWT настоящие, подписаны тестовым секретом.

Стиль тестов: Arrange-Act-Assert (given/when/then), `@DisplayName` на русском, фабрики тестовых данных (`HotelFixtures`, `BookingFixtures`, `TestTokens`).

| Модуль | Тесты | Что покрыто |
|---|---|---|
| booking-service | 59 | **сага:** успех, автоподбор с переходом к следующему кандидату, все кандидаты заняты → 409 `NO_ROOMS_AVAILABLE`, обрыв соединения и повтор с тем же `requestId`, 409 без компенсации, 500 × 3 → компенсация, тайм-аут → компенсация, отложенная компенсация и фоновая задача, `404` на `release` без бесконечных повторов, открытый CircuitBreaker, повторная и **параллельная** доставка одного запроса, тот же `requestId` с другими параметрами → 422, зависшая `PENDING`-бронь; валидация дат и типов значений (поле в ответе, без имён классов), `roomId` игнорируется при `autoSelect`; «только свои брони», пагинация, отмена; регистрация, вход, 401/403, пароль длиннее 72 байт → 400; администрирование (в том числе запрет удалить пользователя с недоставленным `release`); unit-тест декоратора Retry/CircuitBreaker (Mockito) |
| hotel-service | 51 | CRUD, фильтры, сортировка, запрет переносить номер с бронями в другой отель; **20 параллельных броней → ровно 1**; удаление номера дожидается параллельного удержания слота; параллельные дубликаты; равномерность автоподбора; идемпотентность и «надгробие»; 409 при пересечении или ремонте; бронь встык; рекомендации с датами и без; статистика и отчёт о загрузке; 401/403/400/404; неверный тип значения → 400 с именем поля; 400 вместо 401 для URL, отклонённого firewall |
| api-gateway | 14 | маршруты и `StripPrefix`, публичные пути, 401 (нет токена, поддельный, просроченный), 404 для внутренних эндпойнтов (и со слешем на конце) и H2 Console, закрытый actuator, ErrorDto для 404/400/502/504, удаление клиентского `X-Booking-Id`, сквозной `traceId` (`X-Trace-Id` = `traceparent`) |
| common | 8 | выпуск и проверка JWT, роли, срок жизни, валидатор дат |
| eureka-server | 1 | реестр поднимается |

## Конфигурация

| Переменная окружения | По умолчанию | Где |
|---|---|---|
| `JWT_SECRET` | dev-секрет из `application.yml` | gateway, booking, hotel (одинаковый, ≥ 32 байт) |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | gateway, booking, hotel |
| `GATEWAY_PORT`, `BOOKING_SERVICE_PORT`, `HOTEL_SERVICE_PORT`, `EUREKA_PORT` | 8080, 8081, 8082, 8761 | соответствующий сервис |
| `GATEWAY_TRUSTED_PROXIES` | localhost | gateway: от каких адресов передавать `X-Forwarded-*` |
| `LOG_DIR` | `logs` | каталог JSON-логов |

Профили: `demo` (по умолчанию — демо-данные и H2 Console) и `test` (используется в тестах). Параметры саги и устойчивости описаны в [booking-service/README.md](booking-service/README.md).

## Структура репозитория и документация

```
├─ pom.xml                 родительский POM: Spring Boot 3.5 parent, Spring Cloud BOM, Spotless
├─ mvnw, mvnw.cmd, .mvn/   Maven Wrapper
├─ common/                 общая библиотека ............ common/README.md
├─ eureka-server/          реестр сервисов ............. eureka-server/README.md
├─ api-gateway/            шлюз ........................ api-gateway/README.md
├─ hotel-service/          отели, номера, слоты ........ hotel-service/README.md
├─ booking-service/        пользователи, брони, сага ... booking-service/README.md
├─ docs/
│  ├─ adr/                 архитектурные решения
│  └─ http/                сценарий для IntelliJ HTTP Client
├─ scripts/                запуск и остановка всех сервисов
└─ .run/                   конфигурации запуска IntelliJ IDEA
```

Структура пакетов внутри сервиса: `api` (контроллеры, DTO) → `service` (интерфейсы и реализации) → `repository` → `domain`, плюс `mapper` (MapStruct), `config`; в Booking ещё `saga` и `client`.

**Архитектурные решения (ADR):**
- [0001 — Оркестрируемая сага вместо 2PC](docs/adr/0001-orchestrated-saga.md)
- [0002 — Идемпотентность и «надгробия»](docs/adr/0002-idempotency.md)
- [0003 — Timeout + Retry + CircuitBreaker](docs/adr/0003-resilience.md)
- [0004 — Блокировка строки номера при конкурентных бронях](docs/adr/0004-room-locking.md)
- [0005 — Занятость по датам в Hotel Service](docs/adr/0005-occupancy-projection.md)
- [0006 — JWT и проверка в Gateway и сервисах](docs/adr/0006-security.md)
- [0007 — Маршруты Gateway и модуль common](docs/adr/0007-gateway-routing.md)

## Где что реализовано

| Критерий | Реализация |
|---|---|
| Планирование занятости | `RoomServiceImpl.recommend`, `RoomAvailabilityServiceImpl` (блокировка строки, идемпотентность), автоподбор в `BookingSaga.candidates`; тесты `RoomAllocationConcurrencyTest` |
| Согласованность между сервисами | `BookingSaga`, `BookingStateService` (CAS), `CompensationService`, `SagaRecoveryJob`, `ResilientHotelClient` (Retry + CircuitBreaker), тайм-ауты в `HotelClientConfig`; тесты `BookingSagaTest` |
| Booking Service | `BookingController`, `BookingServiceImpl`: `requestId`, пагинация и фильтр, валидация дат (`@ValidDateRange`, `@FutureOrPresent`), проверка владельца |
| Hotel Service | фильтры (`RoomSpecifications`, `HotelSpecifications`), сортировка и пагинация, `StatisticsController` (`/stats`, `/occupancy`) |
| Gateway + Eureka | маршруты `lb://`, `GatewaySecurityConfig`, `TracingFilter` (`X-Trace-Id`, access-лог), `InternalEndpointsFilter`, `GatewayErrorHandler`, тайм-ауты HTTP-клиента |
| Безопасность | resource server в каждом сервисе (`common/autoconfigure`), `@PreAuthorize` + `Authorities`, иерархия ролей, JSON 401/403 |
| Тестирование | 133 теста: MockMvc, WebTestClient, WireMock, Mockito, параллельные сценарии |
| Структура БД | `db/schema.sql` обоих сервисов: доп. поля, индексы, FK, CHECK, UNIQUE; описание таблиц выше |
| Предзаполнение | `db/demo-data.sql`: согласованные данные под сценарии демонстрации |
| SOLID / DRY | интерфейсы сервисов и порт `HotelClient` с декоратором, тонкие контроллеры, MapStruct, общий модуль `common` |
| Код-стиль | Spotless + palantir-java-format (`./mvnw spotless:apply`, проверка в `verify`), `.editorconfig` |
| Ошибки | `ErrorDto`, `GlobalExceptionHandler`, `GatewayErrorHandler`, иерархия `ApiException`, единые коды (`ErrorCodes.forStatus`), понятные сообщения без деталей реализации |
| Логи и трассировка | Micrometer Tracing, `traceId`/`bookingId` в MDC, ECS JSON-логи, `X-Trace-Id` |
| Документация | этот README, README модулей, ADR, Swagger, `.http`-сценарий |
