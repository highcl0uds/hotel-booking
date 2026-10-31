package ru.hotelbooking.hotel.domain;

import java.time.LocalDate;

/**
 * Команда удержания или освобождения слота номера.
 *
 * @param requestId ключ идемпотентности: повторная доставка той же команды больше ничего не меняет
 * @param bookingId id корреляции саги бронирования
 */
public record SlotCommand(String requestId, Long bookingId, LocalDate startDate, LocalDate endDate) {}
