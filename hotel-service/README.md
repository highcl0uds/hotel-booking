# hotel-service

Hotel Management Service. Порт **8082**, база H2 `hoteldb` в памяти.

Отвечает за:
- отели и номера (CRUD, фильтры, сортировка, пагинация);
- рекомендации номеров по `times_booked`;
- блокировку и освобождение слотов для саги бронирования;
- статистику загруженности.

## Пакеты

| Пакет | Содержимое |
|---|---|
| `api` | контроллеры `HotelController`, `RoomController`, `RoomAvailabilityController` (INTERNAL), `StatisticsController`; DTO-records |
| `service` | интерфейсы `HotelService`, `RoomService`, `RoomAvailabilityService`, `StatisticsService` и реализации; `OccupancyCalculator` — чистая функция расчёта загрузки |
| `repository` | Spring Data JPA; `RoomSpecifications` и `HotelSpecifications` для фильтров; `findByIdForUpdate` с `PESSIMISTIC_WRITE` |
| `domain` | `Hotel`, `Room`, `RoomReservation` (слот `[start, end)`), `SlotCommand` |
| `mapper` | MapStruct |

## Эндпойнты

| Метод и путь | Роль | Описание |
|---|---|---|
| `GET /api/hotels?city&name&minStars&page&size&sort` | USER | отели с фильтрами |
| `GET /api/hotels/{id}` | USER | отель |
| `POST /api/hotels`, `PUT /api/hotels/{id}`, `DELETE /api/hotels/{id}` | ADMIN | CRUD; удаление запрещено (409), пока есть предстоящие брони |
| `GET /api/rooms?hotelId&city&type&minCapacity&maxPrice&startDate&endDate&page&sort` | USER | свободные номера: работающие и, если заданы даты, без пересекающихся блокировок |
| `GET /api/rooms/recommend?hotelId&startDate&endDate&...` | USER, INTERNAL | те же номера с теми же фильтрами (даты необязательны), `ORDER BY times_booked, id` |
| `GET /api/rooms/{id}` | USER | номер |
| `POST /api/rooms`, `PUT /api/rooms/{id}`, `DELETE /api/rooms/{id}` | ADMIN | CRUD; `available=false` выводит номер из эксплуатации |
| `POST /api/rooms/{id}/confirm-availability` | INTERNAL | блокировка слота, идемпотентно по `requestId`; 409 при пересечении или ремонте |
| `POST /api/rooms/{id}/release` | INTERNAL | компенсация, идемпотентно; неизвестный `requestId` сохраняется как «надгробие» |
| `GET /api/rooms/stats?hotelId&sort` | ADMIN | `times_booked` и предстоящие брони по номерам |
| `GET /api/hotels/stats` | ADMIN | по отелям: min, max и среднее `times_booked`, разброс, простаивающие номера |
| `GET /api/hotels/{id}/occupancy?from&to` | ADMIN | загрузка за период: room-nights, %, по номерам и по дням, простаивающие номера |

Swagger: http://localhost:8082/swagger-ui.html. OpenAPI лежит по адресу `/api/hotels/v3/api-docs`, поэтому доступен и через Gateway.

## Схема БД

`src/main/resources/db/schema.sql`. Hibernate только сверяет схему (`ddl-auto=validate`). Демо-данные — `db/demo-data.sql`, профиль `demo` активен по умолчанию. H2 Console: http://localhost:8082/h2-console (`jdbc:h2:mem:hoteldb`, пользователь `sa`, без пароля).

## Тесты

| Класс | Что проверяет |
|---|---|
| `HotelApiTest` | CRUD, фильтры, пагинация, 401/403, валидация, 404, запрет удаления, 400 для URL, отклонённого firewall |
| `RoomApiTest` | CRUD номеров (номер с бронями нельзя перенести в другой отель), исключение занятых и неработающих номеров, бронь встык, порядок рекомендаций с датами и без; неверный тип значения в теле или параметре → 400 с именем поля |
| `RoomAvailabilityApiTest` | блокировка, идемпотентный повтор, 409, «надгробие», доступ только для INTERNAL |
| `RoomAllocationConcurrencyTest` | 20 параллельных броней → ровно 1; параллельные дубликаты → 1 запись; равномерная загрузка при последовательном и параллельном автоподборе; удаление номера дожидается параллельного удержания слота и отказывает 409 |
| `StatisticsApiTest`, `OccupancyCalculatorTest` | статистика и отчёт о загрузке |
