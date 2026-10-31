package ru.hotelbooking.booking.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hotelbooking.booking.api.dto.CreateUserRequest;
import ru.hotelbooking.booking.api.dto.UpdateUserRequest;
import ru.hotelbooking.booking.api.dto.UserResponse;
import ru.hotelbooking.booking.service.UserService;
import ru.hotelbooking.common.security.AuthenticatedUser;
import ru.hotelbooking.common.security.Authorities;
import ru.hotelbooking.common.security.CurrentUser;
import ru.hotelbooking.common.web.Locations;
import ru.hotelbooking.common.web.PageResponse;

@Tag(name = "Users (ADMIN)")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@PreAuthorize(Authorities.ADMIN)
public class UserAdminController {

    private final UserService userService;

    @Operation(summary = "List users")
    @GetMapping
    public PageResponse<UserResponse> list(@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return userService.list(pageable);
    }

    @Operation(summary = "Get a user")
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable long id) {
        return userService.get(id);
    }

    @Operation(summary = "Create a user with any role")
    @PostMapping
    public ResponseEntity<UserResponse> create(@RequestBody @Valid CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.created(Locations.ofCreated(created.id())).body(created);
    }

    @Operation(summary = "Update username, password and/or role (null fields are kept)")
    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable long id, @RequestBody @Valid UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @Operation(summary = "Delete a user without upcoming bookings")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, @CurrentUser AuthenticatedUser actor) {
        userService.delete(id, actor);
        return ResponseEntity.noContent().build();
    }
}
