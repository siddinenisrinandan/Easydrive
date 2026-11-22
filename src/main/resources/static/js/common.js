/* ======================================================================
   DriveEase - shared frontend helpers (API, session, layout, UI, AI chat)
   ====================================================================== */

const DE = (() => {
  const TOKEN_KEY = "de_token";
  const USER_KEY = "de_user";
  const ADMIN_KEY = "de_admin";

  // ---------------------------------------------------------------- storage (safe)
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* ignore */ } },
    del(k) { try { localStorage.removeItem(k); } catch (e) { /* ignore */ } },
  };
  const session = {
    get(k) { try { return sessionStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { sessionStorage.setItem(k, v); } catch (e) { /* ignore */ } },
    del(k) { try { sessionStorage.removeItem(k); } catch (e) { /* ignore */ } },
  };

  // ---------------------------------------------------------------- API
  async function request(method, url, body, extraHeaders) {
    const headers = { "Content-Type": "application/json", ...(extraHeaders || {}) };
    const token = store.get(TOKEN_KEY);
    if (token) headers["X-Auth-Token"] = token;
    const admin = store.get(ADMIN_KEY);
    if (admin) headers["X-Admin-Token"] = admin;
    let res;
    try {
      res = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch (e) {
      throw new Error("Cannot reach the server. Is the Spring Boot app running?");
    }
    let data = null;
    const text = await res.text();
    if (text) { try { data = JSON.parse(text); } catch (e) { data = text; } }
    if (!res.ok) {
      const msg = (data && data.error) || `Request failed (${res.status})`;
      const err = new Error(msg);
      err.status = res.status;
      if (res.status === 401 && url.indexOf("/api/admin") === -1 && token && msg.indexOf("expired") >= 0) {
        clearSession();
      }
      throw err;
    }
    return data;
  }
  const api = {
    get: (u, h) => request("GET", u, undefined, h),
    post: (u, b, h) => request("POST", u, b === undefined ? {} : b, h),
    put: (u, b, h) => request("PUT", u, b, h),
    del: (u, h) => request("DELETE", u, undefined, h),
  };

  // ---------------------------------------------------------------- session
  function setSession(token, user) { store.set(TOKEN_KEY, token); store.set(USER_KEY, JSON.stringify(user)); }
  function clearSession() { store.del(TOKEN_KEY); store.del(USER_KEY); }
  function user() { try { return JSON.parse(store.get(USER_KEY)); } catch (e) { return null; } }
  function loggedIn() { return !!store.get(TOKEN_KEY); }
  function setAdmin(token) { if (token) store.set(ADMIN_KEY, token); else store.del(ADMIN_KEY); }
  function isAdmin() { return !!store.get(ADMIN_KEY); }
  function requireLogin() {
    if (!loggedIn()) {
      location.href = "/auth.html?next=" + encodeURIComponent(location.pathname + location.search);
      return false;
    }
    return true;
  }
  async function logout() {
    try { await api.post("/api/auth/logout"); } catch (e) { /* ignore */ }
    clearSession();
    location.href = "/";
  }

  // ---------------------------------------------------------------- formatting
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const money = (n) => "₹" + Math.round(Number(n) || 0).toLocaleString("en-IN");
  const money2 = (n) => (n < 0 ? "−" : "") + "₹" + Math.abs(Number(n) || 0).toLocaleString("en-IN", { maximumFractionDigits: 2 });
  /** Server LocalDateTime strings may carry 6-9 fraction digits; trim to milliseconds for every browser. */
  const parseDate = (s) => (s instanceof Date ? s : new Date(String(s).replace(/(\.\d{3})\d+/, "$1")));
  const fmtDate = (s) => {
    if (!s) return "-";
    const d = parseDate(s);
    return d.toLocaleString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
  };
  const fmtDay = (s) => (s ? parseDate(s).toLocaleDateString("en-IN", { day: "2-digit", month: "short" }) : "-");
  const pretty = (s) => String(s || "").toLowerCase().replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase());
  const qs = (k) => new URLSearchParams(location.search).get(k);
  const initials = (name) => String(name || "?").trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join("").toUpperCase();
  /** value for <input type="datetime-local"> */
  const toLocalInput = (d) => {
    const p = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
  };
  const statusBadge = (s) => {
    const cls = { CONFIRMED: "blue", ONGOING: "green", COMPLETED: "gray", CANCELLED: "red", PENDING_PAYMENT: "amber",
      SUCCESS: "green", PENDING: "amber", FAILED: "red", REFUNDED: "gray", PARTIALLY_REFUNDED: "gray",
      OPEN: "red", IN_PROGRESS: "amber", RESOLVED: "green" }[s] || "gray";
    return `<span class="badge ${cls}">${esc(pretty(s))}</span>`;
  };
  const stars = (n) => "★★★★★".slice(0, Math.round(n)) + "☆☆☆☆☆".slice(0, 5 - Math.round(n));

  /** tiny, safe markdown: **bold**, *italic*, "- " lists, "1. " lists, line breaks */
  function md(text) {
    const lines = esc(text).split(/\n/);
    let html = "", list = null;
    const inline = (l) => l.replace(/\*\*(.+?)\*\*/g, "<b>$1</b>").replace(/(^|[\s(])\*(?!\s)(.+?)\*/g, "$1<i>$2</i>");
    const close = () => { if (list) { html += `</${list}>`; list = null; } };
    for (const raw of lines) {
      const l = raw.trimEnd();
      let m;
      if ((m = l.match(/^\s*[-•]\s+(.*)/))) {
        if (list !== "ul") { close(); html += "<ul>"; list = "ul"; }
        html += `<li>${inline(m[1])}</li>`;
      } else if ((m = l.match(/^\s*\d+\.\s+(.*)/))) {
        if (list !== "ol") { close(); html += "<ol>"; list = "ol"; }
        html += `<li>${inline(m[1])}</li>`;
      } else if (l.trim() === "") {
        close();
      } else {
        close();
        html += `<p>${inline(l)}</p>`;
      }
    }
    close();
    return html;
  }

  // ---------------------------------------------------------------- vehicle illustrations
  function shade(hex, amt) {
    let c = String(hex || "#4f46e5").replace("#", "");
    if (c.length === 3) c = c.split("").map((x) => x + x).join("");
    const n = parseInt(c, 16);
    const r = Math.max(0, Math.min(255, (n >> 16) + amt));
    const g = Math.max(0, Math.min(255, ((n >> 8) & 255) + amt));
    const b = Math.max(0, Math.min(255, (n & 255) + amt));
    return "#" + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
  }
  function vehicleArt(v) {
    if (v && v.imageUrl) return `<img src="${esc(v.imageUrl)}" alt="${esc(v.name)}" loading="lazy">`;
    const color = (v && v.color) || "#4f46e5";
    const dark = shade(color, -45);
    const stroke = 'stroke="rgba(15,23,42,.18)" stroke-width="1.5"';
    const shadow = '<ellipse cx="120" cy="106" rx="98" ry="6" fill="rgba(15,23,42,.13)"/>';
    const wheel = (x, r) => `<circle cx="${x}" cy="${104 - r}" r="${r}" fill="#111827"/><circle cx="${x}" cy="${104 - r}" r="${r * 0.45}" fill="#cbd5e1"/><circle cx="${x}" cy="${104 - r}" r="${r * 0.15}" fill="#475569"/>`;
    if (v && v.type === "BIKE") {
      if (v.category === "Scooter") {
        return `<svg viewBox="0 0 240 120" xmlns="http://www.w3.org/2000/svg">${shadow}
          ${wheel(62, 17)}${wheel(182, 17)}
          <path d="M40 84 Q42 60 74 56 L118 56 Q126 74 148 74 L160 40 L172 38 L194 86 L176 88 Q170 76 158 80 L150 88 L84 88 Q80 72 62 72 Q46 74 44 88 Z" fill="${color}" ${stroke}/>
          <path d="M60 52 Q84 42 116 48 L114 58 L66 60 Z" fill="#1f2937"/>
          <path d="M156 30 L186 24" stroke="#334155" stroke-width="5" stroke-linecap="round"/>
          <path d="M166 34 L174 30" stroke="#334155" stroke-width="5"/>
          <circle cx="186" cy="44" r="5" fill="#fde68a"/>
          <rect x="88" y="80" width="56" height="6" rx="3" fill="${dark}"/>
        </svg>`;
      }
      return `<svg viewBox="0 0 240 120" xmlns="http://www.w3.org/2000/svg">${shadow}
        <circle cx="60" cy="80" r="23" fill="none" stroke="#111827" stroke-width="8"/><circle cx="60" cy="80" r="5" fill="#94a3b8"/>
        <circle cx="182" cy="80" r="23" fill="none" stroke="#111827" stroke-width="8"/><circle cx="182" cy="80" r="5" fill="#94a3b8"/>
        <path d="M60 80 L96 58 L146 58 L182 80" stroke="#334155" stroke-width="5" fill="none" stroke-linejoin="round"/>
        <path d="M60 80 L82 56" stroke="#334155" stroke-width="5"/>
        <rect x="104" y="60" width="32" height="22" rx="5" fill="#475569"/>
        <path d="M118 82 L150 86" stroke="#94a3b8" stroke-width="5" stroke-linecap="round"/>
        <path d="M96 52 Q106 34 136 36 L152 44 Q148 60 126 60 L100 60 Z" fill="${color}" ${stroke}/>
        <path d="M66 50 L100 46 L100 56 L72 58 Z" fill="#1f2937"/>
        <path d="M182 80 L160 34 L148 30" stroke="#334155" stroke-width="5" fill="none" stroke-linecap="round"/>
        <circle cx="166" cy="42" r="6" fill="#fde68a" stroke="#334155" stroke-width="2"/>
        <path d="M150 44 L162 40 L164 52 Z" fill="${dark}"/>
      </svg>`;
    }
    const tall = v && ["SUV", "MUV", "Off-Road"].includes(v.category);
    const body = tall
      ? `<path d="M16 88 L16 66 Q18 56 34 54 L64 50 L84 26 Q88 22 96 22 L176 22 Q186 22 192 30 L208 52 L218 56 Q226 60 226 72 L226 88 Q226 94 220 94 L22 94 Q16 94 16 88 Z" fill="${color}" ${stroke}/>
         <path d="M90 30 L130 30 L130 52 L74 52 Z" fill="#bfdbfe" opacity=".92"/>
         <path d="M138 30 L178 30 Q184 30 188 36 L200 52 L138 52 Z" fill="#bfdbfe" opacity=".92"/>
         <rect x="96" y="16" width="70" height="5" rx="2" fill="${dark}"/>`
      : `<path d="M16 84 Q18 66 42 62 L78 56 Q98 34 126 32 L158 32 Q184 34 202 56 L216 60 Q228 64 226 84 L226 90 Q226 94 220 94 L22 94 Q16 94 16 90 Z" fill="${color}" ${stroke}/>
         <path d="M90 56 Q104 40 126 39 L138 39 L138 56 Z" fill="#bfdbfe" opacity=".92"/>
         <path d="M146 39 L158 39 Q178 41 192 56 L146 56 Z" fill="#bfdbfe" opacity=".92"/>`;
    return `<svg viewBox="0 0 240 120" xmlns="http://www.w3.org/2000/svg">${shadow}${body}
      <path d="M20 76 L224 76" stroke="${dark}" stroke-width="2" opacity=".5"/>
      <rect x="214" y="64" width="11" height="7" rx="3" fill="#fde68a"/>
      <rect x="16" y="66" width="8" height="7" rx="2" fill="#ef4444"/>
      ${wheel(64, 17)}${wheel(180, 17)}
    </svg>`;
  }

  // ---------------------------------------------------------------- layout
  function renderLayout(active) {
    const header = document.getElementById("site-header");
    if (header) {
      const u = user();
      header.className = "site-header";
      header.innerHTML = `
        <div class="container nav">
          <a href="/" class="brand"><span class="logo">🚗</span><span>Drive<b>Ease</b></span></a>
          <button class="nav-toggle" aria-label="Menu" onclick="document.querySelector('.nav-links').classList.toggle('open')">☰</button>
          <nav class="nav-links">
            <a href="/" class="${active === "home" ? "active" : ""}">Home</a>
            <a href="/vehicles.html?type=CAR" class="${active === "cars" ? "active" : ""}">Cars</a>
            <a href="/vehicles.html?type=BIKE" class="${active === "bikes" ? "active" : ""}">Bikes</a>
            <a href="/dashboard.html" class="${active === "dashboard" ? "active" : ""}">My Bookings</a>
            <a href="/admin.html" class="${active === "admin" ? "active" : ""}">Admin</a>
            ${u ? `<a href="/dashboard.html?tab=profile" class="user-chip"><span class="avatar">${esc(initials(u.fullName))}</span>${esc(u.fullName.split(" ")[0])}</a>
                   <a href="#" id="logout-link">Logout</a>`
                : `<a href="/auth.html" class="btn accent sm" style="color:var(--navy)">Login / Sign up</a>`}
          </nav>
        </div>`;
      const lo = document.getElementById("logout-link");
      if (lo) lo.addEventListener("click", (e) => { e.preventDefault(); logout(); });
    }
    const footer = document.getElementById("site-footer");
    if (footer) {
      footer.className = "site-footer";
      footer.innerHTML = `
        <div class="container">
          <div class="footer-grid">
            <div>
              <a href="/" class="brand"><span class="logo">🚗</span><span>Drive<b>Ease</b></span></a>
              <p class="mt">Self-drive and chauffeur-driven cars & bikes across Hyderabad, Vijayawada, Vizag, Tirupati and Bengaluru.
              Transparent pricing, insurance on every trip, live GPS tracking and 24x7 roadside assistance.</p>
            </div>
            <div><h4>Rent</h4><a href="/vehicles.html?type=CAR">Cars</a><a href="/vehicles.html?type=BIKE">Bikes & Scooters</a><a href="/vehicles.html?fuel=Electric">Electric vehicles</a><a href="/vehicles.html?category=Luxury">Luxury</a></div>
            <div><h4>Account</h4><a href="/dashboard.html">My bookings</a><a href="/dashboard.html?tab=payments">Saved payments</a><a href="/dashboard.html?tab=profile">Profile & KYC</a><a href="/admin.html">Admin console</a></div>
            <div><h4>24x7 Help</h4><a href="tel:112">Emergency: 112</a><a href="tel:18001234567">Roadside: 1800-123-4567</a><a href="#" onclick="DE.openChat();return false;">Ask DriveEase AI</a></div>
          </div>
          <div class="copyright"><span>© ${new Date().getFullYear()} DriveEase Rentals. Built with Java Spring Boot.</span><span>Demo project - payments are simulated.</span></div>
        </div>`;
    }
    if (!document.getElementById("toast-root")) {
      const t = document.createElement("div");
      t.id = "toast-root";
      document.body.appendChild(t);
    }
    initChat();
  }

  // ---------------------------------------------------------------- UI helpers
  function toast(msg, type) {
    const root = document.getElementById("toast-root");
    if (!root) return alert(msg);
    const el = document.createElement("div");
    el.className = "toast " + (type || "");
    el.textContent = msg;
    root.appendChild(el);
    setTimeout(() => el.remove(), 4200);
  }
  function modal(html, onMount) {
    const back = document.createElement("div");
    back.className = "modal-backdrop";
    back.innerHTML = `<div class="modal">${html}</div>`;
    const close = () => back.remove();
    back.addEventListener("click", (e) => { if (e.target === back) close(); });
    document.body.appendChild(back);
    back.querySelectorAll("[data-close]").forEach((b) => b.addEventListener("click", close));
    if (onMount) onMount(back, close);
    return close;
  }
  async function withBusy(btn, fn) {
    const old = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner" style="width:16px;height:16px;border-width:2px"></span>';
    try { return await fn(); } finally { btn.disabled = false; btn.innerHTML = old; }
  }
  function vehicleCard(v, extraQuery) {
    const href = `/vehicle.html?id=${v.id}${extraQuery || ""}`;
    return `<div class="card vehicle-card ${v.available ? "" : "unavailable"}" onclick="location.href='${href}'">
      <div class="art">${vehicleArt(v)}<span class="badge">${esc(v.category)}</span><span class="rating">★ ${Number(v.rating).toFixed(1)}</span></div>
      <div class="body">
        <h3>${esc(v.name)}</h3>
        <div class="specs">
          <span class="spec">${v.type === "CAR" ? "👥 " + v.seats + " seats" : "🏍 " + esc(v.engine)}</span>
          <span class="spec">⛽ ${esc(v.fuelType)}</span>
          <span class="spec">⚙ ${esc(v.transmission)}</span>
        </div>
        <div class="foot">
          <div class="price">${money(v.pricePerDay)}<small>/day</small></div>
          ${v.available ? `<a class="btn sm" href="${href}" onclick="event.stopPropagation()">Book</a>` : `<span class="badge red">Maintenance</span>`}
        </div>
      </div>
    </div>`;
  }

  // ---------------------------------------------------------------- AI chat widget
  const CHAT_KEY = "de_chat";
  let chatHistory = [];
  let chatBusy = false;

  function initChat() {
    if (document.getElementById("chat-fab")) return;
    try { chatHistory = JSON.parse(session.get(CHAT_KEY)) || []; } catch (e) { chatHistory = []; }
    const fab = document.createElement("button");
    fab.id = "chat-fab";
    fab.className = "chat-fab";
    fab.title = "Ask DriveEase AI";
    fab.textContent = "💬";
    fab.addEventListener("click", toggleChat);
    document.body.appendChild(fab);
  }
  function openChat() { if (!document.getElementById("chat-panel")) toggleChat(); }
  function toggleChat() {
    const existing = document.getElementById("chat-panel");
    if (existing) { existing.remove(); return; }
    const panel = document.createElement("div");
    panel.id = "chat-panel";
    panel.className = "chat-panel";
    panel.innerHTML = `
      <div class="chat-head">
        <div class="bot">🤖</div>
        <div><b>DriveEase AI</b><small id="chat-mode">Your smart rental assistant</small></div>
        <button title="Clear chat" id="chat-clear" style="font-size:1rem">⟲</button>
        <button title="Close" id="chat-close" style="margin-left:4px">×</button>
      </div>
      <div class="chat-body" id="chat-body"></div>
      <form class="chat-input" id="chat-form">
        <input id="chat-text" autocomplete="off" placeholder="Ask anything… e.g. SUV for 6 people under 5000">
        <button class="btn" type="submit">➤</button>
      </form>`;
    document.body.appendChild(panel);
    panel.querySelector("#chat-close").onclick = () => panel.remove();
    panel.querySelector("#chat-clear").onclick = () => { chatHistory = []; session.del(CHAT_KEY); renderChat(); };
    panel.querySelector("#chat-form").onsubmit = (e) => { e.preventDefault(); sendChat(panel.querySelector("#chat-text").value); };
    api.get("/api/ai/status").then((s) => {
      const el = document.getElementById("chat-mode");
      if (el) el.textContent = s.mode === "claude" ? "Powered by Claude AI · any language" : "Smart offline assistant · English & Telugu words";
    }).catch(() => {});
    renderChat();
    panel.querySelector("#chat-text").focus();
  }
  function renderChat() {
    const body = document.getElementById("chat-body");
    if (!body) return;
    let html = "";
    if (chatHistory.length === 0) {
      html += `<div class="msg bot">${md("Hi! 👋 I'm **DriveEase AI**. Tell me about your trip and I'll find the perfect car or bike, calculate the price, explain insurance, or help in an emergency.\n\nనమస్కారం! మీ ప్రయాణానికి సరైన వాహనం చెప్తాను.")}</div>
        <div class="quick">${["Suggest a family car under 4000", "Bike for Araku trip", "Price of Creta for 3 days", "Insurance plans", "Show offers"].map((q) => `<button data-q="${esc(q)}">${esc(q)}</button>`).join("")}</div>`;
    }
    chatHistory.forEach((m, i) => {
      html += `<div class="msg ${m.role === "user" ? "user" : "bot"}">${m.role === "user" ? esc(m.text) : md(m.text)}</div>`;
      if (m.vehicles && m.vehicles.length) {
        html += `<div class="chat-vehicles">${m.vehicles.map((v) => `<div class="chat-vehicle" onclick="location.href='/vehicle.html?id=${v.id}'">${vehicleArt(v)}<b>${esc(v.name)}</b>${money(v.pricePerDay)}/day</div>`).join("")}</div>`;
      }
      if (i === chatHistory.length - 1 && m.quick && m.quick.length) {
        html += `<div class="quick">${m.quick.map((q) => `<button data-q="${esc(q)}">${esc(q)}</button>`).join("")}</div>`;
      }
    });
    if (chatBusy) html += `<div class="msg bot typing"><span></span><span></span><span></span></div>`;
    body.innerHTML = html;
    body.querySelectorAll("[data-q]").forEach((b) => b.addEventListener("click", () => sendChat(b.dataset.q)));
    body.scrollTop = body.scrollHeight;
  }
  async function sendChat(text) {
    text = String(text || "").trim();
    if (!text || chatBusy) return;
    const input = document.getElementById("chat-text");
    if (input) input.value = "";
    const history = chatHistory.map((m) => ({ role: m.role, text: m.text }));
    chatHistory.push({ role: "user", text });
    chatBusy = true;
    renderChat();
    try {
      const r = await api.post("/api/ai/chat", { message: text, history });
      chatHistory.push({ role: "assistant", text: r.reply, vehicles: r.vehicles || [], quick: r.quickReplies || [] });
    } catch (e) {
      chatHistory.push({ role: "assistant", text: "Sorry, I couldn't respond: " + e.message });
    }
    chatBusy = false;
    if (chatHistory.length > 30) chatHistory = chatHistory.slice(-30);
    session.set(CHAT_KEY, JSON.stringify(chatHistory));
    renderChat();
  }

  return {
    api, store, session, setSession, clearSession, user, loggedIn, setAdmin, isAdmin, requireLogin, logout,
    esc, money, money2, parseDate, fmtDate, fmtDay, pretty, qs, initials, toLocalInput, statusBadge, stars, md,
    vehicleArt, vehicleCard, renderLayout, toast, modal, withBusy, openChat, sendChat,
  };
})();
