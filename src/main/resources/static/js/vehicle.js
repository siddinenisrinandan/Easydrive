DE.renderLayout("");

const vehicleId = DE.qs("id");
let vehicle, meta;
const trip = { insurancePlan: "STANDARD", withDriver: false, addons: [], couponCode: "" };
let quoteTimer;

(async function init() {
  if (!vehicleId) { location.href = "/vehicles.html"; return; }
  try {
    [vehicle, meta] = await Promise.all([DE.api.get("/api/vehicles/" + vehicleId), DE.api.get("/api/meta")]);
  } catch (e) {
    document.getElementById("page").innerHTML = `<div class="card empty"><div class="big">🚫</div><h3>${DE.esc(e.message)}</h3><a class="btn" href="/vehicles.html">Back to fleet</a></div>`;
    return;
  }
  document.title = vehicle.name + " - DriveEase";
  render();
})();

function render() {
  const v = vehicle;
  const isCar = v.type === "CAR";
  const start = DE.qs("start") ? new Date(DE.qs("start")) : (() => { const d = new Date(); d.setDate(d.getDate() + 1); d.setHours(9, 0, 0, 0); return d; })();
  const end = DE.qs("end") ? new Date(DE.qs("end")) : new Date(start.getTime() + 24 * 3600 * 1000);
  const loc = DE.qs("location") || meta.locations[0].name;
  const locOptions = (sel) => meta.locations.map((l) => `<option ${l.name === sel ? "selected" : ""} value="${DE.esc(l.name)}">${DE.esc(l.name)} · ${DE.esc(l.city)}</option>`).join("");
  const features = (v.features || "").split(",").filter(Boolean);

  document.getElementById("page").innerHTML = `
    <a href="/vehicles.html?type=${v.type}" class="small">← Back to ${isCar ? "cars" : "bikes"}</a>
    <div class="detail-grid mt">
      <div>
        <div class="card" style="padding:0;overflow:hidden">
          <div class="detail-art">${DE.vehicleArt(v)}</div>
          <div style="padding:22px">
            <div class="row between">
              <div>
                <span class="badge">${DE.esc(v.category)}</span> <span class="badge gray">${v.modelYear}</span>
                ${v.available ? '<span class="badge green">Available</span>' : '<span class="badge red">Under maintenance</span>'}
                <h1 class="mt" style="font-size:1.9rem">${DE.esc(v.name)}</h1>
                <div class="muted">★ ${Number(v.rating).toFixed(1)} · ${v.tripsCompleted} trips · ${DE.esc(v.registrationNumber || "")} · ${DE.esc(v.city)}</div>
              </div>
              <div style="text-align:right">
                <div class="price" style="font-size:1.8rem">${DE.money(v.pricePerDay)}<small>/day</small></div>
                <div class="muted small">or ${DE.money(v.pricePerHour)}/hour</div>
              </div>
            </div>
            <p class="mt">${DE.esc(v.description)}</p>
            <div class="spec-table mt">
              <div class="spec-box"><b>${isCar ? v.seats + " seats" : "2 riders"}</b><span>Capacity</span></div>
              <div class="spec-box"><b>${DE.esc(v.fuelType)}</b><span>Fuel</span></div>
              <div class="spec-box"><b>${DE.esc(v.transmission)}</b><span>Transmission</span></div>
              <div class="spec-box"><b>${DE.esc(v.engine)}</b><span>Engine</span></div>
              <div class="spec-box"><b>${DE.esc(v.mileage)}</b><span>Mileage / range</span></div>
              <div class="spec-box"><b>${v.kmLimitPerDay} km/day</b><span>Free km, then ${DE.money(v.extraKmCharge)}/km</span></div>
            </div>
            <h3 class="mt2">Features</h3>
            <div class="feature-list">${features.map((f) => `<span>✓ ${DE.esc(f.trim())}</span>`).join("")}</div>
            <h3 class="mt2">Rental terms</h3>
            <ul class="muted" style="padding-left:18px;margin:0">
              <li>Refundable security deposit ${DE.money(v.deposit)} (50% off with Premium insurance)</li>
              <li>Fuel policy: same-to-same · Late return: 1 hour grace, then 1.5× hourly rate</li>
              <li>Free cancellation up to 24 hours before pickup</li>
              <li>${isCar ? "Driver age 21+ with valid LMV licence, or add a professional driver" : "Rider age 18+ with valid MCWG licence · Helmet included"}</li>
            </ul>
          </div>
        </div>
      </div>

      <div class="sticky">
        <div class="card">
          <h3>Plan your trip</h3>
          <div class="field"><label>Pickup location</label><select id="t-pickup">${locOptions(loc)}</select></div>
          <div class="field"><label>Drop location</label><select id="t-drop">${locOptions(loc)}</select>
            <div class="hint">Different drop location adds a one-way fee.</div></div>
          <div class="grid-2">
            <div class="field"><label>Pickup</label><input type="datetime-local" id="t-start" value="${DE.toLocalInput(start)}"></div>
            <div class="field"><label>Return</label><input type="datetime-local" id="t-end" value="${DE.toLocalInput(end)}"></div>
          </div>
          <div id="availability" class="notice mb">Checking availability…</div>

          <label>Insurance plan</label>
          <div class="plan-options mb" id="plans">
            ${meta.insurancePlans.map((p) => `
              <label class="plan ${p.code === trip.insurancePlan ? "selected" : ""}">
                <input type="radio" name="plan" value="${p.code}" ${p.code === trip.insurancePlan ? "checked" : ""}>
                <div class="grow">
                  <div class="p-title"><span>${DE.esc(p.label)}</span><span>${(isCar ? p.carPerDay : p.bikePerDay) ? DE.money(isCar ? p.carPerDay : p.bikePerDay) + "/day" : "Free"}</span></div>
                  <div class="p-desc">${DE.esc(p.description)} Max liability ${DE.money(isCar ? p.carDeductible : p.bikeDeductible)}.</div>
                </div>
              </label>`).join("")}
          </div>

          ${isCar ? `<label class="check mb addon"><input type="checkbox" id="t-driver"><div class="grow"><b>Add professional driver</b><small>Verified chauffeur, ${DE.money(meta.driverPerDay)}/day. Licence not needed.</small></div></label>` : ""}

          <label>Add-ons</label>
          <div class="addon-list mb">
            ${meta.addons.filter((a) => a.appliesTo === "ALL" || a.appliesTo === v.type).map((a) => `
              <label class="addon"><input type="checkbox" value="${a.code}" class="t-addon">
                <div class="grow"><b>${DE.esc(a.name)}</b><small>${DE.esc(a.description)}</small></div>
                <b class="small">${DE.money(a.price)}${a.perDay ? "/day" : ""}</b></label>`).join("")}
          </div>

          <label>Coupon code</label>
          <div class="row mb"><input id="t-coupon" placeholder="e.g. FIRST20" class="grow" style="text-transform:uppercase"><button class="btn ghost" id="t-apply">Apply</button></div>
          <div id="coupon-msg" class="small mb"></div>

          <div id="quote"></div>
          <button class="btn accent block mt" id="book-btn" ${v.available ? "" : "disabled"}>Proceed to checkout →</button>
          <p class="hint center">You won't be charged yet.</p>
        </div>
      </div>
    </div>`;

  document.querySelectorAll("#plans input").forEach((r) => r.addEventListener("change", () => {
    trip.insurancePlan = r.value;
    document.querySelectorAll("#plans .plan").forEach((p) => p.classList.toggle("selected", p.querySelector("input").checked));
    refresh();
  }));
  ["t-pickup", "t-drop", "t-start", "t-end"].forEach((id) => document.getElementById(id).addEventListener("change", refresh));
  const drv = document.getElementById("t-driver");
  if (drv) drv.addEventListener("change", () => { trip.withDriver = drv.checked; refresh(); });
  document.querySelectorAll(".t-addon").forEach((c) => c.addEventListener("change", () => {
    trip.addons = [...document.querySelectorAll(".t-addon:checked")].map((x) => x.value);
    refresh();
  }));
  document.getElementById("t-apply").addEventListener("click", () => {
    trip.couponCode = document.getElementById("t-coupon").value.trim().toUpperCase();
    refresh();
  });
  document.getElementById("book-btn").addEventListener("click", proceed);
  refresh();
}

