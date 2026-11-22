DE.renderLayout("admin");

let view = "overview";
let charts = [];
let liveMap = null;
let refreshTimer = null;
const A = "/api/admin";

(function init() {
  document.getElementById("admin-login").addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      const r = await DE.api.post(A + "/login", { username: document.getElementById("a-user").value.trim(), password: document.getElementById("a-pass").value });
      DE.setAdmin(r.token);
      start();
    } catch (err) { DE.toast(err.message, "error"); }
  });
  document.querySelectorAll("#admin-menu [data-view]").forEach((b) => b.addEventListener("click", () => show(b.dataset.view)));
  document.getElementById("admin-logout").onclick = () => { DE.setAdmin(null); location.reload(); };
  if (DE.isAdmin()) start(); else showLogin();
})();

function showLogin() {
  document.getElementById("admin-login").classList.remove("hidden");
  document.getElementById("admin-app").classList.add("hidden");
}

async function start() {
  try {
    await DE.api.get(A + "/stats");
  } catch (e) {
    DE.setAdmin(null);
    showLogin();
    return;
  }
  document.getElementById("admin-login").classList.add("hidden");
  document.getElementById("admin-app").classList.remove("hidden");
  show(view);
}

function show(v) {
  view = v;
  document.querySelectorAll("#admin-menu [data-view]").forEach((b) => b.classList.toggle("active", b.dataset.view === v));
  charts.forEach((c) => c.destroy());
  charts = [];
  if (liveMap) { liveMap.remove(); liveMap = null; }
  clearInterval(refreshTimer);
  const fn = { overview, bookings, live, alerts, vehicles, drivers, customers, payments }[v];
  fn().catch((e) => {
    if (e.status === 401) { DE.setAdmin(null); showLogin(); return; }
    DE.toast(e.message, "error");
  });
  updateAlertCount();
}

async function updateAlertCount() {
  try {
    const s = await DE.api.get(A + "/stats");
    const el = document.getElementById("alert-count");
    el.textContent = s.openAlerts;
    el.classList.toggle("hidden", !s.openAlerts);
  } catch (e) { /* ignore */ }
}

const main = () => document.getElementById("admin-view");

// ------------------------------------------------------------------ overview
async function overview() {
  const s = await DE.api.get(A + "/stats");
  main().innerHTML = `
    <div class="stat-grid">
      <div class="card stat"><div class="icon">💰</div><div><b>${DE.money(s.revenue)}</b><span>Net revenue</span></div></div>
      <div class="card stat"><div class="icon">📅</div><div><b>${s.totalBookings}</b><span>Total bookings</span></div></div>
      <div class="card stat"><div class="icon">📍</div><div><b>${s.activeTrips}</b><span>Live trips · ${s.utilization}% fleet in use</span></div></div>
      <div class="card stat"><div class="icon">🚨</div><div><b style="color:${s.openAlerts ? "var(--danger)" : "inherit"}">${s.openAlerts}</b><span>Open alerts</span></div></div>
      <div class="card stat"><div class="icon">🚗</div><div><b>${s.cars} + ${s.bikes}</b><span>Cars + bikes · ${s.inMaintenance} in maintenance</span></div></div>
      <div class="card stat"><div class="icon">👥</div><div><b>${s.customers}</b><span>Customers</span></div></div>
      <div class="card stat"><div class="icon">🧑‍✈️</div><div><b>${s.drivers}</b><span>Drivers</span></div></div>
      <div class="card stat"><div class="icon">↩</div><div><b>${DE.money(s.refunds)}</b><span>Refunds issued</span></div></div>
    </div>
    <div class="chart-grid mt">
      <div class="card"><h3>Revenue - last 7 days</h3><canvas id="c-rev" height="120"></canvas></div>
      <div class="card"><h3>Bookings by status</h3><canvas id="c-status" height="200"></canvas></div>
    </div>
    <div class="chart-grid mt">
      <div class="card"><h3>Top vehicles</h3>
        <table class="data">${s.topVehicles.map((t, i) => `<tr><td>${i + 1}</td><td>${DE.esc(t.name)}</td><td><b>${t.bookings}</b> bookings</td></tr>`).join("") || "<tr><td>No data</td></tr>"}</table></div>
      <div class="card"><h3>Cars vs bikes · insurance mix</h3><canvas id="c-type" height="200"></canvas></div>
    </div>`;
  if (typeof Chart === "undefined") return;
  charts.push(new Chart(document.getElementById("c-rev"), {
    type: "bar",
    data: { labels: s.revenueLast7Days.map((d) => DE.fmtDay(d.date)), datasets: [{ label: "Revenue (₹)", data: s.revenueLast7Days.map((d) => d.amount), backgroundColor: "#4f46e5", borderRadius: 8 }] },
    options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } },
  }));
  const st = s.bookingsByStatus;
  charts.push(new Chart(document.getElementById("c-status"), {
    type: "doughnut",
    data: { labels: Object.keys(st).map(DE.pretty), datasets: [{ data: Object.values(st), backgroundColor: ["#f59e0b", "#3b82f6", "#16a34a", "#94a3b8", "#ef4444"] }] },
    options: { plugins: { legend: { position: "bottom" } } },
  }));
  const labels = [...Object.keys(s.bookingsByType), ...Object.keys(s.insuranceMix)];
  charts.push(new Chart(document.getElementById("c-type"), {
    type: "bar",
    data: { labels, datasets: [{ data: [...Object.values(s.bookingsByType), ...Object.values(s.insuranceMix)], backgroundColor: ["#4f46e5", "#f59e0b", "#94a3b8", "#16a34a", "#7c3aed"], borderRadius: 8 }] },
    options: { indexAxis: "y", plugins: { legend: { display: false } } },
  }));
}

