package ru.hotelbooking.booking.client;

import java.time.LocalDate;
import java.util.List;
import ru.hotelbooking.booking.client.dto.HotelReservation;
import ru.hotelbooking.booking.client.dto.HotelRoom;
import ru.hotelbooking.booking.client.dto.SlotRequest;

/**
 * Порт к Hotel Service, через который работает сага бронирования.
 *
 * <p>Ошибки сообщаются подклассами {@link HotelCallException}: {@link SlotRejectedException}, когда Hotel Service
 * отклонил запрос (слот не удержан), {@link HotelUnavailableException}, когда исход неизвестен (тайм-аут, 5xx, ошибка
 * соединения, открытый circuit breaker), {@link HotelIntegrationException} для неожиданных ответов.
 */
public interface HotelClient {

    /** Свободные номера на период по возрастанию {@code times_booked}, затем по id. */
    List<HotelRoom> recommend(Long hotelId, LocalDate startDate, LocalDate endDate);

    /** Шаг 2 саги: подтвердить доступность и удержать слот. Идемпотентен по {@link SlotRequest#requestId()}. */
    HotelReservation confirmAvailability(long roomId, SlotRequest request);

    /** Компенсация: освободить слот, удержанный по {@link SlotRequest#requestId()}. Идемпотентна. */
    HotelReservation release(long roomId, SlotRequest request);
}