function tripRequest() {
  return {
    vehicleId: vehicle.id,
    startTime: document.getElementById("t-start").value,
    endTime: document.getElementById("t-end").value,
    pickupLocation: document.getElementById("t-pickup").value,
    dropLocation: document.getElementById("t-drop").value,
    insurancePlan: trip.insurancePlan,
    withDriver: trip.withDriver,
    addons: trip.addons,
    couponCode: trip.couponCode,
  };
}

function refresh() {
  clearTimeout(quoteTimer);
  quoteTimer = setTimeout(async () => {
    const req = tripRequest();
    const qEl = document.getElementById("quote");
    const aEl = document.getElementById("availability");
    const btn = document.getElementById("book-btn");
    try {
      const q = await DE.api.post("/api/pricing/quote", req);
      qEl.innerHTML = `
        <table class="breakdown">
          ${q.lines.map((l) => `<tr class="${l.amount < 0 ? "neg" : ""}"><td>${DE.esc(l.label)}<small>${DE.esc(l.note || "")}</small></td><td>${DE.money2(l.amount)}</td></tr>`).join("")}
        </table>
        <div class="total-row"><span class="muted">Total payable (${q.billableDays} day${q.billableDays > 1 ? "s" : ""})</span><b>${DE.money(q.totalAmount)}</b></div>
        <div class="small muted">+ refundable deposit ${DE.money(q.depositAmount)} · ${q.kmLimit} km free</div>`;
      const cm = document.getElementById("coupon-msg");
      cm.innerHTML = q.couponMessage ? `<span style="color:${q.couponApplied ? "var(--success)" : "var(--danger)"}">${DE.esc(q.couponMessage)}</span>` : "";
      const av = await DE.api.get(`/api/vehicles/${vehicle.id}/availability?start=${encodeURIComponent(req.startTime + ":00")}&end=${encodeURIComponent(req.endTime + ":00")}`);
      aEl.className = "notice mb " + (av.available ? "ok" : "err");
      aEl.textContent = (av.available ? "✓ " : "✕ ") + av.message;
      btn.disabled = !av.available;
    } catch (e) {
      qEl.innerHTML = `<div class="notice err">${DE.esc(e.message)}</div>`;
      aEl.className = "notice mb warn";
      aEl.textContent = "Choose valid dates to check availability";
      btn.disabled = true;
    }
  }, 200);
}

function proceed() {
  const req = tripRequest();
  DE.session.set("de_checkout", JSON.stringify({ ...req, vehicleName: vehicle.name }));
  if (!DE.loggedIn()) {
    DE.toast("Please login or create an account to continue");
    setTimeout(() => (location.href = "/auth.html?next=" + encodeURIComponent("/checkout.html")), 700);
    return;
  }
  location.href = "/checkout.html";
}
