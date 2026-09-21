package com.smartparking.service;

import com.smartparking.entity.Location;
import com.smartparking.entity.ParkingSlot;
import com.smartparking.entity.Role;
import com.smartparking.entity.SlotStatus;
import com.smartparking.entity.User;
import com.smartparking.exception.OverlappingBookingException;
import com.smartparking.repository.LocationRepository;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookingServiceConcurrencyTest {

    @Autowired private BookingService bookingService;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ParkingSlotRepository slotRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void onlyOneConcurrentBookingSucceedsForOverlappingWindow() throws InterruptedException {
        Location location = locationRepository.save(new Location("Concurrency Test Lot", "addr", 1));
        ParkingSlot slot = slotRepository.save(new ParkingSlot(location, 1, SlotStatus.AVAILABLE));

        int threadCount = 10;
        List<User> users = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            users.add(userRepository.save(new User(
                    "concurrent" + i + "_" + System.nanoTime() + "@test.com",
                    passwordEncoder.encode("password123"),
                    Role.USER)));
        }

        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            User user = users.get(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    bookingService.createBooking(user.getEmail(), slot.getId(), start, end);
                    successCount.incrementAndGet();
                } catch (OverlappingBookingException e) {
                    conflictCount.incrementAndGet();
                } catch (InterruptedException ignored) {
                }
            }));
        }

        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            try { f.get(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
    }
}
