package ru.hotelbooking.booking.service;

import org.springframework.data.domain.Pageable;
import ru.hotelbooking.booking.api.dto.BookingResponse;
import ru.hotelbooking.booking.api.dto.CreateBookingRequest;
import ru.hotelbooking.booking.domain.BookingStatus;
import ru.hotelbooking.common.security.AuthenticatedUser;
import ru.hotelbooking.common.web.PageResponse;

public interface BookingService {

    /** Выполняет сагу бронирования; повторный запрос с тем же requestId возвращает существующую бронь. */
    BookingCreation create(AuthenticatedUser user, CreateBookingRequest request);

    /** Только брони вызывающего пользователя, с необязательным фильтром по статусу. */
    PageResponse<BookingResponse> history(AuthenticatedUser user, BookingStatus status, Pageable pageable);

    /** Бронь с историей статусов; видна владельцу и администраторам. */
    BookingResponse get(AuthenticatedUser user, long bookingId);

    /** Отменяет подтверждённую бронь и освобождает слот в Hotel Service; идемпотентна. */
    void cancel(AuthenticatedUser user, long bookingId);

    /**
     * @param created {@code false}, если на повторный запрос вернули существующую бронь
     */
    record BookingCreation(BookingResponse booking, boolean created) {}
}
