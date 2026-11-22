package com.driveease.ai;

import com.driveease.dto.Dtos.ChatRequest;
import com.driveease.dto.Dtos.ChatResponse;
import com.driveease.model.*;
import com.driveease.repository.BookingRepository;
import com.driveease.repository.VehicleRepository;
import com.driveease.service.Catalog;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Front door of the AI assistant.
 * Uses Claude when an API key is configured (understands any language, including Telugu),
 * and the offline {@link LocalAiEngine} otherwise or whenever Claude is unavailable.
 */
@Service
public class AiAssistantService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final ClaudeAiClient claude;
    private final LocalAiEngine local;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;

    public AiAssistantService(ClaudeAiClient claude, LocalAiEngine local,
                              VehicleRepository vehicleRepository, BookingRepository bookingRepository) {
        this.claude = claude;
        this.local = local;
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
    }

    public String mode() {
        return claude.isEnabled() ? "claude" : "local";
    }

    public ChatResponse chat(ChatRequest req, Customer customer) {
        String message = req.message() == null ? "" : req.message().trim();
        if (message.length() > 1500) {
            message = message.substring(0, 1500);
        }
        if (claude.isEnabled() && !message.isEmpty()) {
            List<Vehicle> fleet = vehicleRepository.findAll();
            Optional<String> answer = claude.ask(systemPrompt(fleet, customer), req.history(), message);
            if (answer.isPresent()) {
                List<Vehicle> cards = local.matchVehicles(answer.get(), fleet).stream().limit(3).collect(Collectors.toList());
                return new ChatResponse(answer.get(), cards,
                        List.of("Suggest a car", "Insurance plans", "Show offers"), "claude");
            }
        }
        return local.answer(message, customer);
    }

    private String systemPrompt(List<Vehicle> fleet, Customer customer) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are DriveEase AI, the friendly assistant of DriveEase, a car and bike rental company in ")
                .append("Hyderabad, Andhra Pradesh and nearby cities in India. Help customers choose vehicles, understand prices, ")
                .append("insurance, payments, cancellation, documents and trip safety.\n\n")
                .append("Rules:\n")
                .append("- Reply in the customer's language (English, Telugu or Tenglish) and keep answers short: under 150 words, ")
                .append("using **bold** and '- ' bullet lists only (no tables, no headings).\n")
                .append("- Only recommend vehicles from the fleet list below, by their exact names, and quote prices from it. ")
                .append("Prices are in Indian Rupees; write them as Rs.1,499.\n")
                .append("- Never invent bookings, discounts or policies that are not listed here. ")
                .append("You cannot create, change or cancel bookings yourself: tell the customer which page to use ")
                .append("(Vehicles, My Bookings, Track).\n")
                .append("- In any emergency tell them to call 112 (ambulance 108) and press SOS on the Track page.\n\n");

        sb.append("FLEET (name | type | category | fuel | transmission | seats | price/day | price/hour | free km/day | deposit | rating | available):\n");
        for (Vehicle v : fleet) {
            sb.append("- ").append(v.getName()).append(" | ").append(v.getType()).append(" | ").append(v.getCategory())
                    .append(" | ").append(v.getFuelType()).append(" | ").append(v.getTransmission())
                    .append(" | ").append(v.getSeats()).append(" | Rs.").append((long) v.getPricePerDay())
                    .append(" | Rs.").append((long) v.getPricePerHour()).append(" | ").append(v.getKmLimitPerDay())
                    .append(" | Rs.").append((long) v.getDeposit()).append(" | ").append(v.getRating())
                    .append(" | ").append(v.isAvailable() ? "yes" : "maintenance").append("\n");
        }

        sb.append("\nPRICING: rental = days x day rate + extra hours x hour rate (capped at a day). Minimum 4 hours. ")
                .append("Weekend days +10% of day rate. Long rental discount 3+ days 5%, 7+ days 10%, 30+ days 20%. ")
                .append("Driver Rs.").append((long) Catalog.DRIVER_PER_DAY).append("/day (cars only). ")
                .append("One-way drop fee Rs.").append((long) Catalog.ONE_WAY_FEE_CAR).append(" car / Rs.")
                .append((long) Catalog.ONE_WAY_FEE_BIKE).append(" bike. GST 18% on everything. ")
                .append("Refundable security deposit, halved with Premium insurance. Extra km charged per km. ")
                .append("Late return: 1 hour grace, then 1.5x hourly rate.\n");

        sb.append("\nINSURANCE (per day, car / bike):\n");
        for (InsurancePlan p : InsurancePlan.values()) {
            sb.append("- ").append(p.getLabel()).append(": Rs.").append((long) p.getCarPerDay()).append(" / Rs.")
                    .append((long) p.getBikePerDay()).append(". ").append(p.getDescription())
                    .append(" Car deductible Rs.").append((long) p.getDeductible()).append(".\n");
        }

        sb.append("\nADD-ONS:\n");
        for (Catalog.Addon a : Catalog.ADDONS) {
            sb.append("- ").append(a.name()).append(" (").append(a.appliesTo()).append("): Rs.").append((long) a.price())
                    .append(a.perDay() ? "/day" : " one-time").append("\n");
        }

        sb.append("\nCOUPONS:\n");
        for (Catalog.Coupon c : Catalog.COUPONS) {
            sb.append("- ").append(c.code()).append(": ").append(c.description()).append("\n");
        }

        sb.append("\nLOCATIONS: ").append(Catalog.LOCATIONS.stream().map(Catalog.Location::name)
                .collect(Collectors.joining("; "))).append("\n");

        sb.append("\nPAYMENTS: UPI, credit/debit cards (Visa, Mastercard, RuPay, Amex), net banking, wallets ")
                .append("(PhonePe, Paytm, Amazon Pay, MobiKwik) or pay at pickup. Cards and UPI can be saved as secure tokens; ")
                .append("full card numbers and CVV are never stored.\n")
                .append("CANCELLATION: 24h+ before pickup 100% refund, 4-24h 50%, under 4h none. Unpaid bookings expire after 30 minutes.\n")
                .append("DOCUMENTS: driving licence (1+ year old) and Aadhaar/passport; age 21+ for cars, 18+ for bikes. ")
                .append("With a driver only ID proof is needed.\n")
                .append("TRIP: show the 4-digit trip OTP at pickup. Live GPS tracking, over-speed alerts (90 km/h car, 70 km/h bike), ")
                .append("SOS / breakdown / medical buttons and a share-trip link are on the Track page. Helpline 1800-123-4567.\n");

        sb.append("\nToday is ").append(LocalDateTime.now().format(DT)).append(".\n");

        if (customer != null) {
            sb.append("\nLOGGED-IN CUSTOMER: ").append(customer.getFullName())
                    .append(", loyalty points ").append(customer.getLoyaltyPoints()).append(".\nTheir recent bookings:\n");
            List<Booking> bookings = bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
            if (bookings.isEmpty()) {
                sb.append("- none yet\n");
            }
            bookings.stream().limit(5).forEach(b -> sb.append("- ").append(b.getBookingRef()).append(": ")
                    .append(b.getVehicle().getName()).append(", ").append(b.getStatus()).append(", ")
                    .append(b.getStartTime().format(DT)).append(" to ").append(b.getEndTime().format(DT))
                    .append(", ").append(b.getPickupLocation()).append(" -> ").append(b.getDropLocation())
                    .append(", total Rs.").append((long) b.getTotalAmount())
                    .append(b.getStatus() == BookingStatus.CONFIRMED ? ", trip OTP " + b.getTripOtp() : "")
                    .append(b.getDriver() != null ? ", driver " + b.getDriver().getName() + " " + b.getDriver().getPhone() : "")
                    .append("\n"));
        } else {
            sb.append("\nThe customer is not logged in. Do not reveal details of any booking ID; ask them to login.\n");
        }
        return sb.toString();
    }
}
