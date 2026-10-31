-- Демо-данные Booking Service. Даты считаются от текущей, поэтому сценарии работают в любой день.
-- Брони зеркалят резервы из демо-данных Hotel Service (те же id, request id = hold-<bookingId>-<roomId>).

-- пароли: admin / admin123, alice / alice123, bob / bob123 (BCrypt)
INSERT INTO users (id, username, password, role, created_at, updated_at)
VALUES (1, 'admin', '$2a$10$pzJpshu92R9zwc7bJj/.dO2FEM3xKNZ2N0lF6q8T0LfQHrsrQFgf2', 'ADMIN', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
       (2, 'alice', '$2a$10$I7OJzWYdhepoC/fDtFgNeuo6BY43AvmJjd8jKI2U6pTiIk/fW1dm6', 'USER', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
       (3, 'bob', '$2a$10$cJZxOfxp6PO.sWMhfKTv2eyv/3LNMqO1XTpFA.WoTOt0gU45TEmsa', 'USER', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- 1: alice, номер 101 (отель 1), предстоящая, CONFIRMED  -> бронь номера 101 на пересекающиеся даты вернёт 409
-- 2: bob,   номер 102 (отель 1), предстоящая, CONFIRMED
-- 3: alice, номер 201 (отель 2), прошедшее проживание, CONFIRMED  -> отменить нельзя (BOOKING_COMPLETED)
-- 4: bob,   номер 202 (отель 2), CANCELLED пользователем
-- 5: alice, номер 204 (отель 2), CANCELLED сагой: Hotel Service был недоступен, слот компенсирован
INSERT INTO bookings (id, user_id, request_id, room_id, hotel_id, auto_select, start_date, end_date, status,
                      cancel_reason, compensation_pending, created_at, updated_at, version)
VALUES (1, 2, '00000000-0000-0000-0000-000000000001', 1, 1, FALSE, DATEADD('DAY', 10, CURRENT_DATE),
        DATEADD('DAY', 13, CURRENT_DATE), 'CONFIRMED', NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       (2, 3, '00000000-0000-0000-0000-000000000002', 2, 1, TRUE, DATEADD('DAY', 10, CURRENT_DATE),
        DATEADD('DAY', 12, CURRENT_DATE), 'CONFIRMED', NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       (3, 2, '00000000-0000-0000-0000-000000000003', 6, 2, FALSE, DATEADD('DAY', -10, CURRENT_DATE),
        DATEADD('DAY', -7, CURRENT_DATE), 'CONFIRMED', NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       (4, 3, '00000000-0000-0000-0000-000000000004', 7, 2, FALSE, DATEADD('DAY', 20, CURRENT_DATE),
        DATEADD('DAY', 25, CURRENT_DATE), 'CANCELLED', 'CANCELLED_BY_USER', FALSE, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP, 2),
       (5, 2, '00000000-0000-0000-0000-000000000005', 9, NULL, FALSE, DATEADD('DAY', 5, CURRENT_DATE),
        DATEADD('DAY', 8, CURRENT_DATE), 'CANCELLED', 'HOTEL_UNAVAILABLE', FALSE, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP, 2);

INSERT INTO booking_status_history (booking_id, from_status, to_status, reason, changed_at)
VALUES (1, NULL, 'PENDING', NULL, CURRENT_TIMESTAMP),
       (1, 'PENDING', 'CONFIRMED', NULL, CURRENT_TIMESTAMP),
       (2, NULL, 'PENDING', NULL, CURRENT_TIMESTAMP),
       (2, 'PENDING', 'CONFIRMED', NULL, CURRENT_TIMESTAMP),
       (3, NULL, 'PENDING', NULL, CURRENT_TIMESTAMP),
       (3, 'PENDING', 'CONFIRMED', NULL, CURRENT_TIMESTAMP),
       (4, NULL, 'PENDING', NULL, CURRENT_TIMESTAMP),
       (4, 'PENDING', 'CONFIRMED', NULL, CURRENT_TIMESTAMP),
       (4, 'CONFIRMED', 'CANCELLED', 'CANCELLED_BY_USER', CURRENT_TIMESTAMP),
       (5, NULL, 'PENDING', NULL, CURRENT_TIMESTAMP),
       (5, 'PENDING', 'CANCELLED', 'HOTEL_UNAVAILABLE', CURRENT_TIMESTAMP);

-- идентификаторы, созданные через API, начинаются после демо-записей
ALTER TABLE users ALTER COLUMN id RESTART WITH 100;
ALTER TABLE bookings ALTER COLUMN id RESTART WITH 100;
