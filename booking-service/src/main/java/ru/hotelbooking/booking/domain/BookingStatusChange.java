package ru.hotelbooking.booking.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Запись аудита о переходе статуса брони (след саги для брони).
 */
@Entity
@Table(name = "booking_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** {@code null} при создании брони. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 20)
    private BookingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 20)
    private BookingStatus toStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 40)
    private CancellationReason reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public BookingStatusChange(
            Booking booking,
            BookingStatus fromStatus,
            BookingStatus toStatus,
            CancellationReason reason,
            Instant changedAt) {
        this.booking = booking;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedAt = changedAt;
    }
}
