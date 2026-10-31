package ru.hotelbooking.booking.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hotelbooking.booking.domain.BookingStatusChange;

public interface BookingStatusChangeRepository extends JpaRepository<BookingStatusChange, Long> {

    List<BookingStatusChange> findByBookingIdOrderByChangedAtAscIdAsc(long bookingId);
}