// ------------------------------------------------------------------ bookings
async function bookings() {
  const [list, drv] = await Promise.all([DE.api.get(A + "/bookings"), DE.api.get(A + "/drivers")]);
  const statuses = ["PENDING_PAYMENT", "CONFIRMED", "ONGOING", "COMPLETED", "CANCELLED"];
  main().innerHTML = `<div class="card">
    <div class="row between mb"><h3 style="margin:0">All bookings (${list.length})</h3>
      <input id="b-search" placeholder="Search ref, customer, vehicle…" style="width:260px"></div>
    <div class="table-wrap"><table class="data" id="b-table">
      <tr><th>Ref</th><th>Customer</th><th>Vehicle</th><th>Trip</th><th>Amount</th><th>Payment</th><th>Driver</th><th>Status</th><th></th></tr>
      ${list.map((b) => `<tr data-search="${DE.esc((b.bookingRef + " " + b.customer.fullName + " " + b.vehicle.name + " " + b.customer.phone).toLowerCase())}">
        <td><b>${DE.esc(b.bookingRef)}</b><div class="small muted">${DE.fmtDay(b.createdAt)}</div></td>
        <td>${DE.esc(b.customer.fullName)}<div class="small muted">${DE.esc(b.customer.phone || "")}</div>
            <div class="small muted">🚨 ${DE.esc(b.customer.emergencyContactName || "-")} ${DE.esc(b.customer.emergencyContactPhone || "")}</div></td>
        <td>${DE.esc(b.vehicle.name)}<div class="small muted">${DE.esc(b.vehicle.registrationNumber || "")}</div></td>
        <td class="small">${DE.esc(b.pickupLocation)} → ${DE.esc(b.dropLocation)}<br>${DE.fmtDate(b.startTime)}<br>${DE.fmtDate(b.endTime)}</td>
        <td><b>${DE.money(b.finalAmount || b.totalAmount)}</b><div class="small muted">${DE.pretty(b.insurancePlan)}</div></td>
        <td>${DE.statusBadge(b.paymentStatus)}<div class="small muted">${DE.pretty(b.paymentMethod || "")}</div></td>
        <td>${b.vehicle.type === "CAR" ? `<select data-driver="${b.bookingRef}"><option value="">Self-drive</option>${drv.map((d) => `<option value="${d.id}" ${b.driver && b.driver.id === d.id ? "selected" : ""}>${DE.esc(d.name)}</option>`).join("")}</select>` : '<span class="muted small">Bike</span>'}</td>
        <td><select data-status="${b.bookingRef}">${statuses.map((s) => `<option value="${s}" ${s === b.status ? "selected" : ""}>${DE.pretty(s)}</option>`).join("")}</select></td>
        <td><a class="btn ghost sm" href="/track.html?ref=${b.bookingRef}" target="_blank">Track</a></td>
      </tr>`).join("")}
    </table></div></div>`;
  document.getElementById("b-search").oninput = (e) => {
    const q = e.target.value.toLowerCase();
    document.querySelectorAll("#b-table tr[data-search]").forEach((tr) => tr.classList.toggle("hidden", !tr.dataset.search.includes(q)));
  };
  document.querySelectorAll("[data-status]").forEach((sel) => sel.addEventListener("change", async () => {
    try {
      await DE.api.put(`${A}/bookings/${sel.dataset.status}/status`, { status: sel.value });
      DE.toast("Status updated", "success");
    } catch (e) { DE.toast(e.message, "error"); }
    show("bookings");
  }));
  document.querySelectorAll("[data-driver]").forEach((sel) => sel.addEventListener("change", async () => {
    if (!sel.value) { DE.toast("Pick a driver to assign"); return; }
    try {
      await DE.api.put(`${A}/bookings/${sel.dataset.driver}/driver`, { driverId: Number(sel.value) });
      DE.toast("Driver assigned", "success");
    } catch (e) { DE.toast(e.message, "error"); show("bookings"); }
  }));
}

