package com.driveease.ai;

import com.driveease.dto.Dtos.ChatResponse;
import com.driveease.dto.Dtos.Quote;
import com.driveease.dto.Dtos.QuoteLine;
import com.driveease.dto.Dtos.QuoteRequest;
import com.driveease.model.*;
import com.driveease.repository.BookingRepository;
import com.driveease.repository.VehicleRepository;
import com.driveease.service.Catalog;
import com.driveease.service.PricingService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Free, offline "smart assistant": intent detection + entity extraction + a recommendation engine
 * that works directly on the live fleet data. Understands English, Tenglish and common Telugu words.
 */
@Component
public class LocalAiEngine {

    private static final Pattern REF = Pattern.compile("\\bDE[0-9A-Z]{7}\\b");
    private static final Pattern DAYS = Pattern.compile("(\\d+)\\s*(days?|din|rojulu|roju|రోజులు|రోజు)");
    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*(hours?|hrs?|gantalu|ganta|గంటలు|గంట)");
    private static final Pattern PEOPLE = Pattern.compile("(\\d+)\\s*(people|persons?|members?|seats?|seater|pax|adults|mandi|friends|మంది)");
    private static final Pattern BUDGET = Pattern.compile(
            "(?:under|below|within|less than|budget|upto|up to|max|maximum|lopu|లోపు)\\s*(?:of\\s*)?(?:rs\\.?|₹|inr)?\\s*(\\d[\\d,]{2,})"
                    + "|(?:rs\\.?|₹|inr)\\s*(\\d[\\d,]{2,})"
                    + "|(\\d[\\d,]{2,})\\s*(?:rs|rupees|/-|rupayalu|రూపాయలు)");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;
    private final PricingService pricingService;

    public LocalAiEngine(VehicleRepository vehicleRepository, BookingRepository bookingRepository,
                         PricingService pricingService) {
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
        this.pricingService = pricingService;
    }

    public ChatResponse answer(String rawMessage, Customer customer) {
        String original = rawMessage == null ? "" : rawMessage.trim();
        String m = normalize(original);
        List<Vehicle> fleet = vehicleRepository.findAll();

        if (m.isBlank()) {
            return help(customer);
        }
        if (hasAny(m, "accident", "emergency", "sos", "breakdown", "broke down", "puncture", "flat tyre", "flat tire",
                "danger", "unsafe", "ambulance", "police", "pramadam", "ప్రమాదం")) {
            return emergency();
        }
        Matcher ref = REF.matcher(original.toUpperCase(Locale.ROOT));
        if (ref.find()) {
            return bookingStatus(ref.group(), customer);
        }
        if (hasAny(m, "my booking", "my bookings", "my trip", "my trips", "booking status", "my rides", "na booking")) {
            return myBookings(customer);
        }
        List<Vehicle> mentioned = matchVehicles(m, fleet);
        boolean priceWords = hasAny(m, "price", "cost", "rate", "how much", "charge", "fare", "tariff", "rent for",
                "quote", "estimate", "entha", "enta", "ఎంత", "ధర", "rent");
        if (priceWords && !mentioned.isEmpty()) {
            return priceQuote(mentioned.get(0), m);
        }
        if (hasAny(m, "insurance", "cover", "zero dep", "zero-dep", "damage", "deductible", "protection", "ఇన్సూరెన్స్", "bima")) {
            return insurance();
        }
        if (hasAny(m, "cancel", "refund", "రద్దు")) {
            return cancellation();
        }
        if (hasAny(m, "payment", "pay ", "upi", "card", "net banking", "netbanking", "wallet", "phonepe", "paytm", "gpay", "emi", "deposit")) {
            return payment();
        }
        if (hasAny(m, "document", "licence", "license", "aadhaar", "aadhar", "id proof", "kyc", "age limit", "eligib")) {
            return documents();
        }
        if (hasAny(m, "coupon", "offer", "discount", "promo", "deal", "cashback")) {
            return coupons();
        }
        if (hasAny(m, "track", "live location", "where is my", "gps", "share trip", "share my trip")) {
            return tracking(customer);
        }
        if (hasAny(m, "driver", "chauffeur", "with driver")) {
            return driverInfo();
        }
        if (hasAny(m, "location", "branch", "pickup point", "pick up point", "where can i pick", "airport", "address", "cities", "city do you")) {
            return locations();
        }
        if (hasAny(m, "km limit", "kilometer", "kilometre", "fuel policy", "late return", "extra km", "fuel")) {
            return policies();
        }
        if (hasAny(m, "how to book", "how do i book", "booking process", "steps to book", "book cheyali", "ela book")) {
            return howToBook();
        }
        if (isRecommendation(m)) {
            return recommend(m, fleet);
        }
        if (!mentioned.isEmpty()) {
            return vehicleInfo(mentioned.get(0));
        }
        if (hasAny(m, "hi", "hello", "hey", "namaste", "namaskaram", "నమస్కారం", "good morning", "good evening", "hai")) {
            return greet(customer, original);
        }
        if (hasAny(m, "thank", "thanks", "dhanyavad", "ధన్యవాదాలు")) {
            return reply("You're welcome! Happy and safe journey with **DriveEase**. Anything else I can help with?",
                    List.of(), List.of("Suggest a car", "Show offers", "My bookings"));
        }
        return help(customer);
    }

