package ru.hotelbooking.hotel.api.dto;

import java.math.BigDecimal;
import ru.hotelbooking.hotel.domain.RoomType;

public record RoomResponse(
        Long id,
        Long hotelId,
        String number,
        RoomType type,
        int capacity,
        BigDecimal pricePerNight,
        boolean available,
        long timesBooked) {}
