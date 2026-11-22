DE.renderLayout("");

let draft = null;      // trip configuration from the vehicle page
let quote = null;      // live quote
let booking = null;    // created booking
let vehicle = null;
let me = null;
let meta = null;
let payMethod = "UPI";
let savedMethods = [];
let selectedSaved = null;

function setStep(n) {
  document.querySelectorAll("#stepper .s").forEach((s) => {
    const k = Number(s.dataset.step);
    s.classList.toggle("active", k === n);
    s.classList.toggle("done", k < n);
  });
  window.scrollTo({ top: 0, behavior: "smooth" });
}

(async function init() {
  if (!DE.requireLogin()) return;
  try {
    [me, meta] = await Promise.all([DE.api.get("/api/auth/me"), DE.api.get("/api/meta")]);
  } catch (e) {
    DE.toast(e.message, "error");
    if (e.status === 401) { DE.clearSession(); DE.requireLogin(); }
    return;
  }
  const ref = DE.qs("ref");
  if (ref) {
    // resume payment of an existing booking
    const r = await DE.api.get("/api/bookings/" + ref);
    booking = r.booking;
    vehicle = booking.vehicle;
    if (booking.status !== "PENDING_PAYMENT") { showConfirmation(); return; }
    renderSummaryFromBooking();
    showPayment();
    return;
  }
  try { draft = JSON.parse(DE.session.get("de_checkout")); } catch (e) { draft = null; }
  if (!draft) {
    document.getElementById("main").innerHTML = `<div class="card empty"><div class="big">🛒</div><h3>Nothing to checkout</h3><p>Choose a vehicle first.</p><a class="btn" href="/vehicles.html">Browse vehicles</a></div>`;
    document.getElementById("summary").innerHTML = "";
    return;
  }
  vehicle = await DE.api.get("/api/vehicles/" + draft.vehicleId);
  try {
    quote = await DE.api.post("/api/pricing/quote", draft);
  } catch (e) {
    document.getElementById("main").innerHTML = `<div class="card"><div class="notice err">${DE.esc(e.message)}</div><a class="btn mt" href="/vehicle.html?id=${draft.vehicleId}">Change trip</a></div>`;
    return;
  }
  renderSummaryFromQuote();
  showTrip();
})();

// ------------------------------------------------------------------ summary panel
function summaryHead() {
  return `<div class="row" style="gap:14px;align-items:center">
      <div style="width:110px;background:#f1f5f9;border-radius:12px;padding:6px">${DE.vehicleArt(vehicle)}</div>
      <div><b>${DE.esc(vehicle.name)}</b><div class="muted small">${DE.esc(vehicle.category)} · ${DE.esc(vehicle.fuelType)} · ${DE.esc(vehicle.transmission)}</div></div>
    </div>`;
}
function renderSummaryFromQuote() {
  document.getElementById("summary").innerHTML = `${summaryHead()}
    <div class="kv mt">
      <div><span>Pickup</span>${DE.esc(draft.pickupLocation)}<br>${DE.fmtDate(draft.startTime)}</div>
      <div><span>Return</span>${DE.esc(draft.dropLocation)}<br>${DE.fmtDate(draft.endTime)}</div>
    </div>
    <table class="breakdown mt">${quote.lines.map((l) => `<tr class="${l.amount < 0 ? "neg" : ""}"><td>${DE.esc(l.label)}</td><td>${DE.money2(l.amount)}</td></tr>`).join("")}</table>
    <div class="total-row"><span class="muted">Total</span><b>${DE.money(quote.totalAmount)}</b></div>
    <div class="small muted">Refundable deposit ${DE.money(quote.depositAmount)} collected at pickup</div>`;
}
function renderSummaryFromBooking() {
  const b = booking;
  const rows = [["Rental charge", b.baseAmount], ["Weekend surcharge", b.weekendSurcharge], ["Long rental discount", -b.longRentalDiscount],
    ["Insurance (" + DE.pretty(b.insurancePlan) + ")", b.insuranceAmount], ["Driver", b.driverAmount], ["Add-ons", b.addonsAmount],
    ["One-way fee", b.oneWayFee], ["Coupon " + (b.couponCode || ""), -b.couponDiscount], ["GST (18%)", b.taxAmount]].filter((r) => r[1]);
  document.getElementById("summary").innerHTML = `${summaryHead()}
    <div class="mt"><span class="badge">${DE.esc(b.bookingRef)}</span> ${DE.statusBadge(b.status)}</div>
    <div class="kv mt">
      <div><span>Pickup</span>${DE.esc(b.pickupLocation)}<br>${DE.fmtDate(b.startTime)}</div>
      <div><span>Return</span>${DE.esc(b.dropLocation)}<br>${DE.fmtDate(b.endTime)}</div>
    </div>
    <table class="breakdown mt">${rows.map((r) => `<tr class="${r[1] < 0 ? "neg" : ""}"><td>${DE.esc(r[0])}</td><td>${DE.money2(r[1])}</td></tr>`).join("")}</table>
    <div class="total-row"><span class="muted">Total</span><b>${DE.money(b.totalAmount)}</b></div>
    <div class="small muted">Refundable deposit ${DE.money(b.depositAmount)} · ${b.kmLimit} km free</div>`;
}