    // ================================================================ intents

    private ChatResponse greet(Customer c, String original) {
        boolean telugu = original.codePoints().anyMatch(cp -> cp >= 0x0C00 && cp <= 0x0C7F);
        String name = c == null ? "" : " " + c.getFullName().split("\\s+")[0];
        String hello = telugu ? "నమస్కారం" + name + "! 🙏" : "Hello" + name + "! 👋";
        return reply(hello + " I'm **DriveEase AI**, your rental assistant.\n\nI can:\n"
                        + "- Suggest the best car or bike for your trip and budget\n"
                        + "- Calculate exact prices with insurance and GST\n"
                        + "- Explain insurance, payments, cancellation and documents\n"
                        + "- Check your booking status and help in emergencies",
                List.of(), List.of("Suggest a family car under 4000", "Bike for Araku trip", "Price of Creta for 3 days", "Show offers"));
    }

    private ChatResponse help(Customer c) {
        return reply("I can help you with:\n"
                        + "- **Vehicle suggestions** - try \"SUV for 6 people under 5000\" or \"electric scooter for office\"\n"
                        + "- **Price quotes** - \"price of Thar for 2 days\"\n"
                        + "- **Booking status** - type your booking ID like DE1A2B3C4\n"
                        + "- **Insurance, payment, cancellation, documents, coupons**\n"
                        + "- **Emergency help** - type SOS or breakdown",
                List.of(), c == null
                        ? List.of("Suggest a car", "Suggest a bike", "Insurance plans", "Required documents")
                        : List.of("My bookings", "Suggest a car", "Insurance plans", "Show offers"));
    }

    private ChatResponse emergency() {
        return reply("🚨 **Stay calm, help is on the way.**\n\n"
                        + "1. If anyone is hurt or in danger, call **112** (emergency) or **108** (ambulance) now.\n"
                        + "2. Open **My Bookings -> Track** and press the red **SOS** button - our response team gets your live GPS location instantly.\n"
                        + "3. Breakdown? Turn on hazard lights, move to a safe spot, and press **Breakdown** on the tracking page. Roadside assistance arrives in 30-45 minutes.\n"
                        + "4. 24x7 DriveEase helpline: **1800-123-4567**\n\n"
                        + "Your driver and customer details are shared with our response team automatically.",
                List.of(), List.of("My bookings", "Insurance plans"));
    }

