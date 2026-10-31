package ru.hotelbooking.booking.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hotelbooking.booking.api.dto.CreateUserRequest;
import ru.hotelbooking.booking.api.dto.UpdateUserRequest;
import ru.hotelbooking.booking.api.dto.UserResponse;
import ru.hotelbooking.booking.domain.BookingStatus;
import ru.hotelbooking.booking.domain.User;
import ru.hotelbooking.booking.mapper.UserMapper;
import ru.hotelbooking.booking.repository.BookingRepository;
import ru.hotelbooking.booking.repository.UserRepository;
import ru.hotelbooking.common.error.ConflictException;
import ru.hotelbooking.common.error.NotFoundException;
import ru.hotelbooking.common.security.AuthenticatedUser;
import ru.hotelbooking.common.web.PageResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final Clock clock;

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw usernameTaken(request.username());
        }
        User user = userRepository.save(
                new User(request.username(), passwordEncoder.encode(request.password()), request.role()));
        log.info("User created: id={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse get(long id) {
        return userMapper.toResponse(findUser(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.of(userRepository.findAll(pageable), userMapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse update(long id, UpdateUserRequest request) {
        User user = findUser(id);
        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsernameAndIdNot(request.username(), id)) {
                throw usernameTaken(request.username());
            }
            user.setUsername(request.username());
        }
        if (request.password() != null) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        log.info("User updated: id={}, role={}", id, user.getRole());
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void delete(long id, AuthenticatedUser actor) {
        User user = findUser(id);
        if (actor.id() != null && actor.id() == id) {
            throw new ConflictException(
                    BookingErrorCodes.CANNOT_DELETE_SELF, "Administrators cannot delete themselves");
        }
        if (bookingRepository.existsByUserIdAndStatusInAndEndDateAfter(
                id, EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED), LocalDate.now(clock))) {
            throw new ConflictException(
                    BookingErrorCodes.USER_HAS_ACTIVE_BOOKINGS,
                    "User %d has upcoming bookings; cancel them first".formatted(id));
        }
        // каскад удалил бы бронь вместе с недоставленным освобождением, и слот остался бы удержанным навсегда
        if (bookingRepository.existsByUserIdAndCompensationPendingTrue(id)) {
            throw new ConflictException(
                    BookingErrorCodes.USER_HAS_ACTIVE_BOOKINGS,
                    "User %d has cancelled bookings whose rooms are not released yet; retry later".formatted(id));
        }
        // прошлые брони и их историю удаляет ON DELETE CASCADE
        userRepository.delete(user);
        log.info("User deleted: id={}", id);
    }

    private User findUser(long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("user", id));
    }

    private static ConflictException usernameTaken(String username) {
        return new ConflictException(
                BookingErrorCodes.USERNAME_TAKEN, "Username '%s' is already taken".formatted(username));
    }
}