// ------------------------------------------------------------------ step 1: trip
function showTrip() {
  setStep(1);
  const plan = meta.insurancePlans.find((p) => p.code === quote.insurancePlan);
  const addons = meta.addons.filter((a) => quote.addons.includes(a.code));
  document.getElementById("main").innerHTML = `
    <div class="card">
      <h2>Review your trip</h2>
      <div class="kv mt">
        <div><span>Vehicle</span>${DE.esc(vehicle.name)} (${DE.esc(vehicle.registrationNumber || "")})</div>
        <div><span>Duration</span>${quote.totalHours} hours · billed ${quote.billableDays} day(s)</div>
        <div><span>Pickup</span>${DE.esc(draft.pickupLocation)}</div>
        <div><span>Drop</span>${DE.esc(draft.dropLocation)}</div>
        <div><span>Insurance</span>${DE.esc(plan ? plan.label : quote.insurancePlan)}</div>
        <div><span>Max damage liability</span>${DE.money(quote.deductible)}</div>
        <div><span>Driver</span>${quote.withDriver ? "Professional driver included" : "Self-drive"}</div>
        <div><span>Add-ons</span>${addons.length ? addons.map((a) => DE.esc(a.name)).join(", ") : "None"}</div>
        <div><span>Free km</span>${quote.kmLimit} km (then ${DE.money(quote.extraKmRate)}/km)</div>
        <div><span>Coupon</span>${quote.couponApplied ? DE.esc(draft.couponCode) : "None"}</div>
      </div>
      ${quote.couponMessage && !quote.couponApplied ? `<div class="notice warn mt">${DE.esc(quote.couponMessage)}</div>` : ""}
      <div class="notice mt">📋 Bring your original driving licence and an ID proof (Aadhaar/Passport) at pickup.</div>
      <div class="row mt2">
        <a class="btn ghost" href="/vehicle.html?id=${vehicle.id}">← Change trip</a>
        <button class="btn grow" id="to-details">Continue to traveller details →</button>
      </div>
    </div>`;
  document.getElementById("to-details").onclick = showDetails;
}

// ------------------------------------------------------------------ step 2: traveller
function showDetails() {
  setStep(2);
  const v = (x) => DE.esc(x || "");
  document.getElementById("main").innerHTML = `
    <form class="card" id="details-form">
      <h2>Traveller details</h2>
      <p class="muted small">Saved securely to your profile - you won't need to enter them again.</p>
      <div class="grid-2">
        <div class="field"><label>Full name (as on licence)</label><input id="d-name" required value="${v(me.fullName)}"></div>
        <div class="field"><label>Mobile number</label><input id="d-phone" required value="${v(me.phone)}"></div>
        <div class="field"><label>Date of birth</label><input type="date" id="d-dob" value="${v(me.dateOfBirth)}"></div>
        <div class="field"><label>Email</label><input value="${v(me.email)}" disabled></div>
      </div>
      <h3 class="mt">Driving licence ${quote.withDriver ? '<span class="badge gray">optional with driver</span>' : ""}</h3>
      <div class="grid-2">
        <div class="field"><label>Licence number</label><input id="d-lic" placeholder="TS0920190001234" value="${v(me.licenseNumber)}" ${quote.withDriver ? "" : "required"} style="text-transform:uppercase"></div>
        <div class="field"><label>Valid till</label><input type="date" id="d-licexp" value="${v(me.licenseExpiry)}"></div>
      </div>
      <div class="field"><label>Address</label><input id="d-address" value="${v(me.address)}" placeholder="House no, street, city"></div>
      <h3 class="mt">Emergency contact 🚨</h3>
      <p class="muted small">Notified with your live location if you press SOS during the trip.</p>
      <div class="grid-2">
        <div class="field"><label>Contact name</label><input id="d-ename" required value="${v(me.emergencyContactName)}"></div>
        <div class="field"><label>Contact mobile</label><input id="d-ephone" required value="${v(me.emergencyContactPhone)}"></div>
      </div>
      <div class="field"><label>Special requests (optional)</label><textarea id="d-notes" placeholder="e.g. need the car at 6 AM sharp, child seat facing rear…"></textarea></div>
      <label class="check"><input type="checkbox" required> <span>I agree to the rental terms, fuel policy and cancellation policy.</span></label>
      <div class="row mt2">
        <button type="button" class="btn ghost" id="back-trip">← Back</button>
        <button class="btn grow" type="submit">Reserve & continue to payment →</button>
      </div>
    </form>`;
  document.getElementById("back-trip").onclick = showTrip;
  document.getElementById("details-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const btn = e.target.querySelector("button[type=submit]");
    await DE.withBusy(btn, async () => {
      try {
        booking = await DE.api.post("/api/bookings", {
          ...draft,
          fullName: val("d-name"), phone: val("d-phone"), dateOfBirth: val("d-dob"),
          licenseNumber: val("d-lic").toUpperCase(), licenseExpiry: val("d-licexp"), address: val("d-address"),
          emergencyContactName: val("d-ename"), emergencyContactPhone: val("d-ephone"), specialRequests: val("d-notes"),
        });
        DE.session.del("de_checkout");
        history.replaceState(null, "", "/checkout.html?ref=" + booking.bookingRef);
        renderSummaryFromBooking();
        showPayment();
      } catch (err) { DE.toast(err.message, "error"); }
    });
  });
}
const val = (id) => document.getElementById(id).value.trim();

