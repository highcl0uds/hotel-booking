package ru.hotelbooking.hotel.repository;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import ru.hotelbooking.hotel.domain.ReservationStatus;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.domain.RoomReservation;
import ru.hotelbooking.hotel.domain.RoomType;

/**
 * Комбинируемые фильтры для поиска номеров.
 */
public final class RoomSpecifications {

    private RoomSpecifications() {}

    /**
     * Номера, доступные для бронирования: работающие и, если задан период, без пересекающих его резервов HELD.
     */
    public static Specification<Room> bookable(RoomFilter filter) {
        List<Specification<Room>> specs = new ArrayList<>();
        specs.add(operational());
        if (filter.hotelId() != null) {
            specs.add(inHotel(filter.hotelId()));
        }
        if (filter.city() != null && !filter.city().isBlank()) {
            specs.add(inCity(filter.city()));
        }
        if (filter.type() != null) {
            specs.add(ofType(filter.type()));
        }
        if (filter.minCapacity() != null) {
            specs.add(capacityAtLeast(filter.minCapacity()));
        }
        if (filter.maxPrice() != null) {
            specs.add(priceAtMost(filter.maxPrice()));
        }
        if (filter.startDate() != null && filter.endDate() != null) {
            specs.add(freeDuring(filter.startDate(), filter.endDate()));
        }
        return Specification.allOf(specs);
    }

    public static Specification<Room> inHotel(long hotelId) {
        return (root, query, cb) -> cb.equal(root.get("hotel").get("id"), hotelId);
    }

    private static Specification<Room> operational() {
        return (root, query, cb) -> cb.isTrue(root.get("available"));
    }

    private static Specification<Room> inCity(String city) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("hotel").get("city")), city.toLowerCase());
    }

    private static Specification<Room> ofType(RoomType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    private static Specification<Room> capacityAtLeast(int capacity) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("capacity"), capacity);
    }

    private static Specification<Room> priceAtMost(BigDecimal price) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("pricePerNight"), price);
    }

    private static Specification<Room> freeDuring(LocalDate startDate, LocalDate endDate) {
        return (root, query, cb) -> {
            Subquery<Long> overlapping = query.subquery(Long.class);
            Root<RoomReservation> reservation = overlapping.from(RoomReservation.class);
            overlapping
                    .select(reservation.get("id"))
                    .where(
                            cb.equal(reservation.get("room"), root),
                            cb.equal(reservation.get("status"), ReservationStatus.HELD),
                            cb.lessThan(reservation.get("startDate"), endDate),
                            cb.greaterThan(reservation.get("endDate"), startDate));
            return cb.not(cb.exists(overlapping));
        };
    }

    /**
     * Параметры поиска для {@link #bookable(RoomFilter)}; {@code null} означает «без ограничения».
     */
    public record RoomFilter(
            Long hotelId,
            String city,
            RoomType type,
            Integer minCapacity,
            BigDecimal maxPrice,
            LocalDate startDate,
            LocalDate endDate) {}
}
