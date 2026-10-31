package ru.hotelbooking.hotel.repository;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import ru.hotelbooking.hotel.domain.Hotel;

public final class HotelSpecifications {

    private HotelSpecifications() {}

    public static Specification<Hotel> matching(String city, String name, Integer minStars) {
        List<Specification<Hotel>> specs = new ArrayList<>();
        if (city != null && !city.isBlank()) {
            specs.add((root, query, cb) -> cb.equal(cb.lower(root.get("city")), city.toLowerCase()));
        }
        if (name != null && !name.isBlank()) {
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
        }
        if (minStars != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("stars"), minStars));
        }
        return Specification.allOf(specs);
    }
}
