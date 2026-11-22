package com.driveease.repository;

import com.driveease.model.SavedPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPaymentMethodRepository extends JpaRepository<SavedPaymentMethod, Long> {

    List<SavedPaymentMethod> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<SavedPaymentMethod> findByIdAndCustomerId(Long id, Long customerId);
}
