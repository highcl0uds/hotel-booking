package ru.hotelbooking.hotel.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.hotelbooking.hotel.api.dto.ReservationResponse;
import ru.hotelbooking.hotel.api.dto.SlotRequest;
import ru.hotelbooking.hotel.domain.RoomReservation;
import ru.hotelbooking.hotel.domain.SlotCommand;

@Mapper
public interface ReservationMapper {

    @Mapping(target = "roomId", source = "room.id")
    @Mapping(target = "hotelId", source = "room.hotel.id")
    ReservationResponse toResponse(RoomReservation reservation);

    SlotCommand toCommand(SlotRequest request);
}
