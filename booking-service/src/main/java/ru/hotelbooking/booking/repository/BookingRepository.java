package ru.hotelbooking.booking.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.hotelbooking.booking.domain.Booking;
import ru.hotelbooking.booking.domain.BookingStatus;
import ru.hotelbooking.booking.domain.CancellationReason;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByUserIdAndRequestId(long userId, UUID requestId);

    Page<Booking> findByUserId(long userId, Pageable pageable);

    Page<Booking> findByUserIdAndStatus(long userId, BookingStatus status, Pageable pageable);

    boolean existsByUserIdAndStatusInAndEndDateAfter(long userId, Collection<BookingStatus> statuses, LocalDate date);

    boolean existsByUserIdAndCompensationPendingTrue(long userId);

    List<Booking> findByStatusAndCreatedAtBeforeOrderById(BookingStatus status, Instant createdBefore, Limit limit);

    List<Booking> findByCompensationPendingTrueOrderById(Limit limit);

    /** Compare-and-set статуса: возвращает 0, если бронь уже не в статусе {@code from}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b
            set b.status = :to, b.cancelReason = :reason, b.compensationPending = :compensationPending,
                b.updatedAt = :now, b.version = b.version + 1
            where b.id = :id and b.status = :from
            """)
    int transition(
            long id,
            BookingStatus from,
            BookingStatus to,
            CancellationReason reason,
            boolean compensationPending,
            Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b
            set b.status = ru.hotelbooking.booking.domain.BookingStatus.CONFIRMED, b.hotelId = :hotelId,
                b.updatedAt = :now, b.version = b.version + 1
            where b.id = :id and b.status = ru.hotelbooking.booking.domain.BookingStatus.PENDING
            """)
    int confirm(long id, Long hotelId, Instant now);

    /** Запоминает номер, который сага собирается удержать, чтобы восстановление знало, что освобождать. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b set b.roomId = :roomId, b.updatedAt = :now, b.version = b.version + 1
            where b.id = :id and b.status = ru.hotelbooking.booking.domain.BookingStatus.PENDING
            """)
    int assignRoom(long id, long roomId, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b set b.compensationPending = false, b.updatedAt = :now, b.version = b.version + 1
            where b.id = :id and b.compensationPending = true
            """)
    int markCompensated(long id, Instant now);
}
