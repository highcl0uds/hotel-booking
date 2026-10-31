package ru.hotelbooking.booking.client.dto;

/**
 * Номер в том виде, в каком его возвращает эндпойнт рекомендаций Hotel Service.
 */
public record HotelRoom(Long id, Long hotelId, String number, long timesBooked) {}
