package com.driveease.repository;

import com.driveease.model.Alert;
import com.driveease.model.AlertStatus;
import com.driveease.model.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findAllByOrderByCreatedAtDesc();

    List<Alert> findByBookingRefOrderByCreatedAtDesc(String bookingRef);

    long countByStatusNot(AlertStatus status);

    long countByBookingRefAndTypeAndCreatedAtAfter(String bookingRef, AlertType type, LocalDateTime after);
}
