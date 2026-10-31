package ru.hotelbooking.booking.mapper;

import org.mapstruct.Mapper;
import ru.hotelbooking.booking.api.dto.UserResponse;
import ru.hotelbooking.booking.domain.User;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);
}
