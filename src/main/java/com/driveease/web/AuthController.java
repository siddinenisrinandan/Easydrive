package com.driveease.web;

import com.driveease.dto.Dtos.AuthResponse;
import com.driveease.dto.Dtos.LoginRequest;
import com.driveease.dto.Dtos.ProfileUpdate;
import com.driveease.dto.Dtos.RegisterRequest;
import com.driveease.model.Customer;
import com.driveease.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String TOKEN_HEADER = "X-Auth-Token";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        authService.logout(token);
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Customer me(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return authService.requireCustomer(token);
    }

    @PutMapping("/me")
    public Customer updateMe(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                             @RequestBody ProfileUpdate update) {
        Customer c = authService.requireCustomer(token);
        return authService.updateProfile(c, update);
    }
}
