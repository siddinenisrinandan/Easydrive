DE.renderLayout("dashboard");

let me = null;
let bookings = [];
let tab = DE.qs("tab") || "upcoming";

(async function init() {
  if (!DE.requireLogin()) return;
  document.querySelectorAll("#tabs button").forEach((b) => b.addEventListener("click", () => { tab = b.dataset.tab; render(); }));
  await reload();
  setInterval(() => { if (tab === "upcoming" && bookings.some((b) => b.status === "ONGOING")) reload(true); }, 8000);
})();

async function reload(silent) {
  try {
    [me, bookings] = await Promise.all([DE.api.get("/api/auth/me"), DE.api.get("/api/bookings")]);
    DE.setSession(DE.store.get("de_token"), me);
  } catch (e) {
    if (e.status === 401) { DE.clearSession(); DE.requireLogin(); return; }
    if (!silent) DE.toast(e.message, "error");
    return;
  }
  renderHead();
  render();
}

function renderHead() {
  const done = bookings.filter((b) => b.status === "COMPLETED");
  const km = done.reduce((s, b) => s + (b.distanceKm || 0), 0);
  document.getElementById("profile-head").innerHTML = `
    <div class="row"><span class="avatar lg">${DE.esc(DE.initials(me.fullName))}</span>
      <div><h2 style="margin:0">${DE.esc(me.fullName)}</h2><div class="muted small">${DE.esc(me.email)} · ${DE.esc(me.phone || "")}</div></div></div>
    <div class="row" style="gap:28px">
      <div><b style="font-size:1.4rem;font-family:Poppins">${bookings.length}</b><div class="muted small">Bookings</div></div>
      <div><b style="font-size:1.4rem;font-family:Poppins">${Math.round(km)}</b><div class="muted small">km driven</div></div>
      <div><b style="font-size:1.4rem;font-family:Poppins;color:var(--accent)">${me.loyaltyPoints}</b><div class="muted small">Loyalty points</div></div>
      <a class="btn" href="/vehicles.html">+ New booking</a>
    </div>`;
}

function render() {
  document.querySelectorAll("#tabs button").forEach((b) => b.classList.toggle("active", b.dataset.tab === tab));
  const c = document.getElementById("content");
  if (tab === "upcoming") {
    const list = bookings.filter((b) => ["PENDING_PAYMENT", "CONFIRMED", "ONGOING"].includes(b.status))
      .sort((a, b) => (a.status === "ONGOING" ? -1 : b.status === "ONGOING" ? 1 : DE.parseDate(a.startTime) - DE.parseDate(b.startTime)));
    c.innerHTML = list.length ? list.map(bookingCard).join("") : empty("No upcoming trips", "Plan your next road trip today!");
  } else if (tab === "past") {
    const list = bookings.filter((b) => ["COMPLETED", "CANCELLED"].includes(b.status));
    c.innerHTML = list.length ? list.map(bookingCard).join("") : empty("No past trips yet", "Your completed trips will appear here.");
  } else if (tab === "payments") {
    renderPayments();
    return;
  } else {
    renderProfile();
    return;
  }
  bindActions();
}

const empty = (t, s) => `<div class="card empty"><div class="big">🗓️</div><h3>${t}</h3><p>${s}</p><a class="btn" href="/vehicles.html">Browse vehicles</a></div>`;

