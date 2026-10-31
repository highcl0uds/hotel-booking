package ru.hotelbooking.hotel.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import ru.hotelbooking.hotel.api.dto.RoomRequest;
import ru.hotelbooking.hotel.api.dto.RoomResponse;
import ru.hotelbooking.hotel.api.dto.RoomSearchCriteria;
import ru.hotelbooking.hotel.api.dto.RoomStatsResponse;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.repository.RoomSpecifications.RoomFilter;

@Mapper
public interface RoomMapper {

    @Mapping(target = "hotelId", source = "hotel.id")
    RoomResponse toResponse(Room room);

    @Mapping(target = "roomId", source = "room.id")
    @Mapping(target = "hotelId", source = "room.hotel.id")
    RoomStatsResponse toStatsResponse(Room room, long upcomingReservations);

    /** Отель находит сервис; статистика и поля аудита никогда не берутся из запроса. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    @Mapping(target = "available", expression = "java(request.availableOrDefault())")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void update(@MappingTarget Room room, RoomRequest request);

    RoomFilter toFilter(RoomSearchCriteria criteria);
}
