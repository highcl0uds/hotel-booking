package ru.hotelbooking.hotel.domain;

public enum ReservationStatus {
    /** Слот заблокирован под бронь; пересекающиеся запросы отклоняются. */
    HELD,
    /** Слот освобождён компенсацией или отменой (или вовсе не удерживался: освобождение-«надгробие»). */
    RELEASED
}