function bookingCard(b) {
  const v = b.vehicle;
  let actions = "";
  if (b.status === "PENDING_PAYMENT") {
    actions += `<a class="btn accent sm" href="/checkout.html?ref=${b.bookingRef}">Complete payment</a>`;
  }
  if (b.status === "CONFIRMED") {
    actions += `<button class="btn success sm" data-act="start" data-ref="${b.bookingRef}">Start trip (OTP)</button>`;
  }
  if (b.status === "ONGOING") {
    actions += `<a class="btn sm" href="/track.html?ref=${b.bookingRef}">📍 Track live</a>
                <button class="btn ghost sm" data-act="end" data-ref="${b.bookingRef}">End trip</button>`;
  }
  if (b.status === "CONFIRMED" || b.status === "PENDING_PAYMENT") {
    actions += `<button class="btn ghost sm" data-act="cancel" data-ref="${b.bookingRef}">Cancel</button>`;
  }
  if (b.status === "COMPLETED" && !b.rating) {
    actions += `<button class="btn accent sm" data-act="review" data-ref="${b.bookingRef}">★ Rate trip</button>`;
  }
  if (b.status === "COMPLETED") {
    actions += `<a class="btn ghost sm" href="/track.html?ref=${b.bookingRef}">Route replay</a>`;
  }
  actions += `<a class="btn ghost sm" href="/invoice.html?ref=${b.bookingRef}">Invoice</a>`;

  const live = b.status === "ONGOING"
    ? `<div class="mt"><div class="row between small"><span><span class="live-dot"></span>Live · ${Math.round(b.currentSpeed)} km/h</span><span>${b.distanceKm.toFixed(1)} km driven</span></div>
       <div class="progress mt" style="margin-top:6px"><div style="width:${Math.round(b.tripProgress * 100)}%"></div></div></div>` : "";

  return `<div class="card booking-card">
    <div class="art">${DE.vehicleArt(v)}</div>
    <div>
      <div class="row"><b style="font-size:1.05rem">${DE.esc(v.name)}</b> ${DE.statusBadge(b.status)} <span class="badge gray">${DE.esc(b.bookingRef)}</span></div>
      <div class="kv mt">
        <div><span>Pickup</span>${DE.esc(b.pickupLocation)} · ${DE.fmtDate(b.startTime)}</div>
        <div><span>Return</span>${DE.esc(b.dropLocation)} · ${DE.fmtDate(b.endTime)}</div>
        <div><span>Amount</span>${DE.money(b.finalAmount || b.totalAmount)} ${DE.statusBadge(b.paymentStatus)}</div>
        <div><span>Insurance</span>${DE.pretty(b.insurancePlan)}</div>
        ${b.status === "CONFIRMED" ? `<div><span>Trip OTP</span><b style="letter-spacing:.2em;color:var(--primary)">${DE.esc(b.tripOtp)}</b></div>` : ""}
        ${b.driver ? `<div><span>Driver</span>${DE.esc(b.driver.name)} · <a href="tel:${DE.esc(b.driver.phone)}">${DE.esc(b.driver.phone)}</a></div>` : ""}
        ${b.refundAmount ? `<div><span>Refund</span>${DE.money(b.refundAmount)}</div>` : ""}
        ${b.extraKmCharge || b.lateFee ? `<div><span>Extra charges</span>${DE.money(b.extraKmCharge + b.lateFee)}</div>` : ""}
        ${b.rating ? `<div><span>Your rating</span><span class="stars">${DE.stars(b.rating)}</span></div>` : ""}
      </div>
      ${live}
    </div>
    <div class="actions">${actions}</div>
  </div>`;
}

function bindActions() {
  document.querySelectorAll("[data-act]").forEach((btn) => btn.addEventListener("click", () => {
    const ref = btn.dataset.ref;
    const b = bookings.find((x) => x.bookingRef === ref);
    if (btn.dataset.act === "start") startTrip(b);
    if (btn.dataset.act === "end") endTrip(b);
    if (btn.dataset.act === "cancel") cancel(b);
    if (btn.dataset.act === "review") review(b);
  }));
}

function startTrip(b) {
  DE.modal(`<h3>Start trip - ${DE.esc(b.bookingRef)}</h3>
    <p class="muted">At pickup, our executive inspects the vehicle with you and asks for your trip OTP.
    (Demo: your OTP is <b>${DE.esc(b.tripOtp)}</b>.)</p>
    <div class="field"><label>Enter 4-digit trip OTP</label><input id="otp-in" maxlength="4" inputmode="numeric" style="font-size:1.6rem;letter-spacing:.5em;text-align:center"></div>
    <label class="check mb"><input type="checkbox" id="otp-check"> <span>I have checked the vehicle for existing scratches and the fuel level.</span></label>
    <div class="row"><button class="btn ghost" data-close>Cancel</button><button class="btn success grow" id="otp-go">Verify & start trip</button></div>`,
  (root, close) => {
    root.querySelector("#otp-go").onclick = async (e) => {
      if (!root.querySelector("#otp-check").checked) { DE.toast("Please confirm the vehicle inspection", "error"); return; }
      await DE.withBusy(e.target, async () => {
        try {
          await DE.api.post(`/api/bookings/${b.bookingRef}/start`, { otp: root.querySelector("#otp-in").value });
          close();
          DE.toast("Trip started! Drive safe 🚗", "success");
          location.href = "/track.html?ref=" + b.bookingRef;
        } catch (err) { DE.toast(err.message, "error"); }
      });
    };
  });
}

