# api-gateway

Единая точка входа (Spring Cloud Gateway, WebFlux). Порт **8080**.

## Маршруты

| Внешний путь | Куда | Примечание |
|---|---|---|
| `/api/bookings/**` | `lb://booking-service` | `StripPrefix=2`: `/api/bookings/booking` → `/booking` |
| `/api/hotels/**`, `/api/rooms/**` | `lb://hotel-service` | путь без изменений |
| `/api/rooms/{id}/confirm-availability/**`, `/api/rooms/{id}/release/**` | — | **404**, внутренние шаги саги не публикуются (в том числе со слешем на конце) |
| `/api/bookings/h2-console/**` | — | **404**: без этого правила `StripPrefix` открыл бы H2 Console Booking Service (демо-профиль) любому пользователю с токеном |
| `/actuator/**`, кроме `health` и `info` | — | **403**, управляющие эндпойнты Gateway закрыты |

## Фильтры

| Порядок | Фильтр | Назначение |
|---|---|---|
| −300 | `TracingFilter` | заголовок `X-Trace-Id` в каждом ответе; access-лог «метод путь → статус, время, маршрут, traceId» |
| −200 | `InternalEndpointsFilter` | 404 для неопубликованных путей (шаги саги, H2 Console) раньше проверки токена |
| −100 | Spring Security | firewall (`//`, `/./`, `;`, закодированные разделители → 400); проверка JWT (HS256, срок, издатель); без токена доступны только регистрация, вход, Swagger и health |
| все маршруты | `RemoveRequestHeader=X-Booking-Id` | клиент не может подставить свой `bookingId` в логи сервисов |
| маршрут | `lb://` + `StripPrefix` | балансировка через Eureka; Micrometer передаёт сервису заголовок `traceparent` |

Ошибки самого Gateway приходят в общем формате `ErrorDto`:
- 401/403 — обработчики Spring Security;
- 400 `MALFORMED_REQUEST` — URL отклонён firewall (`ServerExchangeRejectedHandler`);
- 404 `NOT_FOUND` — неизвестный путь или неопубликованный эндпойнт;
- 503 `SERVICE_UNAVAILABLE` — у сервиса нет доступных экземпляров или соединение не устанавливается за 2 с;
- 502 `BAD_GATEWAY` — сервис оборвал соединение; 504 `GATEWAY_TIMEOUT` — нет ответа за 15 с (`GatewayErrorHandler`).

Тайм-ауты задаются в `spring.cloud.gateway.server.webflux.httpclient` (`connect-timeout`, `response-timeout`); ответ ждётся дольше самой долгой саги при зависшем Hotel Service: удержание слота и компенсирующий `release`, каждый до 3 попыток по 2 с, итого около 13 с. Роли проверяют сервисы: Gateway только аутентифицирует запрос.

`X-Forwarded-*` добавляются для клиентов из `GATEWAY_TRUSTED_PROXIES` (по умолчанию localhost). Благодаря этому сервисы возвращают правильные `Location` и URL в Swagger.

## Swagger UI

http://localhost:8080/swagger-ui.html — документация обоих сервисов, сервис выбирается вверху страницы. Чтобы вызывать защищённые методы, получите токен через `POST /api/bookings/user/auth` и нажмите **Authorize**.

## Конфигурация

| Переменная | По умолчанию |
|---|---|
| `GATEWAY_PORT` | `8080` |
| `EUREKA_URL` | `http://localhost:8761/eureka/` |
| `JWT_SECRET` | dev-секрет (задайте свой, ≥ 32 байт; должен совпадать во всех сервисах) |
| `GATEWAY_TRUSTED_PROXIES` | `127\.0\.0\.1\|0:0:0:0:0:0:0:1` |

## Тесты

`GatewayRoutingTest` (WebTestClient, сервисы заменены WireMock): маршрутизация и `StripPrefix`, публичные эндпойнты, 401 без токена и с поддельным или просроченным токеном, 404 для внутренних путей (и со слешем на конце) и H2 Console, ErrorDto для неизвестного пути (404), отклонённого URL (400), оборванного соединения (502) и тайм-аута (504), закрытый actuator, удаление клиентского `X-Booking-Id`, совпадение `X-Trace-Id` с `traceparent`, переданным сервису.