// ------------------------------------------------------------------ step 3: payment
async function showPayment() {
  setStep(3);
  try { savedMethods = await DE.api.get("/api/payment-methods"); } catch (e) { savedMethods = []; }
  const deadline = new Date(DE.parseDate(booking.createdAt).getTime() + 30 * 60000);
  document.getElementById("main").innerHTML = `
    <div class="card">
      <div class="row between"><h2 style="margin:0">Payment</h2><span class="badge amber" id="countdown"></span></div>
      <p class="muted small">Booking <b>${DE.esc(booking.bookingRef)}</b> is reserved for you. 🔒 256-bit secure simulated gateway.</p>
      ${savedMethods.length ? `<h3 class="mt">Saved methods</h3><div id="saved-list">${savedMethods.map((m) => `
        <label class="saved-method" data-id="${m.id}"><input type="radio" name="saved" value="${m.id}">
          <span style="font-size:1.4rem">${m.type === "CARD" ? "💳" : "📱"}</span>
          <div class="grow"><b>${DE.esc(m.label)}</b><div class="small muted">${m.type === "CARD" ? "Expires " + DE.esc(m.expiry || "") : "UPI ID"} ${m.defaultMethod ? "· Default" : ""}</div></div>
        </label>`).join("")}</div>
        <div id="saved-cvv" class="field hidden"><label>CVV for saved card</label><input id="s-cvv" maxlength="4" inputmode="numeric" style="width:120px"></div>
        <div class="center muted small mb">— or pay with a new method —</div>` : ""}
      <div class="pay-tabs" id="pay-tabs">
        <div class="pay-tab" data-m="UPI"><span class="ico">📱</span>UPI</div>
        <div class="pay-tab" data-m="CARD"><span class="ico">💳</span>Card</div>
        <div class="pay-tab" data-m="NET_BANKING"><span class="ico">🏦</span>Net banking</div>
        <div class="pay-tab" data-m="WALLET"><span class="ico">👛</span>Wallet</div>
        <div class="pay-tab" data-m="PAY_AT_PICKUP"><span class="ico">💵</span>At pickup</div>
      </div>
      <div id="pay-form"></div>
      <button class="btn accent block mt" id="pay-btn">Pay ${DE.money(booking.totalAmount)}</button>
      <div class="notice mt small">🧪 Test mode: UPI <b>name@okhdfc</b> succeeds, <b>fail@upi</b> fails. Card <b>4111 1111 1111 1111</b> succeeds, <b>4000 0000 0000 0002</b> is declined.</div>
      <div class="center mt"><button class="btn ghost sm" id="cancel-btn">Cancel this reservation</button></div>
    </div>`;

  const tick = () => {
    const el = document.getElementById("countdown");
    if (!el) return;
    const ms = deadline - new Date();
    if (ms <= 0) { el.textContent = "Reservation expired"; return; }
    el.textContent = `⏱ Complete payment in ${Math.floor(ms / 60000)}:${String(Math.floor((ms % 60000) / 1000)).padStart(2, "0")}`;
    setTimeout(tick, 1000);
  };
  tick();

  document.querySelectorAll("#saved-list .saved-method").forEach((el) => el.addEventListener("click", () => {
    selectedSaved = savedMethods.find((m) => String(m.id) === el.dataset.id);
    document.querySelectorAll("#saved-list .saved-method").forEach((x) => x.classList.toggle("selected", x === el));
    document.querySelectorAll("#pay-tabs .pay-tab").forEach((t) => t.classList.remove("active"));
    document.getElementById("pay-form").innerHTML = "";
    document.getElementById("saved-cvv").classList.toggle("hidden", selectedSaved.type !== "CARD");
  }));
  document.querySelectorAll("#pay-tabs .pay-tab").forEach((t) => t.addEventListener("click", () => selectMethod(t.dataset.m)));
  document.getElementById("pay-btn").onclick = pay;
  document.getElementById("cancel-btn").onclick = async () => {
    if (!confirm("Cancel this reservation?")) return;
    try { await DE.api.post(`/api/bookings/${booking.bookingRef}/cancel`); location.href = "/dashboard.html"; }
    catch (e) { DE.toast(e.message, "error"); }
  };
  const def = savedMethods.find((m) => m.defaultMethod);
  if (def) document.querySelector(`#saved-list .saved-method[data-id="${def.id}"]`).click();
  else selectMethod("UPI");
}

