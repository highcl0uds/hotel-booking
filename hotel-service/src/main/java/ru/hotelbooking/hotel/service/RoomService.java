package ru.hotelbooking.hotel.service;

import java.util.List;
import org.springframework.data.domain.Pageable;
import ru.hotelbooking.common.web.PageResponse;
import ru.hotelbooking.hotel.api.dto.RoomRequest;
import ru.hotelbooking.hotel.api.dto.RoomResponse;
import ru.hotelbooking.hotel.api.dto.RoomSearchCriteria;

public interface RoomService {

    RoomResponse create(RoomRequest request);

    RoomResponse get(long id);

    /**
     * Номера, доступные для бронирования (работающие и, если задан период, не занятые на него), без особого порядка.
     */
    PageResponse<RoomResponse> findBookable(RoomSearchCriteria criteria, Pageable pageable);

    /**
     * Те же доступные номера, что и {@link #findBookable} (свободные на период, если он задан), по возрастанию
     * {@code timesBooked}, затем по id: первыми идут наименее загруженные номера, чтобы загрузка распределялась
     * равномерно и ни один номер не простаивал.
     */
    List<RoomResponse> recommend(RoomSearchCriteria criteria);

    RoomResponse update(long id, RoomRequest request);

    void delete(long id);
}