    private ChatResponse bookingStatus(String ref, Customer c) {
        Optional<Booking> ob = bookingRepository.findByBookingRef(ref);
        if (ob.isEmpty()) {
            return reply("I couldn't find a booking with ID **" + ref + "**. Please check the ID in your confirmation.",
                    List.of(), List.of("My bookings"));
        }
        Booking b = ob.get();
        if (c == null || !b.getCustomer().getId().equals(c.getId())) {
            return reply("For your privacy, please **login** with the account that made booking **" + ref + "** to see its details.",
                    List.of(), List.of("How to book"));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**Booking ").append(b.getBookingRef()).append("** - ").append(pretty(b.getStatus().name())).append("\n\n");
        sb.append("- Vehicle: ").append(b.getVehicle().getName()).append(" (").append(b.getVehicle().getRegistrationNumber()).append(")\n");
        sb.append("- Pickup: ").append(b.getPickupLocation()).append(", ").append(b.getStartTime().format(DT)).append("\n");
        sb.append("- Return: ").append(b.getDropLocation()).append(", ").append(b.getEndTime().format(DT)).append("\n");
        sb.append("- Total: Rs.").append(money(b.getTotalAmount())).append(" (payment ").append(pretty(b.getPaymentStatus().name())).append(")\n");
        if (b.getDriver() != null) {
            sb.append("- Driver: ").append(b.getDriver().getName()).append(", ").append(b.getDriver().getPhone()).append("\n");
        }
        if (b.getStatus() == BookingStatus.CONFIRMED) {
            sb.append("\nShare trip OTP **").append(b.getTripOtp()).append("** at pickup to start your trip.");
        } else if (b.getStatus() == BookingStatus.ONGOING) {
            sb.append("\nYour trip is live. Open **Track** in My Bookings to see the vehicle on the map.");
        } else if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
            sb.append("\nPayment is pending - complete it within 30 minutes to keep this vehicle reserved.");
        }
        return reply(sb.toString(), List.of(b.getVehicle()), List.of("My bookings", "Cancellation policy"));
    }

    private ChatResponse myBookings(Customer c) {
        if (c == null) {
            return reply("Please **login** to see your bookings. You can also type your booking ID (e.g. DE1A2B3C4).",
                    List.of(), List.of("How to book"));
        }
        List<Booking> list = bookingRepository.findByCustomerIdOrderByCreatedAtDesc(c.getId());
        if (list.isEmpty()) {
            return reply("You don't have any bookings yet. Shall I suggest a vehicle?",
                    List.of(), List.of("Suggest a car", "Suggest a bike"));
        }
        StringBuilder sb = new StringBuilder("Here are your latest bookings:\n");
        list.stream().limit(4).forEach(b -> sb.append("- **").append(b.getBookingRef()).append("** ")
                .append(b.getVehicle().getName()).append(" - ").append(pretty(b.getStatus().name()))
                .append(", ").append(b.getStartTime().format(DT)).append("\n"));
        sb.append("\nType a booking ID for full details.");
        return reply(sb.toString(), List.of(), List.of(list.get(0).getBookingRef(), "Cancellation policy"));
    }