async function endTrip(b) {
  if (!confirm("End this trip and return the vehicle?")) return;
  try {
    const r = await DE.api.post(`/api/bookings/${b.bookingRef}/end`);
    DE.toast(`Trip completed · ${r.distanceKm.toFixed(1)} km · Final ${DE.money(r.finalAmount)}`, "success");
    tab = "past";
    await reload();
    review(r);
  } catch (e) { DE.toast(e.message, "error"); }
}

async function cancel(b) {
  const hours = (DE.parseDate(b.startTime) - new Date()) / 3600000;
  const pct = b.paymentStatus !== "SUCCESS" ? null : hours >= 24 ? 100 : hours >= 4 ? 50 : 0;
  const msg = pct === null ? "Cancel this booking? Nothing has been charged." : `Cancel this booking? You will get a ${pct}% refund (${DE.money(b.totalAmount * pct / 100)}).`;
  if (!confirm(msg)) return;
  try {
    const r = await DE.api.post(`/api/bookings/${b.bookingRef}/cancel`);
    DE.toast(r.refundAmount ? `Cancelled. Refund of ${DE.money(r.refundAmount)} initiated.` : "Booking cancelled", "success");
    await reload();
  } catch (e) { DE.toast(e.message, "error"); }
}

function review(b) {
  let rating = 5;
  DE.modal(`<h3>How was your trip with ${DE.esc(b.vehicle.name)}?</h3>
    <div class="star-input center mb" id="stars">${[1, 2, 3, 4, 5].map((n) => `<button data-n="${n}" class="on">★</button>`).join("")}</div>
    <div class="field"><label>Tell us more (optional)</label><textarea id="rev-text" placeholder="Vehicle condition, pickup experience, driver…"></textarea></div>
    <p class="small muted">Earn 50 loyalty points for every review.</p>
    <div class="row"><button class="btn ghost" data-close>Later</button><button class="btn grow" id="rev-go">Submit review</button></div>`,
  (root, close) => {
    root.querySelectorAll("#stars button").forEach((s) => s.addEventListener("click", () => {
      rating = Number(s.dataset.n);
      root.querySelectorAll("#stars button").forEach((x) => x.classList.toggle("on", Number(x.dataset.n) <= rating));
    }));
    root.querySelector("#rev-go").onclick = async () => {
      try {
        await DE.api.post(`/api/bookings/${b.bookingRef}/review`, { rating, comment: root.querySelector("#rev-text").value });
        close();
        DE.toast("Thanks for your review! +50 points", "success");
        reload();
      } catch (e) { DE.toast(e.message, "error"); }
    };
  });
}

