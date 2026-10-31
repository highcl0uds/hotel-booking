package ru.hotelbooking.hotel.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.hotelbooking.hotel.domain.Room;

public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {

    /**
     * {@code SELECT ... FOR UPDATE}: выстраивает в очередь параллельные удержания и освобождения одного номера, поэтому
     * проверка пересечений и вставка резерва выполняются атомарно.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(long id);

    List<Room> findByHotelIdOrderById(long hotelId);

    /** Блокирует все номера отеля, чтобы во время удаления отеля нельзя было удержать ни одного слота. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.hotel.id = :hotelId order by r.id")
    List<Room> lockByHotelId(long hotelId);

    boolean existsByHotelIdAndNumber(long hotelId, String number);

    boolean existsByHotelIdAndNumberAndIdNot(long hotelId, String number, long id);
}
