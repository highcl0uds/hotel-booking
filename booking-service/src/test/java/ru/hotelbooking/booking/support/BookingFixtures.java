package ru.hotelbooking.booking.support;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.hotelbooking.booking.domain.Booking;
import ru.hotelbooking.booking.domain.BookingStatus;
import ru.hotelbooking.booking.domain.CancellationReason;
import ru.hotelbooking.booking.domain.Role;
import ru.hotelbooking.booking.domain.User;
import ru.hotelbooking.booking.repository.UserRepository;
import ru.hotelbooking.booking.saga.BookingDraft;
import ru.hotelbooking.booking.saga.BookingStateService;

/**
 * Построители тестовых данных. Брони переводятся между состояниями теми же локальными транзакциями, что использует
 * сага.
 */
@TestComponent
public class BookingFixtures {

    public static final String PASSWORD = "secret123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BookingStateService stateService;
    private final JdbcTemplate jdbcTemplate;

    public BookingFixtures(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            BookingStateService stateService,
            JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.stateService = stateService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public User user(String username) {
        return userRepository.save(new User(username, passwordEncoder.encode(PASSWORD), Role.USER));
    }

    public User admin(String username) {
        return userRepository.save(new User(username, passwordEncoder.encode(PASSWORD), Role.ADMIN));
    }

    public Booking pending(User user, long roomId, LocalDate start, LocalDate end) {
        Booking booking = stateService.createPending(
                new BookingDraft(user.getId(), UUID.randomUUID(), false, roomId, null, start, end));
        stateService.assignRoom(booking.getId(), roomId);
        return stateService.get(booking.getId());
    }

    public Booking confirmed(User user, long roomId, LocalDate start, LocalDate end) {
        Booking booking = pending(user, roomId, start, end);
        stateService.confirm(booking.getId(), 1L);
        return stateService.get(booking.getId());
    }

    public Booking cancelled(User user, long roomId, LocalDate start, LocalDate end) {
        Booking booking = pending(user, roomId, start, end);
        stateService.cancel(booking.getId(), BookingStatus.PENDING, CancellationReason.ROOM_UNAVAILABLE, false);
        return stateService.get(booking.getId());
    }

    /** Отменена пользователем, пока Hotel Service был недоступен: освобождение слота ещё не доставлено. */
    public Booking cancelledAwaitingRelease(User user, long roomId, LocalDate start, LocalDate end) {
        Booking booking = confirmed(user, roomId, start, end);
        stateService.cancel(booking.getId(), BookingStatus.CONFIRMED, CancellationReason.CANCELLED_BY_USER, true);
        return stateService.get(booking.getId());
    }

    /** Имитирует сагу, прерванную давно (например, сервис упал после удержания слота). */
    public void makeOlder(Booking booking, Duration age) {
        jdbcTemplate.update(
                "update bookings set created_at = dateadd('SECOND', ?, created_at) where id = ?",
                -age.toSeconds(),
                booking.getId());
    }

    public Booking reload(Booking booking) {
        return stateService.get(booking.getId());
    }
}