    private ChatResponse priceQuote(Vehicle v, String m) {
        int days = 1;
        long hours = 0;
        Matcher dm = DAYS.matcher(m);
        Matcher hm = HOURS.matcher(m);
        if (dm.find()) {
            days = Math.max(1, Math.min(90, Integer.parseInt(dm.group(1))));
        } else if (hm.find()) {
            hours = Math.max(Catalog.MIN_RENTAL_HOURS, Math.min(23, Long.parseLong(hm.group(1))));
        } else if (m.contains("week")) {
            days = 7;
        } else if (m.contains("weekend")) {
            days = 2;
        } else if (m.contains("month")) {
            days = 30;
        }
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(LocalTime.of(9, 0));
        LocalDateTime end = hours > 0 ? start.plusHours(hours) : start.plusDays(days);
        Quote q = pricingService.quote(v, new QuoteRequest(v.getId(), start, end, "STANDARD", false,
                List.of(), null, null, null));
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(v.getName()).append("** for ")
                .append(hours > 0 ? hours + " hours" : days + (days == 1 ? " day" : " days"))
                .append(" (starting tomorrow 9 AM, Standard insurance):\n\n");
        for (QuoteLine l : q.lines()) {
            sb.append("- ").append(l.label()).append(": Rs.").append(money(l.amount())).append("\n");
        }
        sb.append("\n**Total: Rs.").append(money(q.totalAmount())).append("**");
        sb.append("\nRefundable security deposit: Rs.").append(money(q.depositAmount()));
        sb.append("\nFree km: ").append(q.kmLimit()).append(" km, then Rs.").append(money(v.getExtraKmCharge())).append("/km");
        String tip = "Electric".equalsIgnoreCase(v.getFuelType()) ? "\n\n💡 Use coupon **EV25** to save up to Rs.1,500."
                : v.getType() == VehicleType.BIKE ? "\n\n💡 Use coupon **BIKE15** to save 15%."
                : "\n\n💡 First booking? Use **FIRST20** for 20% off.";
        sb.append(tip);
        return reply(sb.toString(), List.of(v), List.of("Insurance plans", "Show offers", "Required documents"));
    }

    private ChatResponse insurance() {
        StringBuilder sb = new StringBuilder("We offer 3 protection plans (price per day, car / bike):\n\n");
        for (InsurancePlan p : InsurancePlan.values()) {
            sb.append("- **").append(p.getLabel()).append("** - Rs.").append(money(p.getCarPerDay())).append(" / Rs.")
                    .append(money(p.getBikePerDay())).append(". ").append(p.getDescription())
                    .append(" Damage liability up to Rs.").append(money(p.getDeductible())).append(" (car).\n");
        }
        sb.append("\nI recommend **Premium Zero-Dep** for long trips and highways - it also halves your security deposit.");
        return reply(sb.toString(), List.of(), List.of("Cancellation policy", "Payment options"));
    }

    private ChatResponse cancellation() {
        return reply("**Cancellation & refund policy**\n\n"
                        + "- 24+ hours before pickup: **100% refund**\n"
                        + "- 4 to 24 hours before pickup: **50% refund**\n"
                        + "- Less than 4 hours: no refund\n"
                        + "- Unpaid bookings are released automatically after 30 minutes\n\n"
                        + "Refunds go back to the original payment method in 3-5 working days. Cancel anytime from **My Bookings**.",
                List.of(), List.of("My bookings", "Payment options"));
    }

    private ChatResponse payment() {
        return reply("**Payment options**\n\n"
                        + "- **UPI** - GPay, PhonePe, Paytm, BHIM (any UPI ID)\n"
                        + "- **Cards** - Visa, Mastercard, RuPay, Amex (3-D Secure)\n"
                        + "- **Net banking** - SBI, HDFC, ICICI, Axis and more\n"
                        + "- **Wallets** - PhonePe, Paytm, Amazon Pay, MobiKwik\n"
                        + "- **Pay at pickup** - cash / card / UPI at the counter\n\n"
                        + "You can **save** a card or UPI ID for 1-click checkout. We store only a secure token and the last 4 digits - never your full card number or CVV.\n"
                        + "The security deposit is fully refundable after the trip.",
                List.of(), List.of("Cancellation policy", "Show offers"));
    }

    private ChatResponse documents() {
        return reply("**Documents required**\n\n"
                        + "- Valid **driving licence** (LMV for cars, MCWG for bikes) - at least 1 year old\n"
                        + "- Government ID: **Aadhaar** / Passport / Voter ID\n"
                        + "- Minimum age: 21 for cars, 18 for bikes\n"
                        + "- International customers: passport + International Driving Permit\n\n"
                        + "Booking **with a driver**? Only an ID proof is needed.",
                List.of(), List.of("How to book", "Insurance plans"));
    }

