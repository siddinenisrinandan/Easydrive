const state = {
  type: DE.qs("type") || "",
  category: DE.qs("category") || "",
  fuel: DE.qs("fuel") || "",
  transmission: "",
  seats: DE.qs("seats") || "",
  maxPrice: 10000,
  q: "",
  sort: "popular",
  start: DE.qs("start") || "",
  end: DE.qs("end") || "",
  location: DE.qs("location") || "",
};
DE.renderLayout(state.type === "BIKE" ? "bikes" : state.type === "CAR" ? "cars" : "");

function bindChips(groupId, key) {
  const group = document.getElementById(groupId);
  const sync = () => group.querySelectorAll(".chip").forEach((c) => c.classList.toggle("active", c.dataset.v === state[key]));
  group.addEventListener("click", (e) => {
    const chip = e.target.closest(".chip");
    if (!chip) return;
    state[key] = chip.dataset.v;
    sync();
    load();
  });
  sync();
  return sync;
}

let debounce;
async function load() {
  clearTimeout(debounce);
  debounce = setTimeout(async () => {
    const p = new URLSearchParams();
    if (state.type) p.set("type", state.type);
    if (state.category) p.set("category", state.category);
    if (state.fuel) p.set("fuel", state.fuel);
    if (state.transmission) p.set("transmission", state.transmission);
    if (state.seats) p.set("seats", state.seats);
    if (state.maxPrice < 10000) p.set("maxPrice", state.maxPrice);
    if (state.q) p.set("q", state.q);
    p.set("sort", state.sort);
    const datesOk = state.start && state.end && new Date(state.end) > new Date(state.start);
    if (datesOk) { p.set("start", state.start + ":00"); p.set("end", state.end + ":00"); }

    const note = document.getElementById("avail-note");
    if (datesOk) {
      note.classList.remove("hidden");
      note.innerHTML = `Showing vehicles free from <b>${DE.fmtDate(state.start)}</b> to <b>${DE.fmtDate(state.end)}</b>${state.location ? " at <b>" + DE.esc(state.location) + "</b>" : ""}.`;
    } else {
      note.classList.add("hidden");
    }

    const title = state.type === "CAR" ? "Cars for rent" : state.type === "BIKE" ? "Bikes & scooters for rent" : "Our fleet";
    document.getElementById("page-title").textContent = title;
    try {
      const list = await DE.api.get("/api/vehicles?" + p.toString());
      const extra = datesOk
        ? `&start=${encodeURIComponent(state.start)}&end=${encodeURIComponent(state.end)}${state.location ? "&location=" + encodeURIComponent(state.location) : ""}`
        : state.location ? "&location=" + encodeURIComponent(state.location) : "";
      document.getElementById("result-count").textContent = `${list.length} vehicle${list.length === 1 ? "" : "s"} found`;
      document.getElementById("grid").innerHTML = list.length
        ? list.map((v) => DE.vehicleCard(v, extra)).join("")
        : `<div class="card empty" style="grid-column:1/-1"><div class="big">🔍</div><h3>No vehicles match</h3><p>Try removing a filter or ask the AI assistant for suggestions.</p><button class="btn" onclick="DE.openChat()">Ask DriveEase AI</button></div>`;
    } catch (e) {
      DE.toast(e.message, "error");
    }
  }, 150);
}

(async function init() {
  const syncType = bindChips("f-type", "type");
  bindChips("f-fuel", "fuel");
  bindChips("f-trans", "transmission");
  try {
    const meta = await DE.api.get("/api/meta");
    document.getElementById("f-category").innerHTML = `<button class="chip" data-v="">All</button>` +
      meta.categories.map((c) => `<button class="chip" data-v="${DE.esc(c)}">${DE.esc(c)}</button>`).join("");
    bindChips("f-category", "category");
  } catch (e) { /* categories optional */ }

  const seats = document.getElementById("f-seats");
  seats.value = state.seats;
  seats.onchange = () => { state.seats = seats.value; load(); };

  const price = document.getElementById("f-price");
  const label = document.getElementById("price-label");
  const showPrice = () => { label.textContent = state.maxPrice >= 10000 ? "Any" : DE.money(state.maxPrice); };
  price.oninput = () => { state.maxPrice = Number(price.value); showPrice(); load(); };
  showPrice();

  const q = document.getElementById("f-q");
  q.oninput = () => { state.q = q.value.trim(); load(); };
  const sort = document.getElementById("f-sort");
  sort.onchange = () => { state.sort = sort.value; load(); };

  const s = document.getElementById("f-start"), e = document.getElementById("f-end");
  s.value = state.start; e.value = state.end;
  s.onchange = () => { state.start = s.value; load(); };
  e.onchange = () => { state.end = e.value; load(); };

  document.getElementById("f-reset").onclick = () => {
    Object.assign(state, { type: "", category: "", fuel: "", transmission: "", seats: "", maxPrice: 10000, q: "", start: "", end: "" });
    price.value = 10000; seats.value = ""; q.value = ""; s.value = ""; e.value = "";
    showPrice();
    document.querySelectorAll(".chip").forEach((c) => c.classList.toggle("active", c.dataset.v === ""));
    syncType();
    load();
  };
  load();
})();
