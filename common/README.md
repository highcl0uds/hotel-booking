# common

Библиотека общей инфраструктуры. Подключается к `hotel-service`, `booking-service` и `api-gateway`, поэтому в сервисах нет дублирующегося кода (ADR-0007).

| Пакет | Что внутри |
|---|---|
| `error` | `ErrorDto` — единый формат ошибки; `ErrorCodes` (и `forStatus` — код по HTTP-статусу, общий для сервисов и Gateway); иерархия `ApiException` (`NotFoundException`, `ConflictException`, `ServiceUnavailableException`) |
| `security` | `JwtProperties` (`app.security.jwt.*`); `JwtSupport` — валидация и конвертер ролей `roles → ROLE_*`; `JwtTokenFactory` — выпуск токенов; `Roles`; `Authorities` — выражения для `@PreAuthorize`; `AuthenticatedUser` и `@CurrentUser` |
| `web` | `GlobalExceptionHandler` — любое исключение → `ErrorDto` с правильным статусом; значение не того типа → 400 с именем поля и ожидаемым форматом, без имён Java-классов в ответе; JSON-ответы 401/403; `CorrelationMdcFilter` (`X-Booking-Id` → MDC); `PageResponse`; `Locations` — заголовок `Location` с публичным URL созданного ресурса; `CorrelationHeaders` |
| `validation` | `@ValidDateRange` для полуинтервала `[startDate, endDate)`; `@MaxUtf8Bytes` — предел длины строки в байтах UTF-8 (BCrypt принимает не больше 72 байт) |
| `autoconfigure` | Автоконфигурации Spring Boot (см. ниже) |

## Автоконфигурации

Подключаются автоматически через `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

- `CommonAutoConfiguration` — `JwtProperties` и бин `Clock`. Работает в любом типе приложения.
- `ServletWebAutoConfiguration` — обработчик ошибок, MDC-фильтр и `@CurrentUser`. Только для сервлетных сервисов.
- `ServletSecurityAutoConfiguration` — resource server:
  - stateless-режим;
  - публичные пути из `app.security.public-paths` (плюс health и Swagger);
  - проверка JWT и иерархия ролей `ADMIN > USER`;
  - URL, отклонённый firewall, → 400 `ErrorDto` (`RequestRejectedHandler`), а ERROR-диспетчеризация не требует токена, поэтому ошибки вне Spring MVC не превращаются в 401;
  - `@EnableMethodSecurity`.
- `OpenApiAutoConfiguration` — описание API со схемой Bearer JWT для Swagger UI.

Зависимости на web, security и data объявлены как `optional`. Поэтому реактивный Gateway использует только нейтральные классы: `ErrorDto`, `JwtProperties`, `JwtSupport`.

## Тестовые утилиты (test-jar)

- `TestTokens` — настоящие подписанные токены для тестов: пользователь, администратор, служебный, просроченный, поддельный.
- `Concurrently` — одновременный запуск задач в нескольких потоках для тестов на гонки.
