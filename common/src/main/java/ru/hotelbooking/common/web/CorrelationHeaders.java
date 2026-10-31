package ru.hotelbooking.common.web;

public final class CorrelationHeaders {

    public static final String TRACE_ID = "X-Trace-Id";
    public static final String BOOKING_ID = "X-Booking-Id";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_BOOKING_ID = "bookingId";

    private CorrelationHeaders() {}
}