    private ChatResponse coupons() {
        StringBuilder sb = new StringBuilder("🎉 **Current offers**\n\n");
        for (Catalog.Coupon c : Catalog.COUPONS) {
            sb.append("- **").append(c.code()).append("** - ").append(c.description()).append("\n");
        }
        sb.append("\nPlus: earn 1 loyalty point for every Rs.100 you spend.");
        return reply(sb.toString(), List.of(), List.of("Suggest an EV", "Suggest a bike"));
    }

    private ChatResponse tracking(Customer c) {
        return reply("**Live trip tracking**\n\n"
                        + "Once your trip starts (OTP verified at pickup), open **My Bookings -> Track** to see:\n"
                        + "- Live vehicle position on the map with route, speed and ETA\n"
                        + "- Distance travelled vs. free km limit\n"
                        + "- Driver details with one-tap call\n"
                        + "- Over-speed alerts and the red **SOS** button\n"
                        + "- **Share trip** link so family can follow you live",
                List.of(), c == null ? List.of("How to book") : List.of("My bookings"));
    }

    private ChatResponse driverInfo() {
        return reply("**Need a driver?** Tick *Add professional driver* while booking any car.\n\n"
                        + "- Rs." + money(Catalog.DRIVER_PER_DAY) + " per day (up to 12 hours driving)\n"
                        + "- Verified, police-checked drivers with 5+ years experience\n"
                        + "- Speak Telugu, Hindi and English\n"
                        + "- Driver name, photo, rating and phone number are shown in your booking and on the tracking page",
                List.of(), List.of("Suggest a family car", "Price of Innova for 2 days"));
    }

    private ChatResponse locations() {
        StringBuilder sb = new StringBuilder("📍 **Pickup & drop locations**\n\n");
        Catalog.LOCATIONS.forEach(l -> sb.append("- ").append(l.name()).append(" - ").append(l.city()).append("\n"));
        sb.append("\nOne-way trips (different drop location) have a small fee. Doorstep delivery is available as an add-on.");
        return reply(sb.toString(), List.of(), List.of("Suggest a car", "Show offers"));
    }

    private ChatResponse policies() {
        return reply("**Trip policies**\n\n"
                        + "- **Free km**: cars 250-300 km/day, bikes 150-200 km/day (shown on every vehicle). Extra km charged per km.\n"
                        + "- **Fuel**: same-to-same - return with the same fuel / charge level.\n"
                        + "- **Late return**: 1 hour grace, then 1.5x the hourly rate.\n"
                        + "- **Speed**: alerts above 90 km/h (cars) / 70 km/h (bikes).\n"
                        + "- **Interstate**: allowed; state permit / tolls are paid by the customer.",
                List.of(), List.of("Insurance plans", "Cancellation policy"));
    }

    private ChatResponse howToBook() {
        return reply("**Booking takes 2 minutes:**\n\n"
                        + "1. Pick a car or bike from **Vehicles** (use filters or ask me!)\n"
                        + "2. Choose pickup/return time, location, insurance and add-ons - price updates live\n"
                        + "3. Enter your licence and emergency contact\n"
                        + "4. Pay by UPI / card / net banking / wallet or at pickup\n"
                        + "5. Show the **trip OTP** at pickup, drive, and track your trip live",
                List.of(), List.of("Suggest a car", "Required documents"));
    }

    private ChatResponse vehicleInfo(Vehicle v) {
        String sb = "**" + v.getName() + "** (" + v.getCategory() + ", " + v.getModelYear() + ")\n\n"
                + "- " + v.getFuelType() + " | " + v.getTransmission() + " | " + v.getSeats() + " seats\n"
                + "- Engine: " + v.getEngine() + " | Mileage: " + v.getMileage() + "\n"
                + "- Rs." + money(v.getPricePerDay()) + "/day or Rs." + money(v.getPricePerHour()) + "/hour\n"
                + "- Free " + v.getKmLimitPerDay() + " km/day | Deposit Rs." + money(v.getDeposit()) + "\n"
                + "- Rated " + v.getRating() + " ⭐ by " + v.getTripsCompleted() + " trips\n\n"
                + v.getDescription();
        return reply(sb, List.of(v), List.of("Price of " + shortName(v) + " for 3 days", "Insurance plans"));
    }