// ------------------------------------------------------------------ saved payments
async function renderPayments() {
  const c = document.getElementById("content");
  let list = [];
  try { list = await DE.api.get("/api/payment-methods"); } catch (e) { DE.toast(e.message, "error"); }
  c.innerHTML = `<div class="detail-grid">
    <div class="card">
      <h3>Saved cards & UPI IDs</h3>
      <p class="muted small">🔒 We store only a gateway token and masked details. Full card numbers and CVV are never stored.</p>
      ${list.length ? list.map((m) => `
        <div class="saved-method">
          <span style="font-size:1.5rem">${m.type === "CARD" ? "💳" : "📱"}</span>
          <div class="grow"><b>${DE.esc(m.label)}</b><div class="small muted">${m.type === "CARD" ? DE.esc(m.holderName || "") + " · exp " + DE.esc(m.expiry || "") : "UPI"} · added ${DE.fmtDay(m.createdAt)}</div></div>
          ${m.defaultMethod ? '<span class="badge green">Default</span>' : `<button class="btn ghost sm" data-def="${m.id}">Make default</button>`}
          <button class="btn ghost sm" data-del="${m.id}">Remove</button>
        </div>`).join("") : '<div class="empty"><div class="big">💳</div><p>No saved methods yet. Tick “Save for faster checkout” when you pay.</p></div>'}
    </div>
    <form class="card" id="add-method">
      <h3>Add a payment method</h3>
      <div class="field"><label>Type</label><select id="am-type"><option value="UPI">UPI ID</option><option value="CARD">Credit / debit card</option></select></div>
      <div id="am-upi"><div class="field"><label>UPI ID</label><input id="am-upi-id" placeholder="name@okaxis"></div></div>
      <div id="am-card" class="hidden">
        <div class="field"><label>Card number</label><input id="am-num" placeholder="4111 1111 1111 1111"></div>
        <div class="field"><label>Name on card</label><input id="am-holder"></div>
        <div class="grid-2"><div class="field"><label>Expiry MM/YY</label><input id="am-exp" placeholder="08/29"></div>
        <div class="field"><label>CVV (verification only)</label><input id="am-cvv" type="password" maxlength="4"></div></div>
      </div>
      <button class="btn block" type="submit">Save securely</button>
    </form>
  </div>`;
  const type = document.getElementById("am-type");
  type.onchange = () => {
    document.getElementById("am-upi").classList.toggle("hidden", type.value !== "UPI");
    document.getElementById("am-card").classList.toggle("hidden", type.value !== "CARD");
  };
  document.getElementById("add-method").onsubmit = async (e) => {
    e.preventDefault();
    const g = (id) => document.getElementById(id).value.trim();
    try {
      await DE.api.post("/api/payment-methods", type.value === "UPI"
        ? { method: "UPI", upiId: g("am-upi-id") }
        : { method: "CARD", cardNumber: g("am-num"), cardHolder: g("am-holder"), cardExpiry: g("am-exp"), cardCvv: g("am-cvv") });
      DE.toast("Payment method saved", "success");
      renderPayments();
    } catch (err) { DE.toast(err.message, "error"); }
  };
  c.querySelectorAll("[data-del]").forEach((b) => b.onclick = async () => {
    if (!confirm("Remove this payment method?")) return;
    try { await DE.api.del("/api/payment-methods/" + b.dataset.del); renderPayments(); } catch (e) { DE.toast(e.message, "error"); }
  });
  c.querySelectorAll("[data-def]").forEach((b) => b.onclick = async () => {
    try { await DE.api.post(`/api/payment-methods/${b.dataset.def}/default`); renderPayments(); } catch (e) { DE.toast(e.message, "error"); }
  });
}

// ------------------------------------------------------------------ profile
function renderProfile() {
  const v = (x) => DE.esc(x || "");
  document.getElementById("content").innerHTML = `
    <form class="card" id="profile-form" style="max-width:820px">
      <h3>Personal details</h3>
      <div class="grid-2">
        <div class="field"><label>Full name</label><input id="p-name" value="${v(me.fullName)}"></div>
        <div class="field"><label>Mobile</label><input id="p-phone" value="${v(me.phone)}"></div>
        <div class="field"><label>Date of birth</label><input type="date" id="p-dob" value="${v(me.dateOfBirth)}"></div>
        <div class="field"><label>City</label><input id="p-city" value="${v(me.city)}"></div>
      </div>
      <div class="field"><label>Address</label><input id="p-address" value="${v(me.address)}"></div>
      <h3 class="mt">Driving licence (KYC)</h3>
      <div class="grid-2">
        <div class="field"><label>Licence number</label><input id="p-lic" value="${v(me.licenseNumber)}" style="text-transform:uppercase"></div>
        <div class="field"><label>Valid till</label><input type="date" id="p-licexp" value="${v(me.licenseExpiry)}"></div>
      </div>
      <h3 class="mt">Emergency contact</h3>
      <div class="grid-2">
        <div class="field"><label>Name</label><input id="p-ename" value="${v(me.emergencyContactName)}"></div>
        <div class="field"><label>Mobile</label><input id="p-ephone" value="${v(me.emergencyContactPhone)}"></div>
      </div>
      <div class="row"><button class="btn" type="submit">Save profile</button><span class="muted small">Member since ${DE.fmtDay(me.createdAt)}</span></div>
    </form>`;
  document.getElementById("profile-form").onsubmit = async (e) => {
    e.preventDefault();
    const g = (id) => document.getElementById(id).value.trim();
    try {
      me = await DE.api.put("/api/auth/me", {
        fullName: g("p-name"), phone: g("p-phone"), dateOfBirth: g("p-dob"), city: g("p-city"), address: g("p-address"),
        licenseNumber: g("p-lic").toUpperCase(), licenseExpiry: g("p-licexp"), emergencyContactName: g("p-ename"), emergencyContactPhone: g("p-ephone"),
      });
      DE.setSession(DE.store.get("de_token"), me);
      DE.toast("Profile saved", "success");
      renderHead();
    } catch (err) { DE.toast(err.message, "error"); }
  };
}
