package ru.hotelbooking.hotel.service;

import ru.hotelbooking.hotel.api.dto.ReservationResponse;
import ru.hotelbooking.hotel.domain.SlotCommand;

/**
 * Сторона саги бронирования в Hotel Service. Обе операции идемпотентны по {@link SlotCommand#requestId()} и выполняются
 * в одной локальной транзакции с заблокированной строкой номера.
 */
public interface RoomAvailabilityService {

    /**
     * Подтверждает, что номер свободен на период, и удерживает слот; увеличивает {@code timesBooked}.
     * Повторный вызов с тем же request id возвращает существующий резерв без побочных эффектов.
     */
    ReservationResponse confirmAvailability(long roomId, SlotCommand command);

    /**
     * Компенсация: освобождает слот, удержанный по request id, и уменьшает {@code timesBooked}.
     * Повторное освобождение ничего не делает; освобождение по неизвестному request id сохраняет «надгробие», которое
     * отклонит запоздалое удержание.
     */
    ReservationResponse release(long roomId, SlotCommand command);
}
