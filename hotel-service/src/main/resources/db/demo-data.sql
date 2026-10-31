-- Демо-данные Hotel Service. Даты считаются от текущей, поэтому сценарии работают в любой день.
-- Резервы зеркалят брони из демо-данных Booking Service (те же id броней, request id = hold-<bookingId>-<roomId>).

INSERT INTO hotels (id, name, city, address, stars, created_at, updated_at)
VALUES (1, 'Grand Moscow', 'Moscow', 'Tverskaya st., 3', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (2, 'Neva Palace', 'Saint Petersburg', 'Nevsky pr., 45', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (3, 'Volga Riverside', 'Kazan', 'Kremlevskaya st., 12', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- times_booked различается намеренно: GET /api/rooms/recommend для отеля 1 возвращает 103 (0), 102 (1), 104 (1, при
-- равенстве -> id), 101 (3); номер 105 на ремонте (available = false) и никогда не предлагается.
INSERT INTO rooms (id, hotel_id, number, type, capacity, price_per_night, available, times_booked, version,
                   created_at, updated_at)
VALUES (1, 1, '101', 'STANDARD', 2, 6500.00, TRUE, 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (2, 1, '102', 'STANDARD', 2, 6500.00, TRUE, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (3, 1, '103', 'DELUXE', 3, 9800.00, TRUE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (4, 1, '104', 'SUITE', 4, 15000.00, TRUE, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (5, 1, '105', 'STANDARD', 2, 6500.00, FALSE, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (6, 2, '201', 'STANDARD', 2, 5200.00, TRUE, 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (7, 2, '202', 'DELUXE', 3, 8100.00, TRUE, 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (8, 2, '203', 'STANDARD', 1, 4300.00, TRUE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (9, 2, '204', 'SUITE', 4, 12500.00, TRUE, 4, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (10, 3, '301', 'STANDARD', 2, 3900.00, TRUE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (11, 3, '302', 'STANDARD', 2, 3900.00, TRUE, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (12, 3, '303', 'DELUXE', 3, 5600.00, TRUE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- бронь 1 (alice): номер 101, +10..+13 дней -> запрос номера 101 на пересекающиеся даты получит 409
-- бронь 2 (bob):   номер 102, +10..+12 дней
-- бронь 3 (alice): номер 201, проживание в прошлом (видно в отчётах о загрузке)
-- бронь 4 (bob):   номер 202, отменена пользователем -> слот освобождён
-- бронь 5 (alice): номер 204, компенсирована после сбоя Hotel Service -> освобождение-«надгробие»
INSERT INTO room_reservations (id, room_id, booking_id, request_id, start_date, end_date, status, created_at,
                               released_at)
VALUES (1, 1, 1, 'hold-1-1', DATEADD('DAY', 10, CURRENT_DATE), DATEADD('DAY', 13, CURRENT_DATE), 'HELD',
        CURRENT_TIMESTAMP, NULL),
       (2, 2, 2, 'hold-2-2', DATEADD('DAY', 10, CURRENT_DATE), DATEADD('DAY', 12, CURRENT_DATE), 'HELD',
        CURRENT_TIMESTAMP, NULL),
       (3, 6, 3, 'hold-3-6', DATEADD('DAY', -10, CURRENT_DATE), DATEADD('DAY', -7, CURRENT_DATE), 'HELD',
        CURRENT_TIMESTAMP, NULL),
       (4, 7, 4, 'hold-4-7', DATEADD('DAY', 20, CURRENT_DATE), DATEADD('DAY', 25, CURRENT_DATE), 'RELEASED',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (5, 9, 5, 'hold-5-9', DATEADD('DAY', 5, CURRENT_DATE), DATEADD('DAY', 8, CURRENT_DATE), 'RELEASED',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- идентификаторы, созданные через API, начинаются после демо-записей
ALTER TABLE hotels ALTER COLUMN id RESTART WITH 100;
ALTER TABLE rooms ALTER COLUMN id RESTART WITH 100;
ALTER TABLE room_reservations ALTER COLUMN id RESTART WITH 100;
