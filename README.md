# 🚗 DriveEase: Car & Bike Rental System (Java Spring Boot)

DriveEase is a full-stack rental platform. It covers the whole flow: choosing a car or bike, a live price breakdown with
insurance and GST, payments, an OTP-verified pickup, **live GPS trip tracking**, SOS and roadside help, an **AI assistant**,
and an admin console for the rental company.

> **తెలుగులో సంక్షిప్తంగా:** ఇది Java (Spring Boot) తో తయారు చేసిన పూర్తి కార్/బైక్ రెంటల్ వెబ్‌సైట్.
> 10 కార్లు + 10 బైకులు, బుకింగ్, ధర లెక్కింపు (ఇన్సూరెన్స్, GST, కూపన్లు), పేమెంట్ (UPI / కార్డ్ / నెట్ బ్యాంకింగ్ / వాలెట్ / పికప్ వద్ద),
> కార్డ్/UPI సేవ్ చేసుకోవడం, ట్రిప్ OTP, లైవ్ GPS ట్రాకింగ్ మ్యాప్, SOS / ప్రమాదం / బ్రేక్‌డౌన్ అలర్ట్స్, డ్రైవర్ & కస్టమర్ వివరాలు (మొబైల్, ఎమర్జెన్సీ కాంటాక్ట్),
> AI అసిస్టెంట్ (తెలుగు పదాలు కూడా అర్థం చేసుకుంటుంది), మరియు అడ్మిన్ డ్యాష్‌బోర్డ్ ఉన్నాయి.

---

## ▶️ How to run (ఎలా రన్ చేయాలి)

**You need:** Java **17 or newer** (JDK 17 / 21 / 25). You do not need to install a database, because H2 is embedded.

### Option 0: No Maven installed? Use the run script
Double-click `run.cmd`, or type `.\run.cmd` in a terminal opened in this folder. On the first run it downloads Maven into
`.mvn\maven` and then starts the app. Use `.\run.cmd test` to run the tests.

### Option A: IntelliJ IDEA (easiest)
1. `File → Open…` and select this folder (`carrental system`). IntelliJ detects `pom.xml` and downloads the dependencies.
2. Open `src/main/java/com/driveease/DriveEaseApplication.java` and click the green ▶ **Run** button.
3. Open **http://localhost:8080** in your browser.

### Option B: Command line (needs Maven installed)
```powershell
mvn spring-boot:run
```

To run the unit tests:
```powershell
mvn test
```

### Demo logins
| Role | Login | Password |
|---|---|---|
| Customer | `demo@driveease.in` | `demo123` |
| Admin console (`/admin.html`) | `admin` | `admin123` |

The first start seeds 10 cars, 10 bikes, 8 drivers, 3 customers and some sample bookings. One of the demo customer's trips
is already **live**, so the tracking map moves straight away. The database is saved in `./data/`. Delete that folder to reset everything.

### Test payment values (simulated gateway, no real money)
| Method | Success | Failure |
|---|---|---|
| UPI | `yourname@okhdfc` | `fail@upi` |
| Card | `4111 1111 1111 1111`, any future expiry, any CVV | `4000 0000 0000 0002` (declined) |
| Net banking / Wallet / Pay at pickup | always succeed | — |

---

## ✨ Features

### Customers
- **Fleet of 20 vehicles** (10 cars: Swift, i20, City, Creta, XUV700, Innova Crysta, Nexon EV, Thar, Fortuner, BMW 3 Series;
  10 bikes: Activa, Jupiter, Ather 450X, Ola S1 Pro, Classic 350, Himalayan 450, Pulsar NS200, Duke 390, R15 V4, Splendor+).
- Filters for type, category, fuel, transmission, seats and price, plus search, sorting and an **availability check for your dates**.
- **Transparent pricing engine:** hourly or daily rates (hourly is capped at the day rate), a 10% weekend surcharge, long-rental discounts
  (5% / 10% / 20%), insurance, driver, add-ons, a one-way fee, coupons and 18% GST. You see the security deposit and the free km before you pay.
- **3 insurance plans:** Basic (free), Standard and Premium Zero-Dep. Each shows the maximum damage liability, and Premium halves the deposit.
- **Optional professional driver** for cars. The system assigns a free driver automatically and never double-books one.
- **Add-ons:** GPS, child seat, Wi-Fi, FASTag, pillion helmet, riding gear, doorstep delivery.
- **Coupons:** `FIRST20`, `WEEKEND10`, `BIKE15`, `EV25`, `LONGDRIVE`, each with its own validation rules.
- **4-step checkout:** trip review, traveller KYC (licence and emergency contact), payment, then confirmation.
- **Payments:** UPI, cards (Luhn check, expiry, CVV, brand detection), net banking, wallets, or pay at pickup.
- **Saved payment methods:** stored as a gateway token plus masked details. The full card number and the CVV are **never** stored.
- **Trip OTP:** the 4-digit OTP is verified at pickup before the trip can start.
- **Live tracking page:** a Leaflet + OpenStreetMap map with the planned route, the route driven so far, live speed, ETA, distance, and free-km usage.
- **Over-speed alerts:** raised automatically above 90 km/h for cars and 70 km/h for bikes.
- **SOS, accident, breakdown, medical and support** buttons. Each one sends the live location to the admin team and shows the emergency numbers (112 / 108 / 100).
- **Share live trip** link for family. It shows the map and hides personal details.
- **Driver & customer details:** name, rating, mobile with one-tap call, and emergency contact.
- **My Bookings:** cancel with the refund policy (100% / 50% / 0%), start and end the trip, extra-km and late-fee settlement,
  ratings and reviews, loyalty points, printable **tax invoice**, and saved payments and profile pages.
