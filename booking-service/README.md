# booking-service

Booking Service. Порт **8081**, база H2 `bookingdb` в памяти.

Отвечает за:
- пользователей: регистрацию, вход, администрирование;
- выпуск JWT;
- бронирования и сагу согласованности с Hotel Service.

## Пакеты

| Пакет | Содержимое |
|---|---|
| `api` | `AuthController`, `UserAdminController`, `BookingController`; DTO-records |
| `service` | интерфейсы `AuthService`, `UserService`, `BookingService` и реализации |
| `saga` | `BookingSaga` — оркестратор; `BookingStateService` — локальные транзакции и CAS-переходы статусов; `CompensationService`; `SagaRecoveryJob` |
| `client` | порт `HotelClient`: `RestHotelClient` (HTTP, таймауты, служебный токен) и декоратор `ResilientHotelClient` (Retry + CircuitBreaker) |
| `config` | `HotelClientConfig` (load-balanced `RestClient`), `SagaProperties`, `HotelClientProperties`, `SecurityBeansConfig`, `SchedulingConfig` |
| `domain` | `User`, `Booking`, `BookingStatusChange`, `BookingStatus`, `CancellationReason` |

## Эндпойнты

Через Gateway перед каждым путём ставится префикс `/api/bookings`.

| Метод и путь | Роль | Описание |
|---|---|---|
| `POST /user/register` | публичный | регистрация (роль USER), ответ — токен на 1 час |
| `POST /user/auth` | публичный | вход, ответ — токен; 401 `INVALID_CREDENTIALS` |
| `GET /user`, `GET /user/{id}` | ADMIN | пользователи |
| `POST /user` | ADMIN | создание пользователя с любой ролью |
| `PATCH /user/{id}` | ADMIN | частичное обновление: имя, пароль, роль |
| `DELETE /user/{id}` | ADMIN | удаление; 409, если есть предстоящие брони или если администратор удаляет сам себя |
| `POST /booking` | USER | бронирование: `roomId` или `autoSelect: true`; обязателен `requestId` |
| `GET /bookings?status&page&size&sort` | USER | история **своих** броней |
| `GET /booking/{id}` | USER (владелец) / ADMIN | бронь с историей статусов; чужая → 403 |
| `DELETE /booking/{id}` | USER (владелец) / ADMIN | отмена с освобождением слота; повторная отмена ничего не делает |

Swagger: http://localhost:8081/swagger-ui.html. H2 Console: http://localhost:8081/h2-console (`jdbc:h2:mem:bookingdb`, пользователь `sa`) — только напрямую, Gateway её не публикует.

## Настройки саги

| Свойство | По умолчанию | Смысл |
|---|---|---|
| `app.hotel-client.connect-timeout` / `read-timeout` | 1s / 2s | тайм-ауты одной попытки |
| `resilience4j.retry.instances.hotel-service.*` | 3 попытки, 200 мс × 2 | повторы только при недоступности Hotel |
| `resilience4j.circuitbreaker.instances.hotel-service.*` | окно 10, порог 50 %, OPEN 10 с | быстрый отказ, пока Hotel недоступен |
| `app.saga.max-candidates` | 20 | сколько рекомендованных номеров пробует автоподбор. Параллельные запросы на одни даты получают один порядок, и k-й из них бронирует k-й номер, так что значение ограничивает и стоимость запроса, и число одновременно обслуживаемых автоподборов |
| `app.saga.pending-timeout` | 1m | через сколько зависшая `PENDING`-бронь компенсируется |
| `app.saga.recovery-interval` | PT30S | период `SagaRecoveryJob` |

## Тесты

| Класс | Что проверяет |
|---|---|
| `BookingSagaTest` (WireMock вместо Hotel) | успех; автоподбор с переходом к следующему номеру; все кандидаты заняты → 409 `NO_ROOMS_AVAILABLE` без компенсации; обрыв соединения и повтор с тем же `requestId`; 409 без компенсации; 500 × 3 → компенсация; тайм-аут → компенсация; отложенная компенсация и фоновая задача; `404` на `release` без бесконечных повторов; открытый CircuitBreaker; повторная и параллельная доставка; тот же `requestId` с другими параметрами → 422; зависшая `PENDING`-бронь; валидация (при `autoSelect` поле `roomId` игнорируется; значение не того типа → 400 с именем поля; запрос без тела → `MALFORMED_REQUEST`) |
| `BookingApiTest` | «только свои брони», пагинация, фильтр (неизвестный статус → 400 со списком допустимых), 403/404, отмена (идемпотентная, при недоступном Hotel, завершённая, в процессе) |
| `AuthApiTest` | регистрация, токен на 1 час, вход, 401 для неверных, просроченных и поддельных токенов; пароль длиннее 72 байт → 400, а не 500 от BCrypt; сообщения валидации на английском |
| `UserAdminApiTest` | CRUD пользователей и ограничения на удаление (предстоящие брони, недоставленный `release`, удаление себя) |
| `ResilientHotelClientTest` (Mockito) | число повторов, отказ не повторяется, CircuitBreaker открывается |