// ------------------------------------------------------------------ live fleet map
async function live() {
  main().innerHTML = `<div class="card"><div class="row between"><h3 style="margin:0"><span class="live-dot"></span>Live fleet</h3><span class="small muted" id="live-count"></span></div>
    <div id="map" class="mt" style="height:520px"></div><div id="live-list" class="mt"></div></div>`;
  if (typeof L === "undefined") return;
  liveMap = L.map("map").setView([17.385, 78.4867], 11);
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 19, attribution: "&copy; OpenStreetMap contributors" }).addTo(liveMap);
  const markers = {};
  let fitted = false;
  const refresh = async () => {
    const list = (await DE.api.get(A + "/bookings")).filter((b) => b.status === "ONGOING" && b.currentLat != null);
    document.getElementById("live-count").textContent = `${list.length} vehicle(s) on the road`;
    list.forEach((b) => {
      const pos = [b.currentLat, b.currentLng];
      const over = b.currentSpeed > (b.vehicle.type === "CAR" ? 90 : 70);
      const html = `<b>${DE.esc(b.vehicle.name)}</b><br>${DE.esc(b.bookingRef)} · ${DE.esc(b.customer.fullName)}<br>${Math.round(b.currentSpeed)} km/h ${over ? "⚠️" : ""}<br><a href="/track.html?ref=${b.bookingRef}" target="_blank">Open tracking</a>`;
      if (!markers[b.bookingRef]) {
        markers[b.bookingRef] = L.marker(pos, { icon: L.divIcon({ html: b.vehicle.type === "BIKE" ? "🏍️" : "🚗", className: "vehicle-marker", iconSize: [30, 30] }) }).addTo(liveMap).bindPopup(html);
      } else {
        markers[b.bookingRef].setLatLng(pos).setPopupContent(html);
      }
    });
    if (!fitted && list.length) {
      liveMap.fitBounds(L.latLngBounds(list.map((b) => [b.currentLat, b.currentLng])).pad(0.5));
      fitted = true;
    }
    document.getElementById("live-list").innerHTML = list.length ? `<table class="data"><tr><th>Ref</th><th>Vehicle</th><th>Customer</th><th>Speed</th><th>Progress</th><th>Driven</th></tr>
      ${list.map((b) => `<tr><td>${DE.esc(b.bookingRef)}</td><td>${DE.esc(b.vehicle.name)}</td><td>${DE.esc(b.customer.fullName)} · ${DE.esc(b.customer.phone || "")}</td>
      <td>${Math.round(b.currentSpeed)} km/h</td><td>${Math.round(b.tripProgress * 100)}%</td><td>${b.distanceKm.toFixed(1)} km</td></tr>`).join("")}</table>`
      : '<div class="empty">No live trips right now.</div>';
  };
  await refresh();
  refreshTimer = setInterval(() => refresh().catch(() => {}), 3000);
}

