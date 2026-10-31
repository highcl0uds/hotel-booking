package ru.hotelbooking.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class CorrelationMdcFilter extends OncePerRequestFilter {

    private static final Pattern BOOKING_ID = Pattern.compile("\\d{1,19}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String bookingId = request.getHeader(CorrelationHeaders.BOOKING_ID);
        if (bookingId == null || !BOOKING_ID.matcher(bookingId).matches()) {
            chain.doFilter(request, response);
            return;
        }
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationHeaders.MDC_BOOKING_ID, bookingId)) {
            chain.doFilter(request, response);
        }
    }
}