function selectMethod(m) {
  payMethod = m;
  selectedSaved = null;
  document.querySelectorAll("#saved-list .saved-method").forEach((x) => { x.classList.remove("selected"); x.querySelector("input").checked = false; });
  const cvv = document.getElementById("saved-cvv");
  if (cvv) cvv.classList.add("hidden");
  document.querySelectorAll("#pay-tabs .pay-tab").forEach((t) => t.classList.toggle("active", t.dataset.m === m));
  const f = document.getElementById("pay-form");
  const save = `<label class="check mt"><input type="checkbox" id="p-save"> <span>Save for faster checkout (stored as a secure token, never the full number)</span></label>`;
  if (m === "UPI") {
    f.innerHTML = `<div class="field"><label>UPI ID</label><input id="p-upi" placeholder="yourname@okhdfc"></div>
      <div class="row small muted">Works with GPay, PhonePe, Paytm, BHIM and all bank UPI apps.</div>${save}`;
  } else if (m === "CARD") {
    f.innerHTML = `
      <div class="card-preview"><div class="row between"><b>DriveEase Pay</b><b id="cp-brand">CARD</b></div>
        <div class="num" id="cp-num">•••• •••• •••• ••••</div>
        <div class="row between"><span id="cp-name">CARD HOLDER</span><span id="cp-exp">MM/YY</span></div></div>
      <div class="field"><label>Card number</label><input id="p-card" inputmode="numeric" maxlength="23" placeholder="4111 1111 1111 1111"></div>
      <div class="field"><label>Name on card</label><input id="p-holder" placeholder="As printed on card"></div>
      <div class="grid-2">
        <div class="field"><label>Expiry (MM/YY)</label><input id="p-exp" maxlength="5" placeholder="08/29"></div>
        <div class="field"><label>CVV</label><input id="p-cvv" type="password" maxlength="4" inputmode="numeric" placeholder="123"></div>
      </div>${save}`;
    const num = document.getElementById("p-card");
    num.addEventListener("input", () => {
      const d = num.value.replace(/\D/g, "").slice(0, 19);
      num.value = d.replace(/(.{4})/g, "$1 ").trim();
      document.getElementById("cp-num").textContent = (d + "•".repeat(Math.max(0, 16 - d.length))).replace(/(.{4})/g, "$1 ").trim();
      document.getElementById("cp-brand").textContent = d.startsWith("4") ? "VISA" : /^(5[1-5]|2[2-7])/.test(d) ? "MASTERCARD" : /^3[47]/.test(d) ? "AMEX" : /^(60|65|81|82|508)/.test(d) ? "RUPAY" : "CARD";
    });
    document.getElementById("p-holder").addEventListener("input", (e) => { document.getElementById("cp-name").textContent = e.target.value.toUpperCase() || "CARD HOLDER"; });
    const exp = document.getElementById("p-exp");
    exp.addEventListener("input", () => {
      let d = exp.value.replace(/\D/g, "").slice(0, 4);
      if (d.length > 2) d = d.slice(0, 2) + "/" + d.slice(2);
      exp.value = d;
      document.getElementById("cp-exp").textContent = d || "MM/YY";
    });
  } else if (m === "NET_BANKING") {
    f.innerHTML = `<div class="field"><label>Choose your bank</label><select id="p-bank">${meta.banks.map((b) => `<option>${DE.esc(b)}</option>`).join("")}</select></div>
      <p class="muted small">You'll be redirected to your bank's secure page (simulated).</p>`;
  } else if (m === "WALLET") {
    f.innerHTML = `<div class="grid-2">
      <div class="field"><label>Wallet</label><select id="p-wallet">${meta.wallets.map((w) => `<option>${DE.esc(w)}</option>`).join("")}</select></div>
      <div class="field"><label>Wallet mobile number</label><input id="p-wphone" value="${DE.esc(me.phone || "")}"></div></div>`;
  } else {
    f.innerHTML = `<div class="notice warn">Your booking will be confirmed now. Pay <b>${DE.money(booking.totalAmount)}</b> + refundable deposit by cash, card or UPI at the pickup counter before the trip starts.</div>`;
  }
  document.getElementById("pay-btn").textContent = m === "PAY_AT_PICKUP" ? "Confirm booking (pay at pickup)" : `Pay ${DE.money(booking.totalAmount)}`;
}