// ------------------------------------------------------------------ alerts
async function alerts() {
  const list = await DE.api.get(A + "/alerts");
  main().innerHTML = `<div class="card"><h3>SOS, incidents & support</h3>
    ${list.length ? list.map((a) => `<div class="alert-item ${a.type}" style="padding:14px">
      <div class="row between"><div><b>${DE.pretty(a.type)}</b> · ${DE.esc(a.bookingRef)} · <span class="small muted">${DE.fmtDate(a.createdAt)} · by ${DE.esc(a.raisedBy)}</span></div>${DE.statusBadge(a.status)}</div>
      <div class="mt" style="margin-top:6px">${DE.esc(a.message)}</div>
      <div class="kv mt small">
        <div><span>Customer</span>${DE.esc(a.customerName || "-")} · <a href="tel:${DE.esc(a.customerPhone || "")}">${DE.esc(a.customerPhone || "")}</a></div>
        <div><span>Vehicle</span>${DE.esc(a.vehicleName || "-")}</div>
        <div><span>Driver</span>${a.driverName ? DE.esc(a.driverName) + " · " + DE.esc(a.driverPhone) : "Self-drive"}</div>
        <div><span>Location</span>${a.lat != null ? `<a target="_blank" rel="noopener" href="https://www.openstreetmap.org/?mlat=${a.lat}&mlon=${a.lng}#map=15/${a.lat}/${a.lng}">${a.lat.toFixed(4)}, ${a.lng.toFixed(4)}</a>` : "-"}</div>
      </div>
      ${a.resolutionNote ? `<div class="small mt" style="color:var(--success)">↳ ${DE.esc(a.resolutionNote)}</div>` : ""}
      ${a.status !== "RESOLVED" ? `<div class="row mt"><input placeholder="Resolution note / action taken" data-note="${a.id}" class="grow">
        ${a.status === "OPEN" ? `<button class="btn ghost sm" data-progress="${a.id}">Acknowledge</button>` : ""}
        <button class="btn success sm" data-resolve="${a.id}">Resolve</button></div>` : ""}
    </div>`).join("") : '<div class="empty"><div class="big">✅</div>No alerts</div>'}</div>`;
  const update = async (id, status) => {
    const note = document.querySelector(`[data-note="${id}"]`).value;
    try { await DE.api.put(`${A}/alerts/${id}`, { status, note }); DE.toast("Alert updated", "success"); show("alerts"); }
    catch (e) { DE.toast(e.message, "error"); }
  };
  document.querySelectorAll("[data-resolve]").forEach((b) => (b.onclick = () => update(b.dataset.resolve, "RESOLVED")));
  document.querySelectorAll("[data-progress]").forEach((b) => (b.onclick = () => update(b.dataset.progress, "IN_PROGRESS")));
  refreshTimer = setInterval(updateAlertCount, 10000);
}

// ------------------------------------------------------------------ fleet
async function vehicles() {
  const list = await DE.api.get(A + "/vehicles");
  main().innerHTML = `<div class="card">
    <div class="row between mb"><h3 style="margin:0">Fleet (${list.length})</h3><button class="btn sm" id="add-v">+ Add vehicle</button></div>
    <div class="table-wrap"><table class="data">
      <tr><th></th><th>Vehicle</th><th>Type</th><th>Reg. no</th><th>₹/day</th><th>₹/hour</th><th>Rating</th><th>Trips</th><th>Status</th><th></th></tr>
      ${list.map((v) => `<tr>
        <td style="width:90px">${DE.vehicleArt(v)}</td>
        <td><b>${DE.esc(v.name)}</b><div class="small muted">${DE.esc(v.category)} · ${DE.esc(v.fuelType)} · ${DE.esc(v.transmission)}</div></td>
        <td>${v.type}</td><td>${DE.esc(v.registrationNumber || "")}</td>
        <td><input type="number" value="${v.pricePerDay}" data-day="${v.id}" style="width:90px"></td>
        <td><input type="number" value="${v.pricePerHour}" data-hour="${v.id}" style="width:80px"></td>
        <td>★ ${Number(v.rating).toFixed(1)}</td><td>${v.tripsCompleted}</td>
        <td><label class="check"><input type="checkbox" data-avail="${v.id}" ${v.available ? "checked" : ""}> ${v.available ? "Active" : "Maintenance"}</label></td>
        <td><button class="btn ghost sm" data-save="${v.id}">Save</button></td>
      </tr>`).join("")}
    </table></div></div>`;
  document.querySelectorAll("[data-save]").forEach((b) => (b.onclick = async () => {
    const id = b.dataset.save;
    try {
      await DE.api.put(`${A}/vehicles/${id}`, {
        pricePerDay: Number(document.querySelector(`[data-day="${id}"]`).value),
        pricePerHour: Number(document.querySelector(`[data-hour="${id}"]`).value),
      });
      DE.toast("Prices updated", "success");
    } catch (e) { DE.toast(e.message, "error"); }
  }));
  document.querySelectorAll("[data-avail]").forEach((c) => (c.onchange = async () => {
    try { await DE.api.put(`${A}/vehicles/${c.dataset.avail}`, { available: c.checked }); show("vehicles"); }
    catch (e) { DE.toast(e.message, "error"); }
  }));
  document.getElementById("add-v").onclick = addVehicle;
}

