package ru.hotelbooking.hotel.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import ru.hotelbooking.hotel.domain.Hotel;

public interface HotelRepository extends JpaRepository<Hotel, Long>, JpaSpecificationExecutor<Hotel> {

    /** Распределение загрузки по всем отелям, посчитанное одним агрегирующим запросом. */
    @Query("""
            select new ru.hotelbooking.hotel.repository.HotelLoadRow(
                h.id, h.name, h.city,
                count(r.id),
                coalesce(sum(case when r.available = true then 1L else 0L end), 0L),
                coalesce(sum(r.timesBooked), 0L),
                coalesce(min(r.timesBooked), 0L),
                coalesce(max(r.timesBooked), 0L),
                coalesce(avg(r.timesBooked), 0.0),
                coalesce(sum(case when r.available = true and r.timesBooked = 0 then 1L else 0L end), 0L))
            from Hotel h left join Room r on r.hotel = h
            group by h.id, h.name, h.city
            order by h.id
            """)
    List<HotelLoadRow> summarizeLoad();
}
