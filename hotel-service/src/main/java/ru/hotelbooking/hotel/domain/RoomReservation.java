package ru.hotelbooking.hotel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Слот номера на полуоткрытый период {@code [startDate, endDate)}, созданный шагом саги «подтверждение доступности».
 * {@code requestId} — ключ идемпотентности этого шага.
 */
@Entity
@Table(name = "room_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "request_id", nullable = false, unique = true, length = 100)
    private String requestId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    private RoomReservation(Room room, SlotCommand command, ReservationStatus status, Instant releasedAt) {
        this.room = room;
        this.bookingId = command.bookingId();
        this.requestId = command.requestId();
        this.startDate = command.startDate();
        this.endDate = command.endDate();
        this.status = status;
        this.releasedAt = releasedAt;
    }

    public static RoomReservation hold(Room room, SlotCommand command) {
        return new RoomReservation(room, command, ReservationStatus.HELD, null);
    }

    /**
     * Освобождение, пришедшее раньше удержания (или вместо него). Благодаря сохранённой записи запоздалое удержание с
     * тем же request id завершится ошибкой, а не заблокирует номер под уже отменённую бронь.
     */
    public static RoomReservation tombstone(Room room, SlotCommand command, Instant now) {
        return new RoomReservation(room, command, ReservationStatus.RELEASED, now);
    }

    public boolean isHeld() {
        return status == ReservationStatus.HELD;
    }

    public boolean belongsTo(long roomId) {
        return room.getId() == roomId;
    }

    public void release(Instant now) {
        this.status = ReservationStatus.RELEASED;
        this.releasedAt = now;
    }
}