    // ================================================================ recommendation engine

    private boolean isRecommendation(String m) {
        return hasAny(m, "suggest", "recommend", "best", "which", "need", "want", "looking for", "kavali", "కావాలి",
                "car", "cars", "bike", "bikes", "scooter", "scooty", "suv", "sedan", "family", "trip", "budget", "cheap", "ev",
                "electric", "seater", "people", "luxury", "wedding", "ride", "travel", "కారు", "కార్", "బైక్", "bandi")
                || PEOPLE.matcher(m).find() || BUDGET.matcher(m).find();
    }

    private ChatResponse recommend(String m, List<Vehicle> fleet) {
        List<String> reasons = new ArrayList<>();
        VehicleType type = null;
        if (hasAny(m, "bike", "bikes", "scooter", "scooters", "scooty", "two wheeler", "2 wheeler", "two-wheeler",
                "motorcycle", "bandi", "బైక్", "solo ride")) {
            type = VehicleType.BIKE;
        } else if (hasAny(m, "car", "cars", "suv", "suvs", "sedan", "hatchback", "muv", "family", "కారు", "కార్", "seater", "wedding")) {
            type = VehicleType.CAR;
        }
        Integer people = null;
        Matcher pm = PEOPLE.matcher(m);
        if (pm.find()) {
            people = Integer.parseInt(pm.group(1));
            if (people > 2) {
                type = VehicleType.CAR;
            }
        }
        Double budget = null;
        Matcher bm = BUDGET.matcher(m);
        if (bm.find()) {
            String num = bm.group(1) != null ? bm.group(1) : bm.group(2) != null ? bm.group(2) : bm.group(3);
            budget = Double.parseDouble(num.replace(",", ""));
        }
        String fuel = hasAny(m, "electric", " ev", "ev ", "battery", "green") ? "Electric"
                : m.contains("diesel") ? "Diesel" : m.contains("petrol") ? "Petrol" : null;
        String transmission = hasAny(m, "automatic", " auto ", "gearless", "no gear") ? "Automatic"
                : m.contains("manual") ? "Manual" : null;

        Set<String> wanted = new HashSet<>();
        if (hasAny(m, "suv")) wanted.add("SUV");
        if (hasAny(m, "sedan")) wanted.add("Sedan");
        if (hasAny(m, "hatchback", "small car", "compact")) wanted.add("Hatchback");
        if (hasAny(m, "luxury", "premium", "wedding", "vip", "business", "bmw")) wanted.add("Luxury");
        if (hasAny(m, "off road", "offroad", "off-road", "adventure", "hills", "hill", "ladakh", "araku", "trek", "mountain", "forest", "jungle")) {
            wanted.add("Off-Road");
            wanted.add("Adventure");
            wanted.add("SUV");
        }
        if (hasAny(m, "sport", "speed", "racing", "fast", "track day")) wanted.add("Sports");
        if (hasAny(m, "cruise", "cruiser", "highway", "long ride", "road trip", "bullet")) {
            wanted.add("Cruiser");
            wanted.add("Adventure");
        }
        if (hasAny(m, "city", "office", "commute", "daily", "college", "short", "local", "mileage")) {
            wanted.add("Scooter");
            wanted.add("Commuter");
            wanted.add("Hatchback");
            wanted.add("EV");
        }
        if (hasAny(m, "family", "kids", "group", "tirupati", "pilgrim", "luggage", "7 seater", "parents")) {
            wanted.add("MUV");
            wanted.add("SUV");
            if (people == null) {
                people = 5;
            }
        }
        boolean cheap = hasAny(m, "cheap", "cheapest", "budget", "low cost", "economy", "affordable", "takkuva", "తక్కువ", "low price");

        final VehicleType fType = type;
        final Integer fPeople = people;
        final Double fBudget = budget;
        List<Vehicle> base = fleet.stream().filter(Vehicle::isAvailable)
                .filter(v -> fType == null || v.getType() == fType)
                .filter(v -> fPeople == null || v.getSeats() >= fPeople)
                .filter(v -> fuel == null || fuel.equalsIgnoreCase(v.getFuelType()))
                .filter(v -> transmission == null || transmission.equalsIgnoreCase(v.getTransmission()))
                .collect(Collectors.toList());

        List<Vehicle> withinBudget = fBudget == null ? base
                : base.stream().filter(v -> v.getPricePerDay() <= fBudget).collect(Collectors.toList());
        boolean relaxedBudget = false;
        if (withinBudget.isEmpty() && fBudget != null && !base.isEmpty()) {
            withinBudget = base;
            relaxedBudget = true;
        }
        if (withinBudget.isEmpty()) {
            withinBudget = fleet.stream().filter(Vehicle::isAvailable)
                    .filter(v -> fType == null || v.getType() == fType).collect(Collectors.toList());
            reasons.add("I couldn't match every filter, so here are the closest options.");
        }

        Comparator<Vehicle> order = Comparator.comparingDouble((Vehicle v) -> -score(v, wanted, fBudget));
        if (cheap) {
            order = Comparator.comparingDouble(Vehicle::getPricePerDay);
        }
        if (relaxedBudget) {
            order = Comparator.comparingDouble(Vehicle::getPricePerDay);
        }
        List<Vehicle> picks = withinBudget.stream().sorted(order).limit(3).collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        List<String> understood = new ArrayList<>();
        if (fType != null) understood.add(fType == VehicleType.CAR ? "car" : "bike / scooter");
        if (fPeople != null) understood.add(fPeople + "+ seats");
        if (fBudget != null) understood.add("budget Rs." + money(fBudget) + "/day");
        if (fuel != null) understood.add(fuel.toLowerCase());
        if (transmission != null) understood.add(transmission.toLowerCase());
        if (!wanted.isEmpty()) understood.add(String.join(" / ", new TreeSet<>(wanted)).toLowerCase() + " style");
        if (!understood.isEmpty()) {
            sb.append("Looking for: *").append(String.join(", ", understood)).append("*\n\n");
        }
        if (relaxedBudget) {
            sb.append("Nothing fits Rs.").append(money(fBudget)).append("/day exactly, so here are the most affordable matches:\n\n");
        } else {
            sb.append("Here are my top picks for you:\n\n");
        }
        int i = 1;
        for (Vehicle v : picks) {
            sb.append(i++).append(". **").append(v.getName()).append("** - Rs.").append(money(v.getPricePerDay()))
                    .append("/day | ").append(v.getSeats()).append(" seats | ").append(v.getFuelType())
                    .append(" ").append(v.getTransmission()).append(" | ⭐ ").append(v.getRating()).append("\n   ")
                    .append(why(v, wanted)).append("\n");
        }
        reasons.forEach(r -> sb.append("\n").append(r));
        if (picks.stream().anyMatch(v -> "Electric".equalsIgnoreCase(v.getFuelType()))) {
            sb.append("\n💡 EVs save fuel money - use **EV25** for 25% off.");
        } else if (picks.stream().anyMatch(v -> v.getType() == VehicleType.BIKE)) {
            sb.append("\n💡 Use **BIKE15** for 15% off on bikes.");
        }
        List<String> quick = new ArrayList<>();
        if (!picks.isEmpty()) {
            quick.add("Price of " + shortName(picks.get(0)) + " for 2 days");
        }
        quick.add(fType == VehicleType.BIKE ? "Suggest a car" : "Suggest a bike");
        quick.add("Insurance plans");
        return reply(sb.toString(), picks, quick);
    }

