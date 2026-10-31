package ru.hotelbooking.hotel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import ru.hotelbooking.common.error.ConflictException;
import ru.hotelbooking.common.test.Concurrently;
import ru.hotelbooking.hotel.api.dto.RoomResponse;
import ru.hotelbooking.hotel.api.dto.RoomSearchCriteria;
import ru.hotelbooking.hotel.domain.Hotel;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.domain.SlotCommand;
import ru.hotelbooking.hotel.repository.RoomRepository;
import ru.hotelbooking.hotel.support.IntegrationTest;

/**
 * Гарантии алгоритма распределения номеров при параллельных запросах и равномерность загрузки, проверенные на настоящей
 * базе.
 */
@DisplayName("Планирование занятости: параллельные брони и равномерная загрузка")
class RoomAllocationConcurrencyTest extends IntegrationTest {

    private static final int THREADS = 20;

    @Autowired
    private RoomAvailabilityService availabilityService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("20 параллельных броней одного номера на пересекающиеся даты: успешна ровно одна")
    void onlyOneOfConcurrentOverlappingHoldsSucceeds() throws Exception {
        Room room = fixtures.room(fixtures.hotel("Grand", "Moscow"), "101");

        List<Outcome> outcomes = Concurrently.run(
                THREADS, i -> () -> attempt(room.getId(), fixtures.slot(inDays(10 + i % 3), inDays(13 + i % 3))));

        assertThat(outcomes).filteredOn(Outcome.HELD::equals).hasSize(1);
        assertThat(outcomes).filteredOn(Outcome.CONFLICT::equals).hasSize(THREADS - 1);
        assertThat(fixtures.reload(room).getTimesBooked()).isEqualTo(1);
        assertThat(countReservations()).isEqualTo(1);
    }

    @Test
    @DisplayName("Параллельная повторная доставка одного запроса: одна блокировка, times_booked = 1")
    void concurrentDuplicatesProduceSingleReservation() throws Exception {
        Room room = fixtures.room(fixtures.hotel("Grand", "Moscow"), "101");
        SlotCommand sameRequest = fixtures.slot(inDays(10), inDays(12));

        List<Outcome> outcomes = Concurrently.run(THREADS, i -> () -> attempt(room.getId(), sameRequest));

        assertThat(outcomes).containsOnly(Outcome.HELD);
        assertThat(fixtures.reload(room).getTimesBooked()).isEqualTo(1);
        assertThat(countReservations()).isEqualTo(1);
    }

    @Test
    @DisplayName("30 последовательных автоподборов на 10 номеров: каждый номер забронирован ровно 3 раза")
    void sequentialAutoSelectionSpreadsLoadEvenly() {
        Hotel hotel = fixtures.hotel("Grand", "Moscow");
        List<Room> rooms = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> fixtures.room(hotel, "10" + i))
                .toList();

        for (int i = 0; i < 30; i++) {
            LocalDate start = inDays(1 + i * 2);
            autoSelect(hotel, start, start.plusDays(2));
        }

        assertThat(rooms)
                .allSatisfy(room ->
                        assertThat(fixtures.reload(room).getTimesBooked()).isEqualTo(3));
    }

    @Test
    @DisplayName("10 параллельных автоподборов на одни даты при 10 номерах: все успешны, по одной брони на номер")
    void concurrentAutoSelectionUsesEveryRoomOnce() throws Exception {
        Hotel hotel = fixtures.hotel("Grand", "Moscow");
        List<Room> rooms = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> fixtures.room(hotel, "10" + i))
                .toList();

        List<Long> chosen = Concurrently.run(10, i -> () -> autoSelect(hotel, inDays(5), inDays(8)));

        assertThat(chosen).doesNotContainNull().doesNotHaveDuplicates().hasSize(10);
        Map<Long, Long> timesBooked =
                rooms.stream().map(fixtures::reload).collect(Collectors.toMap(Room::getId, Room::getTimesBooked));
        assertThat(timesBooked.values()).containsOnly(1L);
    }

    @Test
    @DisplayName("Удаление номера во время удержания слота дожидается его и отказывает 409: резерв не теряется")
    void roomDeletionWaitsForConcurrentHold() throws Exception {
        Room room = fixtures.room(fixtures.hotel("Grand", "Moscow"), "101");
        CountDownLatch roomLocked = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<?> hold = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            roomRepository.findByIdForUpdate(room.getId()).orElseThrow();
            roomLocked.countDown();
            pause(Duration.ofMillis(300));
            availabilityService.confirmAvailability(room.getId(), fixtures.slot(inDays(5), inDays(7)));
        }));
        roomLocked.await();

        assertThatThrownBy(() -> roomService.delete(room.getId()))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ROOM_HAS_ACTIVE_RESERVATIONS"));
        hold.get();
        executor.shutdown();
        assertThat(countReservations()).isEqualTo(1);
    }

    /**
     * Что делает Booking Service при {@code autoSelect=true}: берёт рекомендованные номера по порядку и переходит к
     * следующему кандидату, если параллельный запрос только что занял номер.
     */
    private Long autoSelect(Hotel hotel, LocalDate start, LocalDate end) {
        var criteria = new RoomSearchCriteria(hotel.getId(), null, null, null, null, start, end);
        SlotCommand command = fixtures.slot(start, end);
        for (RoomResponse candidate : roomService.recommend(criteria)) {
            SlotCommand perRoom =
                    new SlotCommand(command.requestId() + "-" + candidate.id(), command.bookingId(), start, end);
            if (attempt(candidate.id(), perRoom) == Outcome.HELD) {
                return candidate.id();
            }
        }
        return null;
    }

    private Outcome attempt(long roomId, SlotCommand command) {
        try {
            availabilityService.confirmAvailability(roomId, command);
            return Outcome.HELD;
        } catch (ConflictException e) {
            return Outcome.CONFLICT;
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Integer countReservations() {
        return jdbcTemplate.queryForObject("select count(*) from room_reservations", Integer.class);
    }

    private enum Outcome {
        HELD,
        CONFLICT
    }
}
