package ru.hotelbooking.hotel.service;

/**
 * Коды ошибок, специфичные для Hotel Service; на коды, связанные со слотами, опирается Booking Service.
 */
public final class HotelErrorCodes {

    /** Запрошенный период пересекается с другим резервом HELD. */
    public static final String ROOM_ALREADY_BOOKED = "ROOM_ALREADY_BOOKED";
    /** Номер выведен из эксплуатации ({@code available = false}). */
    public static final String ROOM_OUT_OF_SERVICE = "ROOM_OUT_OF_SERVICE";
    /** Запрос на удержание пришёл после компенсирующего освобождения. */
    public static final String RESERVATION_RELEASED = "RESERVATION_RELEASED";
    /** Ключ идемпотентности уже использован для другого номера. */
    public static final String REQUEST_ID_CONFLICT = "REQUEST_ID_CONFLICT";

    public static final String ROOM_NUMBER_TAKEN = "ROOM_NUMBER_TAKEN";
    public static final String HOTEL_HAS_ACTIVE_RESERVATIONS = "HOTEL_HAS_ACTIVE_RESERVATIONS";
    public static final String ROOM_HAS_ACTIVE_RESERVATIONS = "ROOM_HAS_ACTIVE_RESERVATIONS";

    private HotelErrorCodes() {}
}
