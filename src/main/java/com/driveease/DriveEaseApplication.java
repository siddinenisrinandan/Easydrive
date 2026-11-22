package com.driveease;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DriveEase - Car &amp; Bike Rental System.
 *
 * Start this class and open http://localhost:8080
 */
@SpringBootApplication
@EnableScheduling
public class DriveEaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriveEaseApplication.class, args);
    }
}
