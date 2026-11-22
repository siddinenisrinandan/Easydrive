package com.driveease.repository;

import com.driveease.model.Booking;
import com.driveease.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingRef(String bookingRef);

    boolean existsByBookingRef(String bookingRef);

    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findAllByOrderByCreatedAtDesc();

    long countByStatus(BookingStatus status);

    /** Bookings of this vehicle that overlap [start, end). Used to prevent double booking. */
    @Query("select count(b) from Booking b where b.vehicle.id = :vehicleId and b.status in :statuses "
            + "and b.startTime < :endTime and b.endTime > :startTime")
    long countVehicleOverlaps(@Param("vehicleId") Long vehicleId,
                              @Param("statuses") Collection<BookingStatus> statuses,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

    /** Bookings of this driver that overlap [start, end). */
    @Query("select count(b) from Booking b where b.driver.id = :driverId and b.status in :statuses "
            + "and b.startTime < :endTime and b.endTime > :startTime")
    long countDriverOverlaps(@Param("driverId") Long driverId,
                             @Param("statuses") Collection<BookingStatus> statuses,
                             @Param("startTime") LocalDateTime startTime,
                             @Param("endTime") LocalDateTime endTime);

    @Query("select count(b) from Booking b where b.vehicle.id = :vehicleId")
    long countByVehicleId(@Param("vehicleId") Long vehicleId);

    /**
     * Writes only the GPS fields, and only while the trip is still ONGOING, so the background
     * tracker can never overwrite a status change (e.g. trip ended) made at the same moment.
     */
    @Modifying
    @Query("update Booking b set b.tripProgress = :progress, b.currentLat = :lat, b.currentLng = :lng, "
            + "b.currentSpeed = :speed, b.distanceKm = :distance where b.id = :id and b.status = :status")
    int updateTelemetry(@Param("id") Long id,
                        @Param("progress") double progress,
                        @Param("lat") Double lat,
                        @Param("lng") Double lng,
                        @Param("speed") double speed,
                        @Param("distance") double distance,
                        @Param("status") BookingStatus status);
}
