package ru.hotelbooking.hotel.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.support.TransactionTemplate;
import ru.hotelbooking.hotel.domain.Hotel;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.domain.RoomType;
import ru.hotelbooking.hotel.domain.SlotCommand;
import ru.hotelbooking.hotel.repository.HotelRepository;
import ru.hotelbooking.hotel.repository.RoomRepository;
import ru.hotelbooking.hotel.service.RoomAvailabilityService;

/**
 * Построители тестовых данных, работающие через настоящие репозитории и сервисы.
 */
@TestComponent
public class HotelFixtures {

    private final AtomicLong bookingIds = new AtomicLong(1000);
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomAvailabilityService availabilityService;
    private final TransactionTemplate transactionTemplate;

    public HotelFixtures(
            HotelRepository hotelRepository,
            RoomRepository roomRepository,
            RoomAvailabilityService availabilityService,
            TransactionTemplate transactionTemplate) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.availabilityService = availabilityService;
        this.transactionTemplate = transactionTemplate;
    }

    public Hotel hotel(String name, String city) {
        Hotel hotel = new Hotel();
        hotel.setName(name);
        hotel.setCity(city);
        hotel.setAddress("Main st., 1");
        hotel.setStars(4);
        return hotelRepository.save(hotel);
    }

    public Room room(Hotel hotel, String number) {
        return room(hotel, number, 0, true);
    }

    public Room room(Hotel hotel, String number, int timesBooked) {
        return room(hotel, number, timesBooked, true);
    }

    public Room roomOutOfService(Hotel hotel, String number) {
        return room(hotel, number, 0, false);
    }

    /** Удерживает номер через настоящий сервис — так же, как это делает сага бронирования. */
    public SlotCommand hold(Room room, LocalDate startDate, LocalDate endDate) {
        SlotCommand command = slot(startDate, endDate);
        availabilityService.confirmAvailability(room.getId(), command);
        return command;
    }

    public SlotCommand slot(LocalDate startDate, LocalDate endDate) {
        long bookingId = bookingIds.incrementAndGet();
        return new SlotCommand("hold-" + bookingId, bookingId, startDate, endDate);
    }

    public Room reload(Room room) {
        return transactionTemplate.execute(
                status -> roomRepository.findById(room.getId()).orElseThrow());
    }

    private Room room(Hotel hotel, String number, int timesBooked, boolean available) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber(number);
        room.setType(RoomType.STANDARD);
        room.setCapacity(2);
        room.setPricePerNight(new BigDecimal("5000.00"));
        room.setAvailable(available);
        for (int i = 0; i < timesBooked; i++) {
            room.registerBooking();
        }
        return roomRepository.save(room);
    }
}
