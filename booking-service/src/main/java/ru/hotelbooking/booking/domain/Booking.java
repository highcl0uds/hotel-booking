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
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Бронь номера на полуоткрытый период {@code [startDate, endDate)}.
 * Статус меняется только через {@code BookingStateService} условными обновлениями (compare-and-set).
 */
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Ключ идемпотентности, сгенерированный клиентом: повторный запрос с тем же ключом возвращает эту бронь. */
    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    /** Удерживаемый или забронированный номер; {@code null}, пока автоподбор его не выбрал. */
    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "hotel_id")
    private Long hotelId;

    @Column(name = "auto_select", nullable = false)
    private boolean autoSelect;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "cancel_reason", length = 40)
    private CancellationReason cancelReason;

    /**
     * Слот может ещё удерживаться в Hotel Service: освобождение должна (повторно) доставить фоновая задача
     * восстановления.
     */
    @Column(name = "compensation_pending", nullable = false)
    private boolean compensationPending;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static Booking pending(
            User user, UUID requestId, Long roomId, boolean autoSelect, LocalDate startDate, LocalDate endDate) {
        Booking booking = new Booking();
        booking.user = user;
        booking.requestId = requestId;
        booking.roomId = autoSelect ? null : roomId;
        booking.autoSelect = autoSelect;
        booking.startDate = startDate;
        booking.endDate = endDate;
        booking.status = BookingStatus.PENDING;
        return booking;
    }

    public boolean isOwnedBy(long userId) {
        return user.getId() == userId;
    }
}
