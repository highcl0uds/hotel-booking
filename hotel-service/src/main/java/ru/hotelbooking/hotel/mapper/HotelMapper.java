package ru.hotelbooking.hotel.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import ru.hotelbooking.hotel.api.dto.HotelLoadResponse;
import ru.hotelbooking.hotel.api.dto.HotelRequest;
import ru.hotelbooking.hotel.api.dto.HotelResponse;
import ru.hotelbooking.hotel.domain.Hotel;
import ru.hotelbooking.hotel.repository.HotelLoadRow;

@Mapper
public interface HotelMapper {

    HotelResponse toResponse(Hotel hotel);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Hotel toEntity(HotelRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void update(@MappingTarget Hotel hotel, HotelRequest request);

    @Mapping(target = "loadSpread", expression = "java(row.timesBookedMax() - row.timesBookedMin())")
    HotelLoadResponse toLoadResponse(HotelLoadRow row);
}
