package ru.hotelbooking.booking.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.hotelbooking.booking.api.dto.AuthRequest;
import ru.hotelbooking.booking.api.dto.RegisterRequest;
import ru.hotelbooking.booking.api.dto.TokenResponse;
import ru.hotelbooking.booking.service.AuthService;

@Tag(name = "Authentication")
@SecurityRequirements
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a user (role USER) and get an access token")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@RequestBody @Valid RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "Log in and get an access token valid for one hour")
    @PostMapping("/auth")
    public TokenResponse authenticate(@RequestBody @Valid AuthRequest request) {
        return authService.authenticate(request);
    }
}
