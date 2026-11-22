DE.renderLayout("dashboard");

const ref = DE.qs("ref");
const share = DE.qs("share");
let map, vehicleMarker, trailLine, plannedLine, pickupMarker, dropMarker;
let lastAlertCount = null;
let data = null;

(async function init() {
  if (!ref) { location.href = "/dashboard.html"; return; }
  if (typeof L !== "undefined") {
    map = L.map("map", { zoomControl: true }).setView([17.385, 78.4867], 12);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19, attribution: "&copy; OpenStreetMap contributors",
    }).addTo(map);
  } else {
    document.getElementById("map").innerHTML = '<div class="empty">Map library could not load (offline?). Live data is still shown on the right.</div>';
  }
  await poll(true);
  setInterval(() => poll(false), 3000);
})();

async function poll(first) {
  try {
    data = await DE.api.get(`/api/tracking/${encodeURIComponent(ref)}${share ? "?share=" + encodeURIComponent(share) : ""}`);
  } catch (e) {
    if (first) {
      document.getElementById("panel").innerHTML = `<div class="card"><div class="notice err">${DE.esc(e.message)}</div>
        ${e.status === 401 ? `<a class="btn mt" href="/auth.html?next=${encodeURIComponent(location.pathname + location.search)}">Login</a>` : ""}</div>`;
    }
    return;
  }
  drawMap(first);
  renderPanel();
  if (lastAlertCount !== null && data.alerts.length > lastAlertCount) {
    const a = data.alerts[0];
    DE.toast((a.type === "OVERSPEED" ? "⚠️ " : "🔔 ") + a.message, a.type === "OVERSPEED" ? "error" : "");
  }
  lastAlertCount = data.alerts.length;
}

function drawMap(first) {
  if (!map) return;
  const d = data;
  const icon = (html, size) => L.divIcon({ html, className: "vehicle-marker", iconSize: [size, size], iconAnchor: [size / 2, size / 2] });
  if (!plannedLine) {
    plannedLine = L.polyline(d.plannedRoute, { color: "#6366f1", weight: 4, opacity: 0.45, dashArray: "8 10" }).addTo(map);
    pickupMarker = L.marker([d.pickupLat, d.pickupLng], { icon: icon("🟢", 22) }).addTo(map).bindPopup("<b>Pickup</b><br>" + DE.esc(d.pickupLocation));
    dropMarker = L.marker([d.dropLat, d.dropLng], { icon: icon("🏁", 26) }).addTo(map).bindPopup("<b>Drop</b><br>" + DE.esc(d.dropLocation));
    trailLine = L.polyline([], { color: "#4f46e5", weight: 6, opacity: 0.9 }).addTo(map);
    map.fitBounds(plannedLine.getBounds(), { padding: [40, 40] });
  } else {
    plannedLine.setLatLngs(d.plannedRoute);
  }
  trailLine.setLatLngs(d.trail);
  const pos = d.currentLat != null ? [d.currentLat, d.currentLng] : [d.pickupLat, d.pickupLng];
  const emoji = d.vehicleType === "BIKE" ? "🏍️" : "🚗";
  if (!vehicleMarker) {
    vehicleMarker = L.marker(pos, { icon: icon(emoji, 32), zIndexOffset: 1000 }).addTo(map);
  } else {
    vehicleMarker.setLatLng(pos);
  }
  vehicleMarker.bindPopup(`<b>${DE.esc(d.vehicleName)}</b><br>${DE.esc(d.registrationNumber || "")}<br>${Math.round(d.currentSpeed)} km/h`);
  if (!first && document.getElementById("follow").checked && d.status === "ONGOING") {
    map.panTo(pos, { animate: true });
  }
}

