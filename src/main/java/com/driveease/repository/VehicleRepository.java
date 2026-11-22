package com.driveease.repository;

import com.driveease.model.Vehicle;
import com.driveease.model.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByType(VehicleType type);
}