async function pay() {
  const btn = document.getElementById("pay-btn");
  const get = (id) => { const el = document.getElementById(id); return el ? el.value.trim() : null; };
  const saveEl = document.getElementById("p-save");
  const body = selectedSaved
    ? { method: selectedSaved.type, savedMethodId: selectedSaved.id, cardCvv: get("s-cvv") }
    : {
      method: payMethod, upiId: get("p-upi"), cardNumber: get("p-card"), cardHolder: get("p-holder"),
      cardExpiry: get("p-exp"), cardCvv: get("p-cvv"), bank: get("p-bank"), walletProvider: get("p-wallet"),
      walletPhone: get("p-wphone"), saveMethod: !!(saveEl && saveEl.checked),
    };
  await DE.withBusy(btn, async () => {
    try {
      await new Promise((r) => setTimeout(r, 900)); // gateway feel
      booking = await DE.api.post(`/api/bookings/${booking.bookingRef}/pay`, body);
      showConfirmation();
    } catch (e) {
      DE.toast(e.message, "error");
    }
  });
}

// ------------------------------------------------------------------ step 4: confirmation
function showConfirmation() {
  setStep(4);
  renderSummaryFromBooking();
  const b = booking;
  const cash = b.paymentMethod === "PAY_AT_PICKUP" && b.paymentStatus === "PENDING";
  document.getElementById("main").innerHTML = `
    <div class="card success-hero">
      <div class="tick">✓</div>
      <h2>Booking confirmed!</h2>
      <p class="muted">${cash ? "Pay at the pickup counter." : "Payment received."} A confirmation has been sent to ${DE.esc(me.email)} and ${DE.esc(me.phone || "")}.</p>
      <div class="mt"><span class="muted small">Booking ID</span><h2 style="margin:0">${DE.esc(b.bookingRef)}</h2></div>
      <div class="mt2"><span class="muted small">Trip OTP - show this at pickup</span><div class="otp">${DE.esc(b.tripOtp)}</div></div>
      ${b.driver ? `<div class="notice ok mt" style="text-align:left">🧑‍✈️ Your driver: <b>${DE.esc(b.driver.name)}</b> · ★ ${b.driver.rating} · <a href="tel:${DE.esc(b.driver.phone)}">${DE.esc(b.driver.phone)}</a> · ${DE.esc(b.driver.languages)}</div>` : ""}
      <div class="kv mt2" style="text-align:left">
        <div><span>Pickup</span>${DE.esc(b.pickupLocation)}<br>${DE.fmtDate(b.startTime)}</div>
        <div><span>Return</span>${DE.esc(b.dropLocation)}<br>${DE.fmtDate(b.endTime)}</div>
        <div><span>Paid</span>${cash ? "Due at pickup" : DE.money(b.totalAmount)}</div>
        <div><span>Deposit (refundable)</span>${DE.money(b.depositAmount)}</div>
      </div>
      <div class="row mt2" style="justify-content:center">
        <a class="btn" href="/dashboard.html">My bookings</a>
        <a class="btn ghost" href="/invoice.html?ref=${b.bookingRef}">View invoice</a>
        <a class="btn ghost" href="/track.html?ref=${b.bookingRef}">Trip page</a>
      </div>
    </div>`;
}
