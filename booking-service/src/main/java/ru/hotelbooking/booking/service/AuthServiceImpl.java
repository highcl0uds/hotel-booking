package ru.hotelbooking.booking.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hotelbooking.booking.api.dto.AuthRequest;
import ru.hotelbooking.booking.api.dto.CreateUserRequest;
import ru.hotelbooking.booking.api.dto.RegisterRequest;
import ru.hotelbooking.booking.api.dto.TokenResponse;
import ru.hotelbooking.booking.api.dto.UserResponse;
import ru.hotelbooking.booking.domain.Role;
import ru.hotelbooking.booking.domain.User;
import ru.hotelbooking.booking.mapper.UserMapper;
import ru.hotelbooking.booking.repository.UserRepository;
import ru.hotelbooking.common.error.ApiException;
import ru.hotelbooking.common.security.JwtTokenFactory;
import ru.hotelbooking.common.security.JwtTokenFactory.IssuedToken;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** BCrypt-хеш для сравнения, когда пользователя не существует: оба случая занимают одинаковое время. */
    private static final String DUMMY_HASH = "$2a$10$9wMCsGexhY.dgabt1sQu3ubTPbuXm9k0Fc56ARmIBvQAFsuLHXBSm";

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenFactory tokenFactory;
    private final UserMapper userMapper;

    @Override
    public TokenResponse register(RegisterRequest request) {
        UserResponse user =
                userService.create(new CreateUserRequest(request.username(), request.password(), Role.USER));
        log.info("User registered: id={}, username={}", user.id(), user.username());
        return token(user);
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse authenticate(AuthRequest request) {
        User user = userRepository.findByUsername(request.username()).orElse(null);
        String hash = user != null ? user.getPassword() : DUMMY_HASH;
        if (!passwordEncoder.matches(request.password(), hash) || user == null) {
            log.info("Authentication failed for username={}", request.username());
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, BookingErrorCodes.INVALID_CREDENTIALS, "Invalid username or password");
        }
        log.info("User authenticated: id={}", user.getId());
        return token(userMapper.toResponse(user));
    }

    private TokenResponse token(UserResponse user) {
        IssuedToken token = tokenFactory.issueUserToken(
                user.id(), user.username(), List.of(user.role().name()));
        return TokenResponse.bearer(token.value(), token.ttl().toSeconds(), token.expiresAt(), user);
    }
}
