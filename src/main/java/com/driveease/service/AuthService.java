package com.driveease.service;

import com.driveease.dto.Dtos.AuthResponse;
import com.driveease.dto.Dtos.LoginRequest;
import com.driveease.dto.Dtos.ProfileUpdate;
import com.driveease.dto.Dtos.RegisterRequest;
import com.driveease.model.Customer;
import com.driveease.repository.CustomerRepository;
import com.driveease.util.PasswordHasher;
import com.driveease.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Customer sign-up / login with token sessions, plus a simple admin login. */
@Service
public class AuthService {

    private final CustomerRepository customerRepository;
    private final String adminUsername;
    private final String adminPassword;
    private final Set<String> adminTokens = ConcurrentHashMap.newKeySet();

    public AuthService(CustomerRepository customerRepository,
                       @Value("${app.admin.username}") String adminUsername,
                       @Value("${app.admin.password}") String adminPassword) {
        this.customerRepository = customerRepository;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists. Please login.");
        }
        validatePhone(req.phone());
        Customer c = new Customer();
        c.setFullName(req.fullName().trim());
        c.setEmail(email);
        c.setPhone(normalizePhone(req.phone()));
        c.setPasswordHash(PasswordHasher.hash(req.password()));
        c.setCreatedAt(LocalDateTime.now());
        c.setLoyaltyPoints(100); // welcome bonus
        c.setAuthToken(PasswordHasher.randomToken(32));
        customerRepository.save(c);
        return new AuthResponse(c.getAuthToken(), c);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        Customer c = customerRepository.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!PasswordHasher.matches(req.password(), c.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        c.setAuthToken(PasswordHasher.randomToken(32));
        customerRepository.save(c);
        return new AuthResponse(c.getAuthToken(), c);
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        customerRepository.findByAuthToken(token).ifPresent(c -> {
            c.setAuthToken(null);
            customerRepository.save(c);
        });
    }

    /** Returns the logged-in customer or throws 401. */
    public Customer requireCustomer(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("Please login to continue");
        }
        return customerRepository.findByAuthToken(token)
                .orElseThrow(() -> ApiException.unauthorized("Your session has expired. Please login again."));
    }

    public Customer optionalCustomer(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return customerRepository.findByAuthToken(token).orElse(null);
    }

    @Transactional
    public Customer updateProfile(Customer c, ProfileUpdate p) {
        if (notBlank(p.fullName())) c.setFullName(p.fullName().trim());
        if (notBlank(p.phone())) {
            validatePhone(p.phone());
            c.setPhone(normalizePhone(p.phone()));
        }
        if (notBlank(p.dateOfBirth())) c.setDateOfBirth(p.dateOfBirth());
        if (notBlank(p.licenseNumber())) c.setLicenseNumber(p.licenseNumber().trim().toUpperCase(Locale.ROOT));
        if (notBlank(p.licenseExpiry())) c.setLicenseExpiry(p.licenseExpiry());
        if (notBlank(p.address())) c.setAddress(p.address().trim());
        if (notBlank(p.city())) c.setCity(p.city().trim());
        if (notBlank(p.emergencyContactName())) c.setEmergencyContactName(p.emergencyContactName().trim());
        if (notBlank(p.emergencyContactPhone())) {
            validatePhone(p.emergencyContactPhone());
            c.setEmergencyContactPhone(normalizePhone(p.emergencyContactPhone()));
        }
        return customerRepository.save(c);
    }

    // ------------------------------------------------------------------ admin

    public String adminLogin(String username, String password) {
        if (adminUsername.equals(username) && adminPassword.equals(password)) {
            String token = "adm_" + PasswordHasher.randomToken(24);
            adminTokens.add(token);
            return token;
        }
        throw ApiException.unauthorized("Invalid admin credentials");
    }

    public void requireAdmin(String token) {
        if (!isAdmin(token)) {
            throw ApiException.unauthorized("Admin login required");
        }
    }

    public boolean isAdmin(String token) {
        return token != null && adminTokens.contains(token);
    }

    // ------------------------------------------------------------------ helpers

    public static void validatePhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("91") && digits.length() == 12) {
            digits = digits.substring(2);
        }
        if (!digits.matches("[6-9][0-9]{9}")) {
            throw ApiException.badRequest("Please enter a valid 10-digit Indian mobile number");
        }
    }

    public static String normalizePhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("91") && digits.length() == 12) {
            digits = digits.substring(2);
        }
        return "+91 " + digits.substring(0, 5) + " " + digits.substring(5);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