    private double score(Vehicle v, Set<String> wanted, Double budget) {
        double s = v.getRating() * 10 + Math.min(v.getTripsCompleted(), 300) / 30.0;
        if (wanted.contains(v.getCategory())) {
            s += 40;
        }
        if (budget != null) {
            s += 10 * (v.getPricePerDay() / budget); // best vehicle that still fits the budget
        }
        return s;
    }

    private String why(Vehicle v, Set<String> wanted) {
        String feats = v.getFeatures() == null ? "" : Arrays.stream(v.getFeatures().split(","))
                .map(String::trim).limit(3).collect(Collectors.joining(", "));
        String fit = wanted.contains(v.getCategory()) ? "Great " + v.getCategory().toLowerCase() + " pick. " : "";
        return fit + feats + ". " + v.getMileage() + ".";
    }

    // ================================================================ helpers

    /** Finds vehicles mentioned by name or model in the text (used for Claude replies too). */
    public List<Vehicle> matchVehicles(String text, List<Vehicle> fleet) {
        String m = normalize(text);
        Map<String, Long> brandCount = fleet.stream()
                .filter(v -> v.getBrand() != null)
                .collect(Collectors.groupingBy(v -> v.getBrand().toLowerCase(Locale.ROOT), Collectors.counting()));
        List<Vehicle> out = new ArrayList<>();
        for (Vehicle v : fleet) {
            List<String> aliases = aliases(v);
            String brand = v.getBrand() == null ? "" : v.getBrand().toLowerCase(Locale.ROOT);
            if (brandCount.getOrDefault(brand, 0L) == 1 && brand.length() >= 3) {
                aliases.add(brand); // e.g. "ather", "bmw", "ktm"
            }
            for (String alias : aliases) {
                if (Pattern.compile("(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])").matcher(m).find()) {
                    out.add(v);
                    break;
                }
            }
        }
        return out;
    }

