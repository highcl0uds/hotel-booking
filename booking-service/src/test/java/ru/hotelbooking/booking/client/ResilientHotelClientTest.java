package ru.hotelbooking.booking.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.hotelbooking.booking.client.dto.HotelReservation;
import ru.hotelbooking.booking.client.dto.SlotRequest;

class ResilientHotelClientTest {

    private static final SlotRequest SLOT = SlotRequest.of(1, 7, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 3));

    private HotelClient delegate;
    private CircuitBreaker circuitBreaker;
    private ResilientHotelClient client;

    @BeforeEach
    void setUp() {
        delegate = mock(HotelClient.class);
        Retry retry = Retry.of(
                "test",
                RetryConfig.custom()
                        .maxAttempts(3)
                        .waitDuration(Duration.ofMillis(1))
                        .retryExceptions(HotelUnavailableException.class)
                        .build());
        circuitBreaker = CircuitBreaker.of(
                "test",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(4)
                        .minimumNumberOfCalls(4)
                        .failureRateThreshold(50)
                        .recordExceptions(HotelUnavailableException.class)
                        .build());
        client = new ResilientHotelClient(delegate, retry, circuitBreaker);
    }

    @Test
    @DisplayName("Недоступность Hotel повторяется ограниченное число раз")
    void retriesUnavailabilityUpToMaxAttempts() {
        when(delegate.confirmAvailability(anyLong(), any())).thenThrow(new HotelUnavailableException("down", null));

        assertThatThrownBy(() -> client.confirmAvailability(7, SLOT)).isInstanceOf(HotelUnavailableException.class);

        verify(delegate, times(3)).confirmAvailability(7, SLOT);
    }

    @Test
    @DisplayName("Отказ Hotel (409) не повторяется и не считается сбоем circuit breaker")
    void rejectionIsNotRetried() {
        when(delegate.confirmAvailability(anyLong(), any()))
                .thenThrow(new SlotRejectedException(HttpStatus.CONFLICT, "ROOM_ALREADY_BOOKED", "taken", null));

        assertThatThrownBy(() -> client.confirmAvailability(7, SLOT)).isInstanceOf(SlotRejectedException.class);

        verify(delegate, times(1)).confirmAvailability(7, SLOT);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    @DisplayName("После серии сбоев circuit breaker открывается и вызовы не доходят до Hotel")
    void openCircuitBreakerStopsCalls() {
        when(delegate.release(anyLong(), any())).thenThrow(new HotelUnavailableException("down", null));
        assertThatThrownBy(() -> client.release(7, SLOT)).isInstanceOf(HotelUnavailableException.class);
        assertThatThrownBy(() -> client.release(7, SLOT)).isInstanceOf(HotelUnavailableException.class);

        assertThatThrownBy(() -> client.release(7, SLOT))
                .isInstanceOf(HotelUnavailableException.class)
                .hasMessageContaining("circuit breaker is open");

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(delegate, times(4)).release(7, SLOT);
    }

    @Test
    @DisplayName("Успешный ответ возвращается без повторов")
    void successIsReturnedImmediately() {
        HotelReservation reservation = new HotelReservation(1L, 7L, 1L, 1L, SLOT.requestId(), null, null, "HELD");
        when(delegate.confirmAvailability(7, SLOT)).thenReturn(reservation);

        assertThat(client.confirmAvailability(7, SLOT)).isEqualTo(reservation);
        verify(delegate, times(1)).confirmAvailability(7, SLOT);
    }
}
