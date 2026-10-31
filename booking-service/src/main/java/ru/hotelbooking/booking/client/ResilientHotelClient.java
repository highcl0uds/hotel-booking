package ru.hotelbooking.booking.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import ru.hotelbooking.booking.client.dto.HotelReservation;
import ru.hotelbooking.booking.client.dto.HotelRoom;
import ru.hotelbooking.booking.client.dto.SlotRequest;

/**
 * Декоратор, добавляющий устойчивость каждому вызову: Retry (ограниченное число попыток, экспоненциальная пауза, только
 * для {@link HotelUnavailableException}) вокруг CircuitBreaker, который сразу отказывает, пока Hotel Service
 * недоступен. Тайм-аут каждой попытки обеспечивает HTTP-клиент.
 */
@Slf4j
public class ResilientHotelClient implements HotelClient {

    private final HotelClient delegate;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public ResilientHotelClient(HotelClient delegate, Retry retry, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public List<HotelRoom> recommend(Long hotelId, LocalDate startDate, LocalDate endDate) {
        return execute("recommend", () -> delegate.recommend(hotelId, startDate, endDate));
    }

    @Override
    public HotelReservation confirmAvailability(long roomId, SlotRequest request) {
        return execute("confirm-availability", () -> delegate.confirmAvailability(roomId, request));
    }

    @Override
    public HotelReservation release(long roomId, SlotRequest request) {
        return execute("release", () -> delegate.release(roomId, request));
    }

    private <T> T execute(String operation, Supplier<T> call) {
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, call);
        Supplier<T> retried = Retry.decorateSupplier(retry, guarded);
        try {
            return retried.get();
        } catch (CallNotPermittedException e) {
            log.warn(
                    "Hotel Service call '{}' not attempted: circuit breaker is {}",
                    operation,
                    circuitBreaker.getState());
            throw new HotelUnavailableException("Hotel Service circuit breaker is open", e);
        }
    }
}