function renderPanel() {
  const d = data;
  const live = d.status === "ONGOING";
  const over = d.currentSpeed > d.speedLimit;
  document.getElementById("t-title").innerHTML = `${live ? '<span class="live-dot"></span>' : ""}${DE.esc(d.vehicleName)} <span class="badge gray">${DE.esc(d.bookingRef)}</span> ${DE.statusBadge(d.status)}`;
  document.getElementById("t-sub").textContent = `${d.pickupLocation} → ${d.dropLocation}${d.registrationNumber ? " · " + d.registrationNumber : ""}`;

  const actions = document.getElementById("t-actions");
  if (!d.sharedView) {
    actions.innerHTML = `<button class="btn ghost sm" id="share-btn">🔗 Share live trip</button>
      ${live && !DE.isAdmin() ? '<button class="btn sm" id="end-btn">End trip</button>' : ""}`;
    document.getElementById("share-btn").onclick = shareTrip;
    const end = document.getElementById("end-btn");
    if (end) end.onclick = endTrip;
  } else {
    actions.innerHTML = '<span class="badge blue">👨‍👩‍👧 Family view</span>';
  }

  const pct = Math.round(d.progress * 100);
  const kmPct = d.kmLimit ? Math.min(100, Math.round((d.distanceTravelledKm / d.kmLimit) * 100)) : 0;
  const statusText = d.status === "CONFIRMED" ? "Trip not started yet - share the OTP at pickup to begin."
    : d.status === "COMPLETED" ? "Trip completed. Showing the route that was driven."
    : d.status === "CANCELLED" ? "This booking was cancelled."
    : pct >= 100 ? "Arrived at destination 🎉" : "On the way";

  const person = (p, role, icon) => p ? `
    <div class="person">
      <span class="avatar">${DE.esc(DE.initials(p.name))}</span>
      <div class="grow"><b>${DE.esc(p.name)}</b> <span class="muted small">${role}</span>
        ${p.rating ? `<span class="small" style="color:var(--accent)">★ ${p.rating}</span>` : ""}
        <div class="small muted">${DE.esc(p.detail || "")}</div>
        ${p.emergencyName ? `<div class="small">🚨 Emergency: ${DE.esc(p.emergencyName)} · <a href="tel:${DE.esc(p.emergencyPhone)}">${DE.esc(p.emergencyPhone)}</a></div>` : ""}
      </div>
      ${p.phone ? `<a class="btn sm" href="tel:${DE.esc(p.phone)}" title="Call ${DE.esc(p.phone)}">${icon} Call</a>` : ""}
    </div>` : "";

  document.getElementById("panel").innerHTML = `
    <div class="card">
      <div class="row between"><b>${statusText}</b><span class="small muted">${pct}%</span></div>
      <div class="progress mt" style="margin-top:8px"><div style="width:${pct}%"></div></div>
      <div class="metric-grid mt">
        <div class="metric ${over ? "alert" : ""}"><b>${Math.round(d.currentSpeed)} <small style="font-size:.8rem">km/h</small></b><span>Speed (limit ${d.speedLimit})</span></div>
        <div class="metric"><b>${d.etaMinutes != null ? d.etaMinutes + " min" : "-"}</b><span>ETA to drop</span></div>
        <div class="metric"><b>${d.distanceTravelledKm.toFixed(1)} km</b><span>Driven</span></div>
        <div class="metric"><b>${d.remainingKm.toFixed(1)} km</b><span>Remaining of ${d.routeLengthKm.toFixed(1)} km</span></div>
      </div>
      <div class="small muted mt">Free km used: ${d.distanceTravelledKm.toFixed(1)} / ${d.kmLimit} km</div>
      <div class="progress" style="margin-top:6px;height:6px"><div style="width:${kmPct}%;${kmPct > 90 ? "background:var(--danger)" : ""}"></div></div>
      ${d.tripStartedAt ? `<div class="small muted mt">Started ${DE.fmtDate(d.tripStartedAt)} · Return by ${DE.fmtDate(d.endTime)}</div>` : ""}
    </div>

    ${!d.sharedView && (live || d.status === "CONFIRMED") ? `
    <div class="card mt">
      <button class="sos-btn" id="sos">🆘 SOS</button>
      <p class="small muted center" style="margin:8px 0 12px">Sends your live location to our 24x7 emergency team${d.customer && d.customer.emergencyName ? " and alerts " + DE.esc(d.customer.emergencyName) : ""}.</p>
      <div class="sos-grid">
        <button class="btn ghost sm" data-alert="ACCIDENT">💥 Accident</button>
        <button class="btn ghost sm" data-alert="BREAKDOWN">🔧 Breakdown</button>
        <button class="btn ghost sm" data-alert="MEDICAL">🏥 Medical</button>
        <button class="btn ghost sm" data-alert="SUPPORT">💬 Support</button>
      </div>
      <div class="row mt small" style="justify-content:center"><a href="tel:112">📞 112</a> · <a href="tel:108">🚑 108</a> · <a href="tel:18001234567">🛠 1800-123-4567</a></div>
    </div>` : ""}

    <div class="card mt">
      <h3>People on this trip</h3>
      ${person(d.driver, "Driver", "📞")}
      ${person(d.customer, d.sharedView ? "Traveller" : "Customer", "📞")}
      ${!d.driver ? '<div class="small muted">Self-drive trip</div>' : ""}
    </div>

    ${!d.sharedView ? `<div class="card mt">
      <h3>Alerts & support</h3>
      ${d.alerts.length ? d.alerts.slice(0, 6).map((a) => `<div class="alert-item ${a.type}">
        <div class="row between"><b>${DE.pretty(a.type)}</b>${DE.statusBadge(a.status)}</div>
        <div>${DE.esc(a.message)}</div>
        ${a.resolutionNote ? `<div class="small" style="color:var(--success)">↳ ${DE.esc(a.resolutionNote)}</div>` : ""}
        <div class="small muted">${DE.fmtDate(a.createdAt)}</div></div>`).join("") : '<div class="small muted">No alerts. Drive safe!</div>'}
    </div>` : ""}`;

  const sos = document.getElementById("sos");
  if (sos) sos.onclick = () => raiseAlert("SOS");
  document.querySelectorAll("[data-alert]").forEach((b) => (b.onclick = () => raiseAlert(b.dataset.alert)));
}

