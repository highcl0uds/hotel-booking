package ru.hotelbooking.booking.client;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.hotelbooking.booking.client.dto.HotelReservation;
import ru.hotelbooking.booking.client.dto.HotelRoom;
import ru.hotelbooking.booking.client.dto.SlotRequest;
import ru.hotelbooking.common.error.ErrorDto;
import ru.hotelbooking.common.web.CorrelationHeaders;

/**
 * HTTP-адаптер к Hotel Service: делает одну попытку и переводит результат в исключения {@link HotelCallException}.
 * Тайм-ауты, аутентификация и обнаружение сервиса настроены на {@link RestClient}; повторы и circuit breaker добавляет
 * {@link ResilientHotelClient}.
 */
@Slf4j
public class RestHotelClient implements HotelClient {

    private static final ParameterizedTypeReference<List<HotelRoom>> ROOM_LIST = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public RestHotelClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<HotelRoom> recommend(Long hotelId, LocalDate startDate, LocalDate endDate) {
        return call(
                "recommend",
                () -> restClient
                        .get()
                        .uri(uri -> uri.path("/api/rooms/recommend")
                                .queryParamIfPresent("hotelId", Optional.ofNullable(hotelId))
                                .queryParam("startDate", startDate)
                                .queryParam("endDate", endDate)
                                .build())
                        .retrieve()
                        .body(ROOM_LIST));
    }

    @Override
    public HotelReservation confirmAvailability(long roomId, SlotRequest request) {
        return post("confirm-availability", roomId, request);
    }

    @Override
    public HotelReservation release(long roomId, SlotRequest request) {
        return post("release", roomId, request);
    }

    private HotelReservation post(String operation, long roomId, SlotRequest request) {
        return call(
                operation,
                () -> restClient
                        .post()
                        .uri("/api/rooms/{id}/" + operation, roomId)
                        .header(CorrelationHeaders.BOOKING_ID, String.valueOf(request.bookingId()))
                        .body(request)
                        .retrieve()
                        .body(HotelReservation.class));
    }

    private static <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404 || e.getStatusCode().value() == 409) {
                ErrorDto error = errorBody(e);
                String code = error != null ? error.code() : e.getStatusCode().toString();
                String message = error != null ? error.message() : e.getMessage();
                throw new SlotRejectedException(e.getStatusCode(), code, message, e);
            }
            throw new HotelIntegrationException(
                    "Hotel Service rejected %s with %s".formatted(operation, e.getStatusCode()), e);
        } catch (HttpServerErrorException e) {
            throw new HotelUnavailableException(
                    "Hotel Service failed on %s with %s".formatted(operation, e.getStatusCode()), e);
        } catch (RestClientException | IllegalStateException e) {
            // тайм-ауты и ошибки соединения (ResourceAccessException), нет зарегистрированных экземпляров
            throw new HotelUnavailableException(
                    "Hotel Service is unreachable on %s: %s".formatted(operation, e.getMessage()), e);
        }
    }

    private static ErrorDto errorBody(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(ErrorDto.class);
        } catch (RuntimeException parseError) {
            log.debug("Hotel Service error body is not an ErrorDto: {}", parseError.getMessage());
            return null;
        }
    }
}
