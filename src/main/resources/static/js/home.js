DE.renderLayout("home");

(async function home() {
  document.getElementById("hero-art").innerHTML = DE.vehicleArt({ type: "CAR", category: "SUV", color: "#f59e0b" });

  // default search dates: tomorrow 9 AM -> day after 9 AM
  const start = new Date(); start.setDate(start.getDate() + 1); start.setHours(9, 0, 0, 0);
  const end = new Date(start); end.setDate(end.getDate() + 1);
  document.getElementById("s-start").value = DE.toLocalInput(start);
  document.getElementById("s-end").value = DE.toLocalInput(end);

  let type = "CAR";
  document.querySelectorAll("#type-switch button").forEach((b) => b.addEventListener("click", () => {
    document.querySelectorAll("#type-switch button").forEach((x) => x.classList.remove("active"));
    b.classList.add("active");
    type = b.dataset.type;
  }));

  document.getElementById("search-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const s = document.getElementById("s-start").value;
    const en = document.getElementById("s-end").value;
    if (new Date(en) <= new Date(s)) { DE.toast("Return time must be after pickup time", "error"); return; }
    const p = new URLSearchParams({ type, start: s, end: en, location: document.getElementById("s-location").value });
    const seats = document.getElementById("s-seats").value;
    if (seats) p.set("seats", seats);
    location.href = "/vehicles.html?" + p.toString();
  });

  try {
    const [meta, vehicles] = await Promise.all([DE.api.get("/api/meta"), DE.api.get("/api/vehicles?sort=popular")]);
    document.getElementById("s-location").innerHTML = meta.locations
      .map((l) => `<option value="${DE.esc(l.name)}">${DE.esc(l.name)} · ${DE.esc(l.city)}</option>`).join("");

    const cars = vehicles.filter((v) => v.type === "CAR").slice(0, 4);
    const bikes = vehicles.filter((v) => v.type === "BIKE").slice(0, 4);
    document.getElementById("featured-cars").innerHTML = cars.map((v) => DE.vehicleCard(v)).join("");
    document.getElementById("featured-bikes").innerHTML = bikes.map((v) => DE.vehicleCard(v)).join("");

    document.getElementById("plans").innerHTML = meta.insurancePlans.map((p, i) => `
      <div class="card" style="${i === 2 ? "border:2px solid var(--primary)" : ""}">
        ${i === 2 ? '<span class="badge">⭐ Most popular</span>' : i === 1 ? '<span class="badge green">Recommended</span>' : '<span class="badge gray">Included</span>'}
        <h3 class="mt">${DE.esc(p.label)}</h3>
        <div class="price">${p.carPerDay ? DE.money(p.carPerDay) : "Free"}<small>${p.carPerDay ? "/day car · " + DE.money(p.bikePerDay) + "/day bike" : ""}</small></div>
        <p class="muted">${DE.esc(p.description)}</p>
        <div class="notice ${p.carDeductible === 0 ? "ok" : ""}">You pay at most <b>${DE.money(p.carDeductible)}</b> (car) / <b>${DE.money(p.bikeDeductible)}</b> (bike) if damaged.</div>
      </div>`).join("");

    const icons = ["🎁", "🌅", "🏍", "⚡", "🛣"];
    document.getElementById("offers").innerHTML = meta.coupons.map((c, i) => `
      <div class="card">
        <div style="font-size:2rem">${icons[i % icons.length]}</div>
        <h3 class="mt">${DE.esc(c.code)}</h3>
        <p class="muted">${DE.esc(c.description)}</p>
        <button class="btn ghost sm" onclick="navigator.clipboard && navigator.clipboard.writeText('${DE.esc(c.code)}'); DE.toast('Copied ${DE.esc(c.code)}','success')">Copy code</button>
      </div>`).join("");
  } catch (e) {
    DE.toast(e.message, "error");
  }
})();