function addVehicle() {
  DE.modal(`<h3>Add vehicle</h3>
    <form id="v-form">
      <div class="grid-2">
        <div class="field"><label>Name</label><input name="name" required placeholder="Kia Seltos"></div>
        <div class="field"><label>Brand</label><input name="brand" required placeholder="Kia"></div>
        <div class="field"><label>Type</label><select name="type"><option>CAR</option><option>BIKE</option></select></div>
        <div class="field"><label>Category</label><input name="category" placeholder="SUV / Sedan / Scooter…"></div>
        <div class="field"><label>Fuel</label><select name="fuelType"><option>Petrol</option><option>Diesel</option><option>Electric</option><option>CNG</option></select></div>
        <div class="field"><label>Transmission</label><select name="transmission"><option>Manual</option><option>Automatic</option></select></div>
        <div class="field"><label>Seats</label><input name="seats" type="number" value="5"></div>
        <div class="field"><label>Year</label><input name="modelYear" type="number" value="2025"></div>
        <div class="field"><label>Price / day (₹)</label><input name="pricePerDay" type="number" required></div>
        <div class="field"><label>Deposit (₹)</label><input name="deposit" type="number" value="5000"></div>
        <div class="field"><label>Registration no</label><input name="registrationNumber" placeholder="TS09 AB 1234"></div>
        <div class="field"><label>Color</label><input name="color" type="color" value="#4f46e5" style="height:44px"></div>
      </div>
      <div class="field"><label>Engine</label><input name="engine" placeholder="1.5L Petrol"></div>
      <div class="field"><label>Mileage</label><input name="mileage" placeholder="17 km/l"></div>
      <div class="field"><label>Features (comma separated)</label><input name="features" placeholder="Sunroof,ADAS,Ventilated seats"></div>
      <div class="field"><label>Photo URL (optional)</label><input name="imageUrl" placeholder="https://…"></div>
      <div class="field"><label>Description</label><textarea name="description"></textarea></div>
      <div class="row"><button type="button" class="btn ghost" data-close>Cancel</button><button class="btn grow" type="submit">Add to fleet</button></div>
    </form>`,
  (root, close) => {
    root.querySelector("#v-form").onsubmit = async (e) => {
      e.preventDefault();
      const fd = Object.fromEntries(new FormData(e.target).entries());
      ["seats", "modelYear", "pricePerDay", "deposit"].forEach((k) => (fd[k] = Number(fd[k]) || 0));
      if (!fd.imageUrl) delete fd.imageUrl;
      if (fd.type === "BIKE") fd.seats = 2;
      try { await DE.api.post(A + "/vehicles", fd); close(); DE.toast("Vehicle added", "success"); show("vehicles"); }
      catch (err) { DE.toast(err.message, "error"); }
    };
  });
}

