package ru.hotelbooking.hotel.service;

import org.springframework.data.domain.Pageable;
import ru.hotelbooking.common.web.PageResponse;
import ru.hotelbooking.hotel.api.dto.HotelRequest;
import ru.hotelbooking.hotel.api.dto.HotelResponse;
import ru.hotelbooking.hotel.api.dto.HotelSearchCriteria;

public interface HotelService {

    HotelResponse create(HotelRequest request);

    HotelResponse get(long id);

    PageResponse<HotelResponse> search(HotelSearchCriteria criteria, Pageable pageable);

    HotelResponse update(long id, HotelRequest request);

    /** Удаляет отель вместе с номерами; запрещено, пока у какого-либо номера есть предстоящий резерв. */
    void delete(long id);
}
