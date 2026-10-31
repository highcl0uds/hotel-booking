package ru.hotelbooking.hotel.api.dto;

import java.time.Instant;

public record HotelResponse(Long id, String name, String city, String address, Integer stars, Instant createdAt) {}