function raiseAlert(type) {
  const titles = { SOS: "🆘 Send SOS?", ACCIDENT: "💥 Report accident", BREAKDOWN: "🔧 Report breakdown", MEDICAL: "🏥 Medical emergency", SUPPORT: "💬 Contact support" };
  DE.modal(`<h3>${titles[type]}</h3>
    <p class="muted">${type === "SOS" ? "Our emergency team will see your live location and call you immediately." : "Tell us what happened. Your live location is attached automatically."}</p>
    <div class="field"><label>Message (optional)</label><textarea id="al-msg" placeholder="${type === "BREAKDOWN" ? "e.g. flat tyre near Shamshabad toll" : "Describe the situation"}"></textarea></div>
    <div class="row"><button class="btn ghost" data-close>Cancel</button><button class="btn ${type === "SUPPORT" ? "" : "danger"} grow" id="al-go">${type === "SOS" ? "SEND SOS NOW" : "Send"}</button></div>`,
  (root, close) => {
    root.querySelector("#al-go").onclick = async (e) => {
      await DE.withBusy(e.target, async () => {
        try {
          const r = await DE.api.post(`/api/bookings/${data.bookingRef}/alerts`, {
            type, message: root.querySelector("#al-msg").value, lat: data.currentLat, lng: data.currentLng });
          close();
          const nums = Object.entries(r.emergencyNumbers).map(([k, v]) => `<li>${DE.esc(k)}: <a href="tel:${DE.esc(v)}"><b>${DE.esc(v)}</b></a></li>`).join("");
          DE.modal(`<h3>${type === "SUPPORT" ? "✅ Request received" : "🚨 Help is on the way"}</h3>
            <div class="notice ${type === "SUPPORT" ? "ok" : "err"}">${DE.esc(r.response)}</div>
            <h4 class="mt">Emergency numbers</h4><ul>${nums}</ul>
            <button class="btn block" data-close>OK</button>`);
          poll(false);
        } catch (err) { DE.toast(err.message, "error"); }
      });
    };
  });
}

function shareTrip() {
  const url = `${location.origin}/track.html?ref=${encodeURIComponent(data.bookingRef)}&share=${encodeURIComponent(data.shareCode)}`;
  DE.modal(`<h3>🔗 Share your live trip</h3>
    <p class="muted">Family and friends can follow the vehicle on the map without logging in. Personal details stay hidden.</p>
    <input id="share-url" readonly value="${DE.esc(url)}">
    <div class="row mt">
      <button class="btn grow" id="copy-share">Copy link</button>
      <a class="btn ghost" target="_blank" rel="noopener" href="https://wa.me/?text=${encodeURIComponent("Track my DriveEase trip live: " + url)}">WhatsApp</a>
    </div>`,
  (root) => {
    root.querySelector("#copy-share").onclick = () => {
      const input = root.querySelector("#share-url");
      input.select();
      try { navigator.clipboard.writeText(url); } catch (e) { document.execCommand("copy"); }
      DE.toast("Link copied", "success");
    };
  });
}

async function endTrip() {
  if (!confirm("End this trip and return the vehicle?")) return;
  try {
    const r = await DE.api.post(`/api/bookings/${data.bookingRef}/end`);
    DE.toast(`Trip completed · ${r.distanceKm.toFixed(1)} km · Final ${DE.money(r.finalAmount)}`, "success");
    setTimeout(() => (location.href = "/dashboard.html?tab=past"), 1200);
  } catch (e) { DE.toast(e.message, "error"); }
}