    private static final Set<String> GENERIC = Set.of("city", "plus", "pro", "series", "classic", "ev", "the", "new");

    private static List<String> aliases(Vehicle v) {
        String name = v.getName().toLowerCase(Locale.ROOT);
        String brand = v.getBrand() == null ? "" : v.getBrand().toLowerCase(Locale.ROOT);
        List<String> list = new ArrayList<>();
        list.add(name);
        String model = name.startsWith(brand) ? name.substring(brand.length()).trim() : name;
        if (!model.isEmpty()) {
            list.add(model);
        }
        Set<String> brandWords = new HashSet<>(Arrays.asList(brand.split("\\s+")));
        for (String tok : model.split("\\s+")) {
            if (tok.length() >= 3 && !GENERIC.contains(tok) && !brandWords.contains(tok) && !tok.matches("[0-9]+")) {
                list.add(tok);
            }
        }
        return list;
    }

    private static String shortName(Vehicle v) {
        List<String> a = aliases(v);
        return a.size() > 2 ? capitalize(a.get(2)) : v.getName();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String normalize(String s) {
        return " " + s.toLowerCase(Locale.ROOT)
                .replaceAll("[?!;:()\"']", " ")
                .replaceAll(",(?!\\d)", " ")      // keep thousands separators like 4,000
                .replaceAll("\\.(?!\\d)", " ")
                .replaceAll("\\s+", " ").trim() + " ";
    }

    private static boolean hasAny(String m, String... words) {
        for (String w : words) {
            if (w.isBlank()) {
                continue;
            }
            String needle = w.trim();
            if (needle.length() <= 3 && needle.chars().allMatch(Character::isLetter) && needle.chars().allMatch(c -> c < 128)) {
                // short English words must match as whole words ("ev" should not match "every")
                if (m.contains(" " + needle + " ")) {
                    return true;
                }
            } else if (m.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    static String money(double v) {
        return String.format(Locale.ENGLISH, "%,.0f", v);
    }

    private static String pretty(String enumName) {
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static ChatResponse reply(String text, List<Vehicle> vehicles, List<String> quick) {
        return new ChatResponse(text, vehicles, quick, "local");
    }
}
