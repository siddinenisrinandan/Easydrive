package com.driveease.repository;

import com.driveease.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBookingRefOrderByCreatedAtAsc(String bookingRef);

    List<Payment> findAllByOrderByCreatedAtDesc();
}
