package ru.hotelbooking.booking.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hotelbooking.booking.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, long id);
}