// ------------------------------------------------------------------ drivers
async function drivers() {
  const list = await DE.api.get(A + "/drivers");
  main().innerHTML = `<div class="card">
    <div class="row between mb"><h3 style="margin:0">Drivers (${list.length})</h3><button class="btn sm" id="add-d">+ Add driver</button></div>
    <div class="table-wrap"><table class="data">
      <tr><th>Driver</th><th>Phone</th><th>Licence</th><th>Experience</th><th>Languages</th><th>Rating</th><th>Trips</th><th>On duty</th></tr>
      ${list.map((d) => `<tr>
        <td><div class="row"><span class="avatar">${DE.esc(DE.initials(d.name))}</span><div><b>${DE.esc(d.name)}</b><div class="small muted">${DE.esc(d.email || "")}</div></div></div></td>
        <td><a href="tel:${DE.esc(d.phone)}">${DE.esc(d.phone)}</a></td><td class="small">${DE.esc(d.licenseNumber || "")}</td>
        <td>${d.experienceYears} yrs</td><td class="small">${DE.esc(d.languages || "")}</td><td>★ ${d.rating}</td><td>${d.tripsCompleted}</td>
        <td><input type="checkbox" data-duty="${d.id}" ${d.available ? "checked" : ""} style="width:auto"></td>
      </tr>`).join("")}
    </table></div></div>`;
  document.querySelectorAll("[data-duty]").forEach((c) => (c.onchange = async () => {
    try { await DE.api.put(`${A}/drivers/${c.dataset.duty}/availability`, { available: c.checked }); DE.toast("Updated", "success"); }
    catch (e) { DE.toast(e.message, "error"); }
  }));
  document.getElementById("add-d").onclick = () => DE.modal(`<h3>Add driver</h3>
    <form id="d-form">
      <div class="field"><label>Name</label><input name="name" required></div>
      <div class="grid-2">
        <div class="field"><label>Phone</label><input name="phone" required></div>
        <div class="field"><label>Email</label><input name="email" type="email"></div>
        <div class="field"><label>Licence number</label><input name="licenseNumber" required></div>
        <div class="field"><label>Experience (years)</label><input name="experienceYears" type="number" value="5"></div>
      </div>
      <div class="field"><label>Languages</label><input name="languages" value="Telugu, Hindi, English"></div>
      <div class="row"><button type="button" class="btn ghost" data-close>Cancel</button><button class="btn grow" type="submit">Add driver</button></div>
    </form>`, (root, close) => {
    root.querySelector("#d-form").onsubmit = async (e) => {
      e.preventDefault();
      const fd = Object.fromEntries(new FormData(e.target).entries());
      fd.experienceYears = Number(fd.experienceYears) || 0;
      fd.city = "Hyderabad";
      try { await DE.api.post(A + "/drivers", fd); close(); DE.toast("Driver added", "success"); show("drivers"); }
      catch (err) { DE.toast(err.message, "error"); }
    };
  });
}

// ------------------------------------------------------------------ customers
async function customers() {
  const list = await DE.api.get(A + "/customers");
  main().innerHTML = `<div class="card"><h3>Customers (${list.length})</h3>
    <div class="table-wrap"><table class="data">
      <tr><th>Customer</th><th>Mobile</th><th>Licence</th><th>Address</th><th>Emergency contact</th><th>Points</th><th>Joined</th></tr>
      ${list.map((c) => `<tr>
        <td><div class="row"><span class="avatar">${DE.esc(DE.initials(c.fullName))}</span><div><b>${DE.esc(c.fullName)}</b><div class="small muted">${DE.esc(c.email)}</div></div></div></td>
        <td><a href="tel:${DE.esc(c.phone || "")}">${DE.esc(c.phone || "-")}</a></td>
        <td class="small">${DE.esc(c.licenseNumber || "-")}<div class="muted">${c.licenseExpiry ? "till " + DE.esc(c.licenseExpiry) : ""}</div></td>
        <td class="small">${DE.esc(c.address || "-")}</td>
        <td class="small">${DE.esc(c.emergencyContactName || "-")}<div>${DE.esc(c.emergencyContactPhone || "")}</div></td>
        <td>${c.loyaltyPoints}</td><td class="small">${DE.fmtDay(c.createdAt)}</td>
      </tr>`).join("")}
    </table></div></div>`;
}

// ------------------------------------------------------------------ payments
async function payments() {
  const list = await DE.api.get(A + "/payments");
  const total = list.filter((p) => p.kind === "CHARGE" && p.status === "SUCCESS").reduce((s, p) => s + p.amount, 0);
  main().innerHTML = `<div class="card"><div class="row between mb"><h3 style="margin:0">Payments ledger</h3><b>Collected: ${DE.money(total)}</b></div>
    <div class="table-wrap"><table class="data">
      <tr><th>Date</th><th>Booking</th><th>Customer</th><th>Type</th><th>Method</th><th>Instrument</th><th>Transaction</th><th>Status</th><th>Amount</th></tr>
      ${list.map((p) => `<tr>
        <td class="small">${DE.fmtDate(p.createdAt)}</td><td>${DE.esc(p.bookingRef)}</td><td>${DE.esc(p.customerName || "")}</td>
        <td>${p.kind === "REFUND" ? '<span class="badge red">Refund</span>' : '<span class="badge blue">Charge</span>'}</td>
        <td>${DE.pretty(p.method || "")}</td><td class="small">${DE.esc(p.maskedDetails || "")}</td>
        <td class="small">${DE.esc(p.transactionId || "")}<div class="muted">${DE.esc(p.gatewayMessage || "")}</div></td>
        <td>${DE.statusBadge(p.status)}</td><td><b>${p.kind === "REFUND" ? "−" : ""}${DE.money(p.amount)}</b></td>
      </tr>`).join("")}
    </table></div></div>`;
}
