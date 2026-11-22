package com.driveease.service;

import com.driveease.dto.Dtos.PaymentRequest;
import com.driveease.model.PaymentMethodType;
import com.driveease.model.SavedPaymentMethod;
import com.driveease.web.ApiException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated payment gateway (works offline, no real money).
 * Validates UPI IDs, cards (Luhn check, expiry, CVV), net banking and wallets the way a real gateway would.
 *
 * To go live, replace {@link #charge} with a call to Razorpay / PayU / Stripe using their Java SDK.
 *
 * Test values:
 *   UPI  success: anything like  name@okhdfc     failure: fail@upi
 *   Card success: 4111 1111 1111 1111  (any future expiry, any 3-digit CVV)
 *   Card declined: 4000 0000 0000 0002
 */
@Service
public class PaymentGatewayService {

    /** Result of a gateway call. */
    public record GatewayResult(boolean success, PaymentMethodType method, String transactionId,
                                String maskedDetails, String message,
                                String brand, String expiry, String holderName, String label) {
    }

    public GatewayResult charge(PaymentRequest req, double amount, SavedPaymentMethod saved) {
        if (amount <= 0) {
            throw ApiException.badRequest("Nothing to pay");
        }
        if (saved != null) {
            return chargeSaved(req, saved);
        }
        PaymentMethodType method = parseMethod(req.method());
        return switch (method) {
            case UPI -> upi(req.upiId());
            case CARD -> card(req.cardNumber(), req.cardHolder(), req.cardExpiry(), req.cardCvv());
            case NET_BANKING -> netBanking(req.bank());
            case WALLET -> wallet(req.walletProvider(), req.walletPhone());
            case PAY_AT_PICKUP -> new GatewayResult(true, method, null, "Pay at pickup",
                    "Booking confirmed. Pay at the counter by cash / card / UPI when you pick up the vehicle.",
                    null, null, null, "Pay at pickup");
        };
    }

    /** Validates an instrument for saving without charging it. */
    public GatewayResult verifyForSaving(PaymentRequest req) {
        PaymentMethodType method = parseMethod(req.method());
        return switch (method) {
            case UPI -> upi(req.upiId());
            case CARD -> card(req.cardNumber(), req.cardHolder(), req.cardExpiry(), req.cardCvv());
            default -> throw ApiException.badRequest("Only UPI IDs and cards can be saved");
        };
    }

    private GatewayResult chargeSaved(PaymentRequest req, SavedPaymentMethod saved) {
        if (saved.getType() == PaymentMethodType.CARD) {
            if (req.cardCvv() == null || !req.cardCvv().matches("[0-9]{3,4}")) {
                throw ApiException.badRequest("Please enter the CVV of your saved card");
            }
            if (saved.getExpiry() != null && isExpired(saved.getExpiry())) {
                throw ApiException.paymentFailed("This saved card has expired. Please use another card.");
            }
        }
        return new GatewayResult(true, saved.getType(), txnId(), saved.getMaskedValue(),
                "Payment successful using saved " + saved.getLabel(),
                saved.getBrand(), saved.getExpiry(), saved.getHolderName(), saved.getLabel());
    }

    private GatewayResult upi(String upiId) {
        if (upiId == null || !upiId.trim().matches("^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$")) {
            throw ApiException.badRequest("Please enter a valid UPI ID, e.g. yourname@okhdfc");
        }
        String id = upiId.trim().toLowerCase(Locale.ROOT);
        String masked = maskUpi(id);
        if (id.startsWith("fail@")) {
            return new GatewayResult(false, PaymentMethodType.UPI, txnId(), masked,
                    "UPI collect request was declined by the payer's bank", null, null, null, "UPI " + masked);
        }
        return new GatewayResult(true, PaymentMethodType.UPI, txnId(), masked,
                "UPI payment successful", "UPI", null, null, "UPI " + masked);
    }

    private GatewayResult card(String number, String holder, String expiry, String cvv) {
        String digits = number == null ? "" : number.replaceAll("[\\s-]", "");
        if (!digits.matches("[0-9]{13,19}") || !luhn(digits)) {
            throw ApiException.badRequest("Invalid card number");
        }
        if (holder == null || holder.isBlank()) {
            throw ApiException.badRequest("Please enter the name on the card");
        }
        if (expiry == null || !expiry.trim().matches("(0[1-9]|1[0-2])/[0-9]{2}")) {
            throw ApiException.badRequest("Expiry must be in MM/YY format");
        }
        if (isExpired(expiry.trim())) {
            throw ApiException.badRequest("This card has expired");
        }
        if (cvv == null || !cvv.matches("[0-9]{3,4}")) {
            throw ApiException.badRequest("Invalid CVV");
        }
        String brand = brand(digits);
        String masked = brand + " **** " + digits.substring(digits.length() - 4);
        if (digits.equals("4000000000000002")) {
            return new GatewayResult(false, PaymentMethodType.CARD, txnId(), masked,
                    "Card declined by issuing bank (insufficient funds)", brand, expiry.trim(), holder.trim(), masked);
        }
        return new GatewayResult(true, PaymentMethodType.CARD, txnId(), masked,
                "Card payment successful (3-D Secure verified)", brand, expiry.trim(), holder.trim(), masked);
    }

    private GatewayResult netBanking(String bank) {
        if (bank == null || !Catalog.BANKS.contains(bank)) {
            throw ApiException.badRequest("Please choose your bank");
        }
        return new GatewayResult(true, PaymentMethodType.NET_BANKING, txnId(), bank,
                "Net banking payment successful via " + bank, null, null, null, bank);
    }

    private GatewayResult wallet(String provider, String phone) {
        if (provider == null || !Catalog.WALLETS.contains(provider)) {
            throw ApiException.badRequest("Please choose a wallet");
        }
        AuthService.validatePhone(phone);
        String digits = phone.replaceAll("[^0-9]", "");
        String masked = provider + " (******" + digits.substring(digits.length() - 4) + ")";
        return new GatewayResult(true, PaymentMethodType.WALLET, txnId(), masked,
                provider + " wallet payment successful", null, null, null, masked);
    }

    // ------------------------------------------------------------------ helpers

    public static PaymentMethodType parseMethod(String method) {
        if (method == null || method.isBlank()) {
            throw ApiException.badRequest("Please choose a payment method");
        }
        try {
            return PaymentMethodType.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown payment method: " + method);
        }
    }

    /** Luhn checksum used by every card network. */
    static boolean luhn(String digits) {
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    static String brand(String digits) {
        if (digits.startsWith("4")) return "VISA";
        if (digits.startsWith("34") || digits.startsWith("37")) return "AMEX";
        int two = Integer.parseInt(digits.substring(0, 2));
        int four = Integer.parseInt(digits.substring(0, 4));
        if ((two >= 51 && two <= 55) || (four >= 2221 && four <= 2720)) return "MASTERCARD";
        if (digits.startsWith("60") || digits.startsWith("65") || digits.startsWith("81")
                || digits.startsWith("82") || digits.startsWith("508")) return "RUPAY";
        return "CARD";
    }

    private static boolean isExpired(String mmYY) {
        try {
            YearMonth ym = YearMonth.parse(mmYY, DateTimeFormatter.ofPattern("MM/yy"));
            return ym.isBefore(YearMonth.now());
        } catch (Exception e) {
            return true;
        }
    }

    private static String maskUpi(String id) {
        int at = id.indexOf('@');
        String user = id.substring(0, at);
        String shown = user.length() <= 2 ? user : user.substring(0, 2);
        return shown + "***" + id.substring(at);
    }

    private static String txnId() {
        return "TXN" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    public static String newGatewayToken() {
        return "tok_" + Long.toHexString(ThreadLocalRandom.current().nextLong()).replace("-", "")
                + Long.toHexString(System.nanoTime());
    }
}
