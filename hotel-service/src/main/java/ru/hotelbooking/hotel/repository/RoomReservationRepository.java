package ru.hotelbooking.hotel.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.hotelbooking.hotel.domain.ReservationStatus;
import ru.hotelbooking.hotel.domain.RoomReservation;

public interface RoomReservationRepository extends JpaRepository<RoomReservation, Long> {

    Optional<RoomReservation> findByRequestId(String requestId);

    /** {@code [startA, endA)} и {@code [startB, endB)} пересекаются, когда {@code startA < endB && endA > startB}. */
    boolean existsByRoomIdAndStatusAndStartDateBeforeAndEndDateAfter(
            long roomId, ReservationStatus status, LocalDate endDate, LocalDate startDate);

    default boolean hasHeldOverlap(long roomId, LocalDate startDate, LocalDate endDate) {
        return existsByRoomIdAndStatusAndStartDateBeforeAndEndDateAfter(
                roomId, ReservationStatus.HELD, endDate, startDate);
    }

    boolean existsByRoomIdAndStatusAndEndDateAfter(long roomId, ReservationStatus status, LocalDate date);

    boolean existsByRoomHotelIdAndStatusAndEndDateAfter(long hotelId, ReservationStatus status, LocalDate date);

    @Query("""
            select r from RoomReservation r join fetch r.room room
            where room.hotel.id = :hotelId and r.status = :status
              and r.startDate < :to and r.endDate > :from
            """)
    List<RoomReservation> findOverlappingInHotel(long hotelId, ReservationStatus status, LocalDate from, LocalDate to);

    @Query("""
            select r.room.id as roomId, count(r) as reservations from RoomReservation r
            where r.room.id in :roomIds and r.status = :status and r.endDate > :date
            group by r.room.id
            """)
    List<RoomReservationCount> countByRoomEndingAfter(
            Collection<Long> roomIds, ReservationStatus status, LocalDate date);
}
