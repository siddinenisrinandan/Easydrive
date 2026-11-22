package com.driveease.repository;

import com.driveease.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmailIgnoreCase(String email);

    Optional<Customer> findByAuthToken(String authToken);

    boolean existsByEmailIgnoreCase(String email);
}
