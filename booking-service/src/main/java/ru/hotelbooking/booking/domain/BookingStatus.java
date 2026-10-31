package ru.hotelbooking.booking.domain;

import java.util.Set;

/**
 * Жизненный цикл брони: {@code PENDING → CONFIRMED}, когда Hotel Service удержал слот,
 * {@code PENDING → CANCELLED} при сбое (с компенсацией), {@code CONFIRMED → CANCELLED} при отмене.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    public boolean canTransitionTo(BookingStatus target) {
        return switch (this) {
            case PENDING -> Set.of(CONFIRMED, CANCELLED).contains(target);
            case CONFIRMED -> target == CANCELLED;
            case CANCELLED -> false;
        };
    }
}
