# ADR-0007. Маршруты Gateway и общий модуль common

## Контекст

В ТЗ два списка, которые расходятся. Для Gateway указаны маршруты `/api/bookings/**` → Booking и `/api/hotels/**` → Hotel. При этом у Booking эндпойнты `/user`, `/booking`, `/bookings`, а у Hotel ещё и `/api/rooms`. Кроме того, сервисы на сервлетах и реактивный Gateway повторяют одну и ту же инфраструктуру: формат ошибок, настройку JWT, обработчики 401/403.

## Решение

**Маршруты**:

| Внешний путь | Сервис | Внутренний путь |
|---|---|---|
| `/api/bookings/**` | `lb://booking-service` | без префикса (`StripPrefix=2`): `/api/bookings/user/auth` → `/user/auth` |
| `/api/hotels/**`, `/api/rooms/**` | `lb://hotel-service` | без изменений |
| `/api/rooms/{id}/confirm-availability/**`, `/api/rooms/{id}/release/**` | — | `404`, не публикуются (`InternalEndpointsFilter`); шаблон `/**` закрывает и вариант со слешем на конце |
| `/api/bookings/h2-console/**` | — | `404`: маршрут Booking снимает префикс, и без запрета H2 Console демо-профиля была бы доступна любому пользователю с токеном |

Оба списка ТЗ выполняются буквально. Адреса сервисов не прописаны: их находит Eureka через `lb://`.

Фильтры Gateway:
- `TracingFilter` — заголовок `X-Trace-Id` и access-лог;
- `InternalEndpointsFilter`;
- `GatewayErrorHandler` — собственные ошибки Gateway (404, 502, 503, 504) в формате `ErrorDto`;
- проверка JWT;
- `X-Forwarded-*` для доверенных адресов (`trusted-proxies`), чтобы сервисы строили правильные `Location` и URL в Swagger.

**Модуль `common`** — общий код без копипаста:
- `ErrorDto`;
- `GlobalExceptionHandler`;
- `JwtProperties`, `JwtSupport`, `JwtTokenFactory`;
- автоконфигурация resource server и OpenAPI;
- `@CurrentUser`;
- валидатор дат;
- `PageResponse`.

Сервлетные зависимости подключены как `optional`, а автоконфигурации ограничены условием `@ConditionalOnWebApplication(SERVLET)`. Благодаря этому реактивный Gateway переиспользует только нейтральные классы.

## Плюсы и минусы

- (+) Одинаковый формат ошибок и правила безопасности во всех сервисах; новый сервис получает их подключением зависимости.
- (−) Сервисы связаны общей библиотекой. Её изменения требуют пересборки всех сервисов: это осознанный компромисс для учебного монорепозитория.
