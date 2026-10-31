package ru.hotelbooking.hotel.service;

import java.util.List;
import org.springframework.data.domain.Pageable;
import ru.hotelbooking.common.web.PageResponse;
import ru.hotelbooking.hotel.api.dto.HotelLoadResponse;
import ru.hotelbooking.hotel.api.dto.OccupancyQuery;
import ru.hotelbooking.hotel.api.dto.OccupancyReport;
import ru.hotelbooking.hotel.api.dto.RoomStatsResponse;

public interface StatisticsService {

    /**
     * Счётчики бронирований номеров, при необходимости одного отеля; с сортировкой, например по {@code timesBooked}.
     */
    PageResponse<RoomStatsResponse> roomStats(Long hotelId, Pageable pageable);

    /** Насколько равномерно брони распределены по номерам каждого отеля. */
    List<HotelLoadResponse> hotelLoad();

    /** Загрузка отеля за период: по номерам, по дням, простаивающие номера. */
    OccupancyReport occupancy(long hotelId, OccupancyQuery query);
}
