package com.driveease.repository;

import com.driveease.model.TrackingPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackingPointRepository extends JpaRepository<TrackingPoint, Long> {

    List<TrackingPoint> findTop400ByBookingIdOrderByIdDesc(Long bookingId);
}