- Unpaid reservations expire automatically after 30 minutes so the vehicle is released.

### 🤖 AI assistant (chat bubble on every page)
- **Offline smart engine (free, default):** detects what you are asking for and pulls out details such as budget, number of people,
  days, fuel and vehicle type. It recommends vehicles from the live fleet and calculates exact quotes. It also checks booking status
  (only for the booking's owner), explains policies, and helps in emergencies. It understands English, Tenglish and common Telugu words
  (కారు, బైక్, ఎంత, కావాలి, రోజులు …).
- **Claude AI mode (optional):** set the environment variable `ANTHROPIC_API_KEY` and restart. The assistant then uses Claude
  (`claude-opus-5-5`) with the live fleet, prices and policies in its prompt, and it replies in any language, including full Telugu.
  If Claude cannot be reached or declines a request, the offline engine answers instead.

```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."
mvn spring-boot:run
```

### 🛠 Admin console (`/admin.html`)
- **Overview:** KPIs (net revenue, bookings, live trips, fleet utilisation, open alerts) and charts (7-day revenue, status mix, cars vs bikes, insurance mix).
- **Bookings:** search all bookings, change their status, and assign drivers. The table also shows customer mobile and emergency contact.
- **Live fleet map:** every vehicle on the road, refreshed every 3 seconds.
- **Alerts:** SOS, incidents, over-speed and support requests, each with location links. You can acknowledge and resolve them with a note.
- **Fleet:** edit prices, mark vehicles for maintenance, and add new vehicles.
- **Drivers, customers and the payments ledger** (charges and refunds).

---

## 🏗 Tech stack & architecture

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.5 (Web, Data JPA, Validation, Scheduling) |
| Database | H2 file database (swap for MySQL/PostgreSQL by changing `application.properties`) |
| Security | PBKDF2-SHA256 password hashing (JDK only), token sessions, separate admin token |
| AI | Built-in NLP engine + optional Anthropic Java SDK (Claude) |
| Frontend | HTML5, CSS3 (responsive), vanilla JavaScript, Leaflet maps, Chart.js |

```
src/main/java/com/driveease
├── DriveEaseApplication.java      ← start here
├── model/        Vehicle, Driver, Customer, Booking, Payment, SavedPaymentMethod, TrackingPoint, Alert + enums
├── repository/   Spring Data JPA repositories (incl. overlap queries that prevent double booking)
├── service/      PricingService, BookingService, PaymentGatewayService, TrackingService (GPS simulator),
│                 AlertService (SOS), AuthService, Catalog (locations, add-ons, coupons)
├── ai/           AiAssistantService, LocalAiEngine (offline), ClaudeAiClient (optional)
├── web/          REST controllers + global error handler
├── dto/          request / response records
└── config/       DataSeeder (demo data)
src/main/resources/static          ← the website (index, vehicles, vehicle, checkout, dashboard, track, invoice, admin)
```

**Booking life cycle:** `PENDING_PAYMENT → CONFIRMED → ONGOING → COMPLETED`, or `CANCELLED` at any point before the trip starts.

**GPS simulation:** a real fleet would get GPS pings from a telematics device in each vehicle. Here `TrackingService` plays that
role. Every 3 seconds it moves each ongoing trip along a curved route, records the speed, and raises over-speed alerts. The tracker
writes only the GPS columns, through a guarded update, so it can never overwrite a trip that a user has just ended.

### Main REST endpoints
| Method | URL | Purpose |
|---|---|---|
| GET | `/api/vehicles?type=&category=&fuel=&seats=&maxPrice=&start=&end=` | Fleet with filters and availability |
| POST | `/api/pricing/quote` | Live price breakdown |
| POST | `/api/auth/register` · `/api/auth/login` | Account |
| POST | `/api/bookings` | Create a booking (reserves the vehicle) |
| POST | `/api/bookings/{ref}/pay` · `/cancel` · `/start` · `/end` · `/review` | Booking actions |
| POST | `/api/bookings/{ref}/alerts` | SOS / accident / breakdown / medical / support |
| GET | `/api/tracking/{ref}` (`?share=code` for family) | Live tracking data |
| GET/POST/DELETE | `/api/payment-methods` | Saved cards & UPI |
| POST | `/api/ai/chat` | AI assistant |
| * | `/api/admin/**` (header `X-Admin-Token`) | Admin console |

H2 database console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/driveease`, user `sa`, no password).

---

## 🚀 Ideas to extend it further
- Real payments with the **Razorpay** test mode (replace `PaymentGatewayService.charge`).
- **Spring Security + JWT**, with roles for customer, admin and driver, plus a driver mobile app.
- SMS / WhatsApp notifications (OTP, booking confirmation, SOS to the emergency contact).
- Real GPS devices posting to a `/api/telematics` endpoint, plus geofencing.
- Document upload (licence photo) with OCR verification, and photo-based damage inspection at pickup and return.
- Dynamic pricing based on demand, and a recommendation model trained on past bookings.

---

*Demo project: payments, GPS and SMS are simulated. Phone numbers, licence numbers and the GSTIN are sample data.*
