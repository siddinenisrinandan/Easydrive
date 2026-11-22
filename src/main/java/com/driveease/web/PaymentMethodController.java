package com.driveease.web;

import com.driveease.dto.Dtos.PaymentRequest;
import com.driveease.model.Customer;
import com.driveease.model.SavedPaymentMethod;
import com.driveease.repository.SavedPaymentMethodRepository;
import com.driveease.service.AuthService;
import com.driveease.service.BookingService;
import com.driveease.service.PaymentGatewayService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.driveease.web.AuthController.TOKEN_HEADER;

/** Saved cards / UPI IDs ("wallet") of the logged-in customer. */
@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

    private final AuthService authService;
    private final SavedPaymentMethodRepository repository;
    private final PaymentGatewayService gateway;
    private final BookingService bookingService;

    public PaymentMethodController(AuthService authService, SavedPaymentMethodRepository repository,
                                   PaymentGatewayService gateway, BookingService bookingService) {
        this.authService = authService;
        this.repository = repository;
        this.gateway = gateway;
        this.bookingService = bookingService;
    }

    @GetMapping
    public List<SavedPaymentMethod> list(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        Customer c = authService.requireCustomer(token);
        return repository.findByCustomerIdOrderByCreatedAtDesc(c.getId());
    }

    @PostMapping
    public SavedPaymentMethod add(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                  @RequestBody PaymentRequest req) {
        Customer c = authService.requireCustomer(token);
        PaymentGatewayService.GatewayResult r = gateway.verifyForSaving(req);
        return bookingService.saveInstrument(c, r);
    }

    @PostMapping("/{id}/default")
    @Transactional
    public List<SavedPaymentMethod> makeDefault(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                                @PathVariable Long id) {
        Customer c = authService.requireCustomer(token);
        List<SavedPaymentMethod> all = repository.findByCustomerIdOrderByCreatedAtDesc(c.getId());
        if (all.stream().noneMatch(m -> m.getId().equals(id))) {
            throw ApiException.notFound("Payment method not found");
        }
        all.forEach(m -> m.setDefaultMethod(m.getId().equals(id)));
        return repository.saveAll(all);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                      @PathVariable Long id) {
        Customer c = authService.requireCustomer(token);
        SavedPaymentMethod m = repository.findByIdAndCustomerId(id, c.getId())
                .orElseThrow(() -> ApiException.notFound("Payment method not found"));
        repository.delete(m);
        return Map.of("ok", true);
    }
}
