package ru.hotelbooking.booking.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.hotelbooking.booking.api.dto.BookingResponse;
import ru.hotelbooking.booking.api.dto.BookingResponse.StatusChangeResponse;
import ru.hotelbooking.booking.api.dto.CreateBookingRequest;
import ru.hotelbooking.booking.domain.Booking;
import ru.hotelbooking.booking.domain.BookingStatusChange;
import ru.hotelbooking.booking.saga.BookingDraft;

@Mapper
public interface BookingMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "history", ignore = true)
    BookingResponse toResponse(Booking booking);

    @Mapping(target = "userId", source = "booking.user.id")
    @Mapping(target = "history", source = "history")
    BookingResponse toResponse(Booking booking, List<BookingStatusChange> history);

    @Mapping(target = "from", source = "fromStatus")
    @Mapping(target = "to", source = "toStatus")
    StatusChangeResponse toResponse(BookingStatusChange change);

    BookingDraft toDraft(Long userId, CreateBookingRequest request);
}
