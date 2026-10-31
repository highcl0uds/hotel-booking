package ru.hotelbooking.booking.service;

public final class BookingErrorCodes {

    public static final String USERNAME_TAKEN = "USERNAME_TAKEN";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String UNKNOWN_USER = "UNKNOWN_USER";
    public static final String USER_HAS_ACTIVE_BOOKINGS = "USER_HAS_ACTIVE_BOOKINGS";
    public static final String CANNOT_DELETE_SELF = "CANNOT_DELETE_SELF";

    public static final String NO_ROOMS_AVAILABLE = "NO_ROOMS_AVAILABLE";
    public static final String HOTEL_UNAVAILABLE = "HOTEL_UNAVAILABLE";
    public static final String BOOKING_IN_PROGRESS = "BOOKING_IN_PROGRESS";
    public static final String BOOKING_COMPLETED = "BOOKING_COMPLETED";
    public static final String BOOKING_NOT_CONFIRMED = "BOOKING_NOT_CONFIRMED";
    /** Тот же requestId прислали повторно с другими параметрами брони. */
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";

    private BookingErrorCodes() {}
}
