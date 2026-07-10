/* ============================================================
   گزارش منتورینگ — آکادمی سبحان صمدی
   app.js — منطق کامل برنامه (وانیلا جاوااسکریپت، بدون وابستگی بیرونی)
============================================================ */
"use strict";

/* ---------------------------------------------------------
   1) STORAGE LAYER — localStorage wrapper, namespaced
--------------------------------------------------------- */
const STORE_KEYS = {
  mentors:"msr_mentors", reports:"msr_reports", leaves:"msr_leaves",
  projects:"msr_projects", creds:"msr_credentials", session:"msr_session",
  notifs:"msr_notifs"
};
function lsGet(key, fallback){
  try{
    const v = localStorage.getItem(key);
    if(v === null || v === undefined) return fallback;
    return JSON.parse(v);
  }catch(e){
    console.warn("داده ذخیره‌شده نامعتبر بود، مقدار پیش‌فرض جایگزین شد:", key, e);
    try{ localStorage.removeItem(key); }catch(_){}
    return fallback;
  }
}
function lsSet(key, val){
  try{ localStorage.setItem(key, JSON.stringify(val)); }
  catch(e){ showToast("<i class='fas fa-triangle-exclamation'></i> حافظه پر است یا غیرقابل‌نوشتن است"); }
}

function loadAll(){
  DB.mentors  = safeArray(lsGet(STORE_KEYS.mentors, null), SEED_MENTORS);
  DB.reports  = safeArray(lsGet(STORE_KEYS.reports, null), []);
  DB.leaves   = safeArray(lsGet(STORE_KEYS.leaves, null), []);
  DB.projects = safeArray(lsGet(STORE_KEYS.projects, null), SEED_PROJECTS);
  DB.notifs   = safeArray(lsGet(STORE_KEYS.notifs, null), []);
  const creds = lsGet(STORE_KEYS.creds, null);
  DB.creds = (creds && typeof creds==="object" && creds.viewerPass && creds.adminPass)
    ? creds : Object.assign({}, DEFAULT_CREDENTIALS);
  saveAll();
}
function safeArray(v, fallback){ return Array.isArray(v) ? v : fallback.slice(); }
function saveAll(){
  lsSet(STORE_KEYS.mentors, DB.mentors);
  lsSet(STORE_KEYS.reports, DB.reports);
  lsSet(STORE_KEYS.leaves, DB.leaves);
  lsSet(STORE_KEYS.projects, DB.projects);
  lsSet(STORE_KEYS.creds, DB.creds);
  lsSet(STORE_KEYS.notifs, DB.notifs);
}
const DB = { mentors:[], reports:[], leaves:[], projects:[], creds:{}, notifs:[] };

function uid(prefix){ return prefix + "_" + Date.now().toString(36) + Math.random().toString(36).slice(2,7); }

/* ---------------------------------------------------------
   2) UTILITIES
--------------------------------------------------------- */
const $ = id => document.getElementById(id);
const faDigits = n => String(n).replace(/\d/g, d => "۰۱۲۳۴۵۶۷۸۹"[d]);
function fmtDate(ts){
  const d = new Date(ts);
  return faDigits(d.toLocaleDateString('fa-IR', {year:'numeric',month:'long',day:'numeric'}));
}
function fmtDateShort(ts){
  const d = new Date(ts);
  return faDigits(d.toLocaleDateString('fa-IR', {month:'short',day:'numeric'}));
}
function todayISO(){ return new Date().toISOString().slice(0,10); }
function showToast(msg, dur=2600){
  const t = $("toast"); t.innerHTML = msg; t.classList.add("show");
  clearTimeout(t._timer); t._timer = setTimeout(()=>t.classList.remove("show"), dur);
}
function openModal(id){ $(id).classList.add("show"); }
function closeModal(id){ $(id).classList.remove("show"); }
function escapeHtml(s){ return (s||"").replace(/[&<>"']/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function nl2br(s){ return escapeHtml(s||"").replace(/\n/g,"<br>"); }

function askConfirm(title, body, onOk){
  $("confirm-title").textContent = title;
  $("confirm-body").textContent = body;
  const btn = $("confirm-ok-btn");
  const clone = btn.cloneNode(true); btn.parentNode.replaceChild(clone, btn);
  clone.addEventListener("click", ()=>{ closeModal("modal-confirm"); onOk(); });
  openModal("modal-confirm");
}

function genericModal(html, maxWidth){
  $("modal-generic-body").style.maxWidth = maxWidth || "480px";
  $("modal-generic-body").innerHTML = html;
  openModal("modal-generic");
}

/* ---------------------------------------------------------
   3) AUTH + SESSION
--------------------------------------------------------- */
let session = { role:null };

function doLogin(){
  const pass = $("login-pass").value;
  $("login-err").textContent = "";
  if(!pass){ $("login-err").textContent = "رمز عبور را وارد کنید"; return; }
  if(pass === DB.creds.viewerPass){
    session = { role:"viewer" }; lsSet(STORE_KEYS.session, session);
    enterApp();
  } else if(pass === DB.creds.adminPass){
    // برای راحتی، اگر رمز مدیر هم در صفحه عادی وارد شود ورود انجام می‌گیرد
    session = { role:"admin" }; lsSet(STORE_KEYS.session, session);
    enterApp();
  } else {
    $("login-err").textContent = "رمز عبور اشتباه است";
    $("login-pass").value = "";
  }
}

/* مکانیزم پنهان فعال‌سازی پنل مدیر: ۵ ضربه سریع روی لوگو */
let tapCount = 0, tapTimer = null;
function setupHiddenAdminGesture(){
  const logo = $("logo-tap");
  let pressTimer = null;
  logo.addEventListener("click", ()=>{
    tapCount++;
    clearTimeout(tapTimer);
    if(tapCount >= 5){
      tapCount = 0;
      navigator.vibrate && navigator.vibrate(40);
      $("admin-gate-err").textContent = "";
      $("admin-gate-pass").value = "";
      openModal("modal-admin-gate");
      return;
    }
    tapTimer = setTimeout(()=>{ tapCount = 0; }, 1400);
  });
  // نگه‌داشتن طولانی لوگو هم همان مسیر مخفی را باز می‌کند
  logo.addEventListener("touchstart", ()=>{ pressTimer = setTimeout(()=>{
    tapCount = 0; navigator.vibrate && navigator.vibrate(40);
    $("admin-gate-err").textContent = ""; $("admin-gate-pass").value = "";
    openModal("modal-admin-gate");
  }, 900); }, {passive:true});
  logo.addEventListener("touchend", ()=>clearTimeout(pressTimer));
  logo.addEventListener("mousedown", ()=>{ pressTimer = setTimeout(()=>{
    tapCount = 0; $("admin-gate-err").textContent = ""; $("admin-gate-pass").value = "";
    openModal("modal-admin-gate");
  }, 900); });
  logo.addEventListener("mouseup", ()=>clearTimeout(pressTimer));
}
function tryAdminGate(){
  const pass = $("admin-gate-pass").value;
  if(pass === DB.creds.adminPass){
    closeModal("modal-admin-gate");
    session = { role:"admin" }; lsSet(STORE_KEYS.session, session);
    $("login-pass").value = "";
    enterApp();
  } else {
    $("admin-gate-err").textContent = "رمز مدیر نادرست است";
  }
}
function confirmLogout(){
  askConfirm("خروج از حساب", "آیا برای خروج از سامانه اطمینان دارید؟", ()=>{
    session = { role:null }; localStorage.removeItem(STORE_KEYS.session);
    location.reload();
  });
}
function enterApp(){
  $("screen-login").classList.remove("active");
  $("shell-admin").classList.remove("active");
  $("shell-viewer").classList.remove("active");
  const safeRun = (label, fn) => { try{ fn(); }catch(err){ showFatalError(`خطا در «${label}»:\n`+(err.stack||err.message||err)); throw err; } };
  if(session.role === "admin"){
    $("shell-admin").classList.add("active");
    safeRun("داشبورد مدیر", renderAdminDashboard);
    safeRun("فرم گزارش", renderAdminReportForm);
    safeRun("آرشیو مدیر", renderAdminArchive);
    safeRun("مرخصی مدیر", renderAdminLeaves);
    safeRun("پروژه‌های مدیر", renderAdminProjects);
    safeRun("تنظیمات مدیر", renderAdminSettings);
    if(unreadNotifCount()>0) $("notif-dot").classList.add("show");
  } else {
    $("shell-viewer").classList.add("active");
    safeRun("داشبورد", renderViewerDashboard);
    safeRun("آرشیو", renderViewerArchive);
    safeRun("مرخصی", renderViewerLeaves);
    safeRun("پروژه‌ها", renderViewerProjects);
  }
}

/* ---------------------------------------------------------
   4) TAB NAVIGATION
--------------------------------------------------------- */
function showAdminTab(name, btn){
  document.querySelectorAll("#shell-admin .tab").forEach(e=>e.classList.remove("active"));
  document.querySelectorAll("#shell-admin .bnav").forEach(e=>e.classList.remove("active"));
  $("tab-a-"+name).classList.add("active");
  btn.classList.add("active");
  if(name==="dashboard") renderAdminDashboard();
  if(name==="archive") renderAdminArchive();
  if(name==="leaves") renderAdminLeaves();
  if(name==="projects") renderAdminProjects();
  if(name==="settings") renderAdminSettings();
  window.scrollTo({top:0,behavior:"smooth"});
}
function showViewerTab(name, btn){
  document.querySelectorAll("#shell-viewer .tab").forEach(e=>e.classList.remove("active"));
  document.querySelectorAll("#shell-viewer .bnav").forEach(e=>e.classList.remove("active"));
  $("tab-v-"+name).classList.add("active");
  btn.classList.add("active");
  if(name==="dashboard") renderViewerDashboard();
  if(name==="archive") renderViewerArchive();
  if(name==="leaves") renderViewerLeaves();
  if(name==="projects") renderViewerProjects();
  window.scrollTo({top:0,behavior:"smooth"});
}

/* ---------------------------------------------------------
   5) DOMAIN HELPERS — mentors / reports / leaves / projects
--------------------------------------------------------- */
function activeMentors(){ return DB.mentors.filter(m=>m.active!==false); }
function mentorById(id){ return DB.mentors.find(m=>m.id===id); }
function initials(name){ return (name||"").trim().split(" ").map(s=>s[0]).slice(0,2).join(""); }

function reportScore(entry){
  // هر معیار حداکثر ۱۰، مجموع ۶ معیار کیفی از ۶۰ + امتیاز پیگیری بازخورد
  const q = (+entry.scientific||0)+(+entry.clarity||0)+(+entry.tone||0)+(+entry.education||0)+(+entry.followup||0)+(+entry.extra||0);
  return Math.round((q/60)*100);
}
function sortedReports(){ return DB.reports.slice().sort((a,b)=>b.createdAt-a.createdAt); }

function leaveUsed(mentorId){
  return DB.leaves.filter(l=>l.mentorId===mentorId && l.status==="approved")
    .reduce((s,l)=>s+(+l.days||0),0);
}
function leaveRemaining(mentorId){ return Math.max(0, MAX_LEAVE_DAYS - leaveUsed(mentorId)); }
function leavesToday(){
  const t = todayISO();
  return DB.leaves.filter(l=>l.status==="approved" && l.start<=t && l.end>=t);
}

function pushNotif(text, icon){
  DB.notifs.unshift({id:uid("n"), text, icon:icon||"<i class='fas fa-bell'></i>", ts:Date.now(), read:false});
  DB.notifs = DB.notifs.slice(0,30);
  saveAll();
}
function unreadNotifCount(){ return DB.notifs.filter(n=>!n.read).length; }

const STATUS_LABELS = { todo:"در صف", in_progress:"در حال انجام", review:"بازبینی", done:"تکمیل‌شده" };
const STATUS_COLORS = { todo:"blue", in_progress:"amber", review:"gold", done:"green" };
const PRIO_LABELS = { low:"کم", mid:"متوسط", high:"بالا" };

/* ---------------------------------------------------------
   6) CANVAS CHARTS — lightweight, dependency-free
--------------------------------------------------------- */
function drawBarChart(canvas, labels, values, opts){
  opts = opts || {};
  const dpr = window.devicePixelRatio || 1;
  const cssW = canvas.parentElement.clientWidth, cssH = opts.height || 160;
  canvas.width = cssW*dpr; canvas.height = cssH*dpr;
  canvas.style.height = cssH+"px";
  const ctx = canvas.getContext("2d"); ctx.scale(dpr,dpr);
  ctx.clearRect(0,0,cssW,cssH);
  const max = Math.max(1, ...values);
  const padL = 6, padR = 6, padB = 22, padT = 14;
  const barW = (cssW - padL - padR) / values.length;
  const innerW = barW*0.5;
  values.forEach((v,i)=>{
    const h = ((cssH-padB-padT) * (v/max));
    const x = padL + i*barW + (barW-innerW)/2;
    const y = cssH - padB - h;
    const grad = ctx.createLinearGradient(0,y,0,cssH-padB);
    grad.addColorStop(0, opts.color2 || "#e3c878");
    grad.addColorStop(1, opts.color || "#c9a84c");
    ctx.fillStyle = grad;
    roundRectPath(ctx, x, y, innerW, Math.max(h,3), 5);
    ctx.fill();
    ctx.fillStyle = "rgba(255,255,255,.45)";
    ctx.font = "10px Vazirmatn"; ctx.textAlign = "center";
    ctx.fillText(labels[i]||"", x+innerW/2, cssH-6);
  });
}
function roundRectPath(ctx,x,y,w,h,r){
  ctx.beginPath();
  ctx.moveTo(x+r,y);
  ctx.arcTo(x+w,y,x+w,y+h,r);
  ctx.arcTo(x+w,y+h,x,y+h,r);
  ctx.arcTo(x,y+h,x,y,r);
  ctx.arcTo(x,y,x+w,y,r);
  ctx.closePath();
}
function drawLineChart(canvas, labels, values, opts){
  opts = opts || {};
  const dpr = window.devicePixelRatio || 1;
  const cssW = canvas.parentElement.clientWidth, cssH = opts.height || 150;
  canvas.width = cssW*dpr; canvas.height = cssH*dpr;
  canvas.style.height = cssH+"px";
  const ctx = canvas.getContext("2d"); ctx.scale(dpr,dpr);
  ctx.clearRect(0,0,cssW,cssH);
  if(!values.length) return;
  const max = Math.max(...values, 10), min = Math.min(...values, 0);
  const padL=8,padR=8,padT=16,padB=22;
  const stepX = (cssW-padL-padR)/Math.max(1,values.length-1);
  const pts = values.map((v,i)=>{
    const x = padL+i*stepX;
    const y = padT + (cssH-padT-padB)*(1-((v-min)/Math.max(1,(max-min))));
    return [x,y];
  });
  // fill
  ctx.beginPath(); ctx.moveTo(pts[0][0], cssH-padB);
  pts.forEach(p=>ctx.lineTo(p[0],p[1]));
  ctx.lineTo(pts[pts.length-1][0], cssH-padB); ctx.closePath();
  const grad = ctx.createLinearGradient(0,0,0,cssH);
  grad.addColorStop(0,"rgba(91,141,239,.28)"); grad.addColorStop(1,"rgba(91,141,239,0)");
  ctx.fillStyle = grad; ctx.fill();
  // line
  ctx.beginPath(); pts.forEach((p,i)=> i===0?ctx.moveTo(p[0],p[1]):ctx.lineTo(p[0],p[1]));
  ctx.strokeStyle = "#5b8def"; ctx.lineWidth = 2.4; ctx.lineJoin="round"; ctx.stroke();
  // dots + labels
  ctx.font = "9.5px Vazirmatn"; ctx.fillStyle="rgba(255,255,255,.4)"; ctx.textAlign="center";
  pts.forEach((p,i)=>{
    ctx.beginPath(); ctx.arc(p[0],p[1],3,0,7); ctx.fillStyle="#5b8def"; ctx.fill();
    ctx.fillStyle="rgba(255,255,255,.4)";
    ctx.fillText(labels[i]||"", p[0], cssH-6);
  });
}

/* ============================================================
   7) ADMIN — DASHBOARD
============================================================ */
function renderAdminDashboard(){
  const reports = sortedReports();
  const totalReports = reports.length;
  const activeProjects = DB.projects.filter(p=>p.status!=="done").length;
  const doneProjects = DB.projects.filter(p=>p.status==="done").length;
  const onLeave = leavesToday().length;
  const lastReport = reports[0];

  const html = `
    <div class="flex-between" style="position:relative;z-index:1;margin-bottom:16px;">
      <div>
        <div style="font-size:18px;font-weight:800;">سلام، مدیر عزیز <i class='fas fa-hand'></i></div>
        <div class="muted" style="font-size:12.5px;margin-top:2px;">${fmtDate(Date.now())}</div>
      </div>
    </div>

    <div class="stat-grid">
      <div class="card stat-card">
        <div class="si" style="background:var(--green-bg);color:var(--green-light);"><i class='fas fa-file-pen'></i></div>
        <div class="sv">${faDigits(totalReports)}</div>
        <div class="sl">گزارش ثبت‌شده</div>
      </div>
      <div class="card stat-card">
        <div class="si" style="background:var(--blue-bg);color:var(--blue);"><i class='fas fa-folder-open'></i></div>
        <div class="sv">${faDigits(activeProjects)}</div>
        <div class="sl">پروژه فعال</div>
      </div>
      <div class="card stat-card">
        <div class="si" style="background:var(--green-bg);color:var(--green);"><i class='fas fa-circle-check'></i></div>
        <div class="sv">${faDigits(doneProjects)}</div>
        <div class="sl">پروژه تکمیل‌شده</div>
      </div>
      <div class="card stat-card">
        <div class="si" style="background:var(--amber-bg);color:var(--amber);"><i class='fas fa-umbrella-beach'></i></div>
        <div class="sv">${faDigits(onLeave)}</div>
        <div class="sl">مرخصی امروز</div>
      </div>
    </div>

    <div class="section-title"><span>روند ثبت گزارش (۶ هفته اخیر)</span></div>
    <div class="card chart-wrap"><canvas id="chart-reports-trend"></canvas></div>

    <div class="section-title"><span>وضعیت منتورها</span></div>
    <div class="card" style="padding:6px 14px;">
      ${activeMentors().map(m=>{
        const used = leaveUsed(m.id), rem = leaveRemaining(m.id);
        const onLeaveNow = leavesToday().some(l=>l.mentorId===m.id);
        return `<div class="row-item">
          <div class="row-avatar">${initials(m.name)}</div>
          <div class="row-main">
            <div class="row-title">${escapeHtml(m.name)}</div>
            <div class="row-sub">${escapeHtml(m.role)} · ${faDigits(rem)} روز مرخصی باقی‌مانده</div>
          </div>
          <div class="row-aside">${onLeaveNow ? '<span class="badge badge-amber">مرخصی</span>' : '<span class="badge badge-green">فعال</span>'}</div>
        </div>`;
      }).join("") || emptyState("<i class='fas fa-users'></i>","هنوز منتوری ثبت نشده")}
    </div>

    <div class="section-title"><span>آخرین گزارش</span><span class="lnk" onclick="showAdminTab('archive', document.querySelector('[data-tab=archive]'))">مشاهده آرشیو ›</span></div>
    <div class="card">
      ${lastReport ? reportSummaryRow(lastReport, ()=>{}) : emptyState("<i class='fas fa-folder'></i>","هنوز گزارشی ثبت نشده")}
    </div>

    <div class="section-title"><span>فعالیت‌های اخیر</span></div>
    <div class="card" style="padding:6px 14px;">
      ${(DB.notifs.slice(0,5).map(n=>`
        <div class="row-item">
          <div class="row-avatar" style="background:rgba(255,255,255,.05);">${n.icon}</div>
          <div class="row-main">
            <div class="row-title" style="font-weight:600;font-size:13px;">${escapeHtml(n.text)}</div>
            <div class="row-sub">${fmtDateShort(n.ts)}</div>
          </div>
        </div>`).join("")) || emptyState("<i class='fas fa-clock'></i>","هنوز فعالیتی ثبت نشده")}
    </div>
  `;
  $("tab-a-dashboard").innerHTML = html;
  // chart data: count reports per ISO week, last 6
  const buckets = lastNWeeksBuckets(6);
  reports.forEach(r=>{
    const wk = isoWeekKey(r.createdAt);
    if(buckets[wk]!==undefined) buckets[wk]++;
  });
  const labels = Object.keys(buckets).map(k=>k.split("-W")[1] ? "ه"+faDigits(k.split("-W")[1]) : k);
  drawBarChart($("chart-reports-trend"), labels, Object.values(buckets));
}
function emptyState(icon, text){
  return `<div class="empty-state"><div class="ico">${icon}</div><p>${text}</p></div>`;
}
function isoWeekKey(ts){
  const d = new Date(ts);
  const onejan = new Date(d.getFullYear(),0,1);
  const week = Math.ceil((((d-onejan)/86400000)+onejan.getDay()+1)/7);
  return d.getFullYear()+"-W"+week;
}
function lastNWeeksBuckets(n){
  const out = {};
  for(let i=n-1;i>=0;i--){
    const d = new Date(Date.now()-i*7*86400000);
    out[isoWeekKey(d.getTime())] = 0;
  }
  return out;
}
function reportSummaryRow(r){
  const mentorCount = Object.keys(r.entries||{}).length;
  const avg = mentorCount ? Math.round(Object.values(r.entries).reduce((s,e)=>s+reportScore(e),0)/mentorCount) : 0;
  return `<div class="row-item" onclick="openReportDetail('${r.id}')">
    <div class="row-avatar"><i class='fas fa-clipboard-list'></i></div>
    <div class="row-main">
      <div class="row-title">گزارش هفته ${escapeHtml(r.weekLabel||fmtDateShort(r.createdAt))}</div>
      <div class="row-sub">${faDigits(mentorCount)} منتور · میانگین امتیاز ${faDigits(avg)}٪</div>
    </div>
    <div class="row-aside">${fmtDateShort(r.createdAt)}</div>
  </div>`;
}

/* ============================================================
   8) ADMIN — WEEKLY REPORT FORM
============================================================ */
function renderAdminReportForm(){
  const mentors = activeMentors();
  const html = `
    <div class="flex-between mb-2">
      <div style="font-size:17px;font-weight:800;">ثبت گزارش هفتگی</div>
      <span class="badge badge-gold">هفته جاری</span>
    </div>

    <div class="card mt-2">
      <div class="card-tt"><i class='fas fa-calendar-days'></i> بازه هفته</div>
      <div class="field-row">
        <div class="field"><label>از تاریخ</label><input type="date" id="rf-from" value="${mondayOfThisWeek()}"></div>
        <div class="field"><label>تا تاریخ</label><input type="date" id="rf-to" value="${todayISO()}"></div>
      </div>
    </div>

    <div class="card mt-3">
      <div class="card-tt"><i class='fas fa-rotate'></i> موضوعات پرتکرار</div>
      <textarea id="rf-topics" placeholder="سوالات و موضوعاتی که این هفته زیاد مطرح شدند..."></textarea>
    </div>
    <div class="card mt-3">
      <div class="card-tt"><i class='fas fa-chart-line'></i> تغییرات نسبت به هفته قبل</div>
      <textarea id="rf-changes" placeholder="تغییرات کیفی، کمی یا فرایندی..."></textarea>
    </div>
    <div class="card mt-3">
      <div class="card-tt"><i class='fas fa-bullseye'></i> هدف هفته بعد</div>
      <textarea id="rf-goal" placeholder="هدف تیم برای هفته آینده..."></textarea>
    </div>

    <div class="section-title"><span>عملکرد منتورها</span></div>
    <div id="rf-mentors">
      ${mentors.map(m=>mentorReportCard(m)).join("") || emptyState("<i class='fas fa-users'></i>","ابتدا منتور اضافه کنید")}
    </div>

    <button class="btn btn-gold btn-block mt-3" onclick="saveWeeklyReport()"><i class='fas fa-floppy-disk'></i> ذخیره گزارش هفتگی</button>
    <button class="btn btn-ghost btn-block mt-2" onclick="previewCurrentForm()"><i class='fas fa-eye'></i> پیش‌نمایش و چاپ</button>
  `;
  $("tab-a-report").innerHTML = html;
}
function mondayOfThisWeek(){
  const d = new Date(); const day = d.getDay(); // 0=Sun
  const diff = (day===6)?0:(day+1); // Saturday as start (Iran week) -> Saturday=6
  const sat = new Date(d); sat.setDate(d.getDate()-((day+1)%7));
  return sat.toISOString().slice(0,10);
}
function mentorReportCard(m){
  const def = SEED_REPORT_DEFAULTS[m.id] || {};
  return `
  <div class="card mt-2" data-mentor="${m.id}">
    <div class="flex-between mb-2">
      <div style="display:flex;align-items:center;gap:10px;">
        <div class="row-avatar">${initials(m.name)}</div>
        <div>
          <div style="font-weight:800;font-size:14px;">${escapeHtml(m.name)}</div>
          <div class="muted" style="font-size:11.5px;">${escapeHtml(m.role)}</div>
        </div>
      </div>
      <span class="badge badge-blue" id="score-${m.id}">—</span>
    </div>
    <div class="field-row">
      <div class="field"><label>ساعت شروع</label><input type="text" id="${m.id}-startTime" value="${def.startTime||''}" placeholder="مثلاً 9:00"></div>
      <div class="field"><label>تعداد پیام</label><input type="number" id="${m.id}-messages" value="${def.messages||''}" oninput="updateMentorScore('${m.id}')"></div>
    </div>
    <div class="field-row">
      <div class="field"><label>پاسخ‌های جدید</label><input type="number" id="${m.id}-newReplies" value="${def.newReplies||''}"></div>
      <div class="field"><label>دقت علمی (۱۰-۰)</label><input type="number" min="0" max="10" id="${m.id}-scientific" value="${def.scientific||''}" oninput="updateMentorScore('${m.id}')"></div>
    </div>
    <div class="field-row">
      <div class="field"><label>شفافیت پاسخ</label><input type="number" min="0" max="10" id="${m.id}-clarity" value="${def.clarity||''}" oninput="updateMentorScore('${m.id}')"></div>
      <div class="field"><label>لحن و ادب</label><input type="number" min="0" max="10" id="${m.id}-tone" value="${def.tone||''}" oninput="updateMentorScore('${m.id}')"></div>
    </div>
    <div class="field-row">
      <div class="field"><label>آموزش‌دهی</label><input type="number" min="0" max="10" id="${m.id}-education" value="${def.education||''}" oninput="updateMentorScore('${m.id}')"></div>
      <div class="field"><label>پیگیری</label><input type="number" min="0" max="10" id="${m.id}-followup" value="${def.followup||''}" oninput="updateMentorScore('${m.id}')"></div>
    </div>
    <div class="field">
      <label>امتیاز تکمیلی</label>
      <input type="number" min="0" max="10" id="${m.id}-extra" value="${def.extra||''}" oninput="updateMentorScore('${m.id}')">
    </div>
    <div class="field">
      <label>یادداشت</label>
      <textarea id="${m.id}-note" placeholder="یادداشت این هفته...">${escapeHtml(def.note||'')}</textarea>
    </div>
  </div>`;
}
function readMentorEntry(mid){
  const gv = id => $(id) ? $(id).value : "";
  const gn = id => +($(id) ? $(id).value : 0) || 0;
  return {
    startTime: gv(`${mid}-startTime`), messages: gn(`${mid}-messages`), newReplies: gn(`${mid}-newReplies`),
    scientific: gn(`${mid}-scientific`), clarity: gn(`${mid}-clarity`), tone: gn(`${mid}-tone`),
    education: gn(`${mid}-education`), followup: gn(`${mid}-followup`), extra: gn(`${mid}-extra`),
    note: gv(`${mid}-note`)
  };
}
function updateMentorScore(mid){
  const e = readMentorEntry(mid); const s = reportScore(e);
  const el = $("score-"+mid); if(el) el.textContent = faDigits(s)+"٪";
}
function buildCurrentReportPayload(){
  const mentors = activeMentors();
  const entries = {};
  mentors.forEach(m=>{ entries[m.id] = readMentorEntry(m.id); });
  return {
    id: uid("rep"),
    weekLabel: $("rf-from").value && $("rf-to").value ? `${faDigits($("rf-from").value)} تا ${faDigits($("rf-to").value)}` : fmtDateShort(Date.now()),
    dateFrom: $("rf-from").value, dateTo: $("rf-to").value,
    topics: $("rf-topics").value, changes: $("rf-changes").value, goal: $("rf-goal").value,
    entries, createdAt: Date.now()
  };
}
function saveWeeklyReport(){
  const payload = buildCurrentReportPayload();
  DB.reports.push(payload); saveAll();
  pushNotif(`گزارش هفته ${payload.weekLabel} ثبت شد`, "<i class='fas fa-file-pen'></i>");
  showToast("<i class='fas fa-circle-check'></i> گزارش با موفقیت در آرشیو ذخیره شد");
  renderAdminDashboard(); renderAdminArchive();
}
function previewCurrentForm(){
  const payload = buildCurrentReportPayload();
  openReportPreviewModal(payload, false);
}

/* ============================================================
   9) ARCHIVE — admin + viewer (shared rendering, admin gets edit/delete)
============================================================ */
function renderAdminArchive(){ $("tab-a-archive").innerHTML = archiveTabHtml(true); bindArchiveSearch(true); }
function renderViewerArchive(){ $("tab-v-archive").innerHTML = archiveTabHtml(false); bindArchiveSearch(false); }

function archiveTabHtml(isAdmin){
  return `
    <div style="font-size:17px;font-weight:800;margin-bottom:14px;">آرشیو گزارش‌های هفتگی</div>
    <div class="search-bar">
      <span><i class='fas fa-magnifying-glass'></i></span>
      <input type="text" id="archive-search-${isAdmin?'a':'v'}" placeholder="جستجو بر اساس تاریخ یا متن گزارش...">
    </div>
    <div class="card" style="padding:6px 14px;" id="archive-list-${isAdmin?'a':'v'}"></div>
  `;
}
function bindArchiveSearch(isAdmin){
  const id = "archive-search-"+(isAdmin?'a':'v');
  renderArchiveList(isAdmin, "");
  $(id).addEventListener("input", e=>renderArchiveList(isAdmin, e.target.value.trim()));
}
function renderArchiveList(isAdmin, query){
  const listId = "archive-list-"+(isAdmin?'a':'v');
  let reports = sortedReports();
  if(query){
    const q = query.toLowerCase();
    reports = reports.filter(r =>
      (r.weekLabel||"").toLowerCase().includes(q) ||
      (r.topics||"").toLowerCase().includes(q) ||
      (r.changes||"").toLowerCase().includes(q) ||
      (r.goal||"").toLowerCase().includes(q)
    );
  }
  $(listId).innerHTML = reports.length ? reports.map(r=>reportSummaryRow(r)).join("") : emptyState("<i class='fas fa-folder'></i>","گزارشی یافت نشد");
}
function openReportDetail(id){
  const r = DB.reports.find(x=>x.id===id);
  if(!r) return;
  openReportPreviewModal(r, true);
}
function openReportPreviewModal(r, isSaved){
  const mentors = Object.keys(r.entries||{}).map(mid=>({mid, m:mentorById(mid)||{name:mid,role:""}, e:r.entries[mid]}));
  const html = `
    <div class="modal-head">
      <h3><i class='fas fa-clipboard-list'></i> گزارش هفته ${escapeHtml(r.weekLabel||'')}</h3>
      <button class="modal-close" onclick="closeModal('modal-generic')"><i class='fas fa-xmark'></i></button>
    </div>
    <div style="font-size:12.5px;color:var(--tx3);margin-bottom:14px;">${fmtDate(r.createdAt)}</div>

    ${r.topics ? infoBlock("<i class='fas fa-rotate'></i> موضوعات پرتکرار", r.topics) : ""}
    ${r.changes ? infoBlock("<i class='fas fa-chart-line'></i> تغییرات نسبت به هفته قبل", r.changes) : ""}
    ${r.goal ? infoBlock("<i class='fas fa-bullseye'></i> هدف هفته بعد", r.goal) : ""}

    <div class="section-title" style="margin-top:18px;"><span>عملکرد منتورها</span></div>
    ${mentors.map(({m,e})=>`
      <div class="card mt-2">
        <div class="flex-between mb-2">
          <div style="font-weight:800;font-size:13.5px;">${escapeHtml(m.name)}</div>
          <span class="badge badge-blue">${faDigits(reportScore(e))}٪</span>
        </div>
        <div class="muted" style="font-size:12px;line-height:2;">
          شروع: ${escapeHtml(e.startTime||'-')} · پیام: ${faDigits(e.messages||0)} · پاسخ جدید: ${faDigits(e.newReplies||0)}<br>
          علمی: ${faDigits(e.scientific||0)} · شفافیت: ${faDigits(e.clarity||0)} · لحن: ${faDigits(e.tone||0)} · آموزش: ${faDigits(e.education||0)} · پیگیری: ${faDigits(e.followup||0)} · تکمیلی: ${faDigits(e.extra||0)}
        </div>
        ${e.note ? `<div style="margin-top:8px;font-size:12.5px;background:rgba(255,255,255,.04);border-radius:10px;padding:10px;">${nl2br(e.note)}</div>` : ""}
      </div>
    `).join("")}

    <div style="display:flex;gap:10px;margin-top:18px;">
      <button class="btn btn-ghost btn-block" onclick="window.print()"><i class='fas fa-print'></i> چاپ</button>
      ${(session.role==="admin" && isSaved) ? `<button class="btn btn-danger btn-block" onclick="deleteReport('${r.id}')">حذف گزارش</button>` : ""}
    </div>
  `;
  genericModal(html, "560px");
}
function infoBlock(title, text){
  return `<div class="card mt-2"><div class="card-tt">${title}</div><div style="font-size:13px;line-height:1.9;">${nl2br(text)}</div></div>`;
}
function deleteReport(id){
  askConfirm("حذف گزارش", "این گزارش برای همیشه از آرشیو حذف می‌شود. این عملیات فقط توسط مدیر قابل انجام است و قابل بازگشت نیست.", ()=>{
    DB.reports = DB.reports.filter(r=>r.id!==id);
    saveAll(); closeModal("modal-generic");
    renderAdminArchive(); renderAdminDashboard();
    showToast("<i class='fas fa-trash'></i> گزارش حذف شد");
  });
}

/* ============================================================
   10) LEAVE MANAGEMENT
============================================================ */
function renderAdminLeaves(){ $("tab-a-leaves").innerHTML = leavesTabHtml(true); }
function renderViewerLeaves(){ $("tab-v-leaves").innerHTML = leavesTabHtml(false); }

function leavesTabHtml(isAdmin){
  const mentors = activeMentors();
  return `
    <div class="flex-between mb-2">
      <div style="font-size:17px;font-weight:800;">مدیریت مرخصی</div>
    </div>

    <div class="section-title"><span>وضعیت سالانه منتورها</span></div>
    ${mentors.map(m=>{
      const used = leaveUsed(m.id), rem = leaveRemaining(m.id);
      const pct = Math.min(100, Math.round((used/MAX_LEAVE_DAYS)*100));
      const circ = 2*Math.PI*26;
      return `<div class="card mt-2 leave-card">
        <div class="leave-ring">
          <svg width="64" height="64">
            <circle cx="32" cy="32" r="26" fill="none" stroke="rgba(255,255,255,.08)" stroke-width="6"></circle>
            <circle cx="32" cy="32" r="26" fill="none" stroke="${pct>80?'#f1626a':'#c9a84c'}" stroke-width="6"
              stroke-dasharray="${circ}" stroke-dashoffset="${circ - (circ*pct/100)}" stroke-linecap="round"></circle>
          </svg>
          <div class="num">${faDigits(rem)}</div>
        </div>
        <div class="row-main">
          <div class="row-title">${escapeHtml(m.name)}</div>
          <div class="row-sub">${faDigits(used)} روز استفاده‌شده از ${faDigits(MAX_LEAVE_DAYS)} روز سالانه</div>
        </div>
      </div>`;
    }).join("") || emptyState("<i class='fas fa-users'></i>","منتوری ثبت نشده")}

    <div class="section-title"><span>درخواست‌های مرخصی</span></div>
    <div class="card" style="padding:6px 14px;" id="leave-req-list-${isAdmin?'a':'v'}">
      ${leaveRequestRows(isAdmin)}
    </div>
    ${isAdmin ? `<button class="fab" onclick="openLeaveForm()" title="ثبت مرخصی جدید">＋</button>` : ""}
  `;
}
function leaveRequestRows(isAdmin){
  const list = DB.leaves.slice().sort((a,b)=>b.createdAt-a.createdAt);
  if(!list.length) return emptyState("<i class='fas fa-umbrella-beach'></i>","هنوز مرخصی‌ای ثبت نشده");
  const statusBadge = s => s==="approved" ? '<span class="badge badge-green">تأیید‌شده</span>'
    : s==="rejected" ? '<span class="badge badge-red">رد‌شده</span>'
    : '<span class="badge badge-amber">در انتظار</span>';
  return list.map(l=>{
    const m = mentorById(l.mentorId) || {name:"نامشخص"};
    return `<div class="row-item" onclick="${isAdmin?`openLeaveDetail('${l.id}')`:'void(0)'}">
      <div class="row-avatar">${initials(m.name)}</div>
      <div class="row-main">
        <div class="row-title">${escapeHtml(m.name)}</div>
        <div class="row-sub">${faDigits(l.days)} روز · ${escapeHtml(l.reason||'بدون توضیح')}</div>
      </div>
      <div class="row-aside">${statusBadge(l.status)}</div>
    </div>`;
  }).join("");
}
function openLeaveForm(existing){
  const mentors = activeMentors();
  const l = existing || {};
  const html = `
    <div class="modal-head"><h3><i class='fas fa-umbrella-beach'></i> ${existing?'ویرایش':'ثبت'} مرخصی</h3><button class="modal-close" onclick="closeModal('modal-generic')"><i class='fas fa-xmark'></i></button></div>
    <div class="field">
      <label>منتور</label>
      <select id="lv-mentor">${mentors.map(m=>`<option value="${m.id}" ${l.mentorId===m.id?'selected':''}>${escapeHtml(m.name)}</option>`).join("")}</select>
    </div>
    <div class="field-row">
      <div class="field"><label>تاریخ شروع</label><input type="date" id="lv-start" value="${l.start||todayISO()}" onchange="recalcLeaveDays()"></div>
      <div class="field"><label>تاریخ پایان</label><input type="date" id="lv-end" value="${l.end||todayISO()}" onchange="recalcLeaveDays()"></div>
    </div>
    <div class="field"><label>تعداد روز</label><input type="number" id="lv-days" value="${l.days||1}" min="1"></div>
    <div class="field"><label>علت مرخصی</label><textarea id="lv-reason" placeholder="توضیح کوتاه...">${escapeHtml(l.reason||'')}</textarea></div>
    <div class="field">
      <label>وضعیت</label>
      <select id="lv-status">
        <option value="pending" ${(!l.status||l.status==='pending')?'selected':''}>در انتظار</option>
        <option value="approved" ${l.status==='approved'?'selected':''}>تأیید‌شده</option>
        <option value="rejected" ${l.status==='rejected'?'selected':''}>رد‌شده</option>
      </select>
    </div>
    <div style="display:flex;gap:10px;margin-top:6px;">
      ${existing ? `<button class="btn btn-danger" onclick="deleteLeave('${l.id}')">حذف</button>` : ""}
      <button class="btn btn-gold btn-block" onclick="saveLeave(${existing?`'${l.id}'`:'null'})">ذخیره</button>
    </div>
  `;
  genericModal(html, "420px");
}
function recalcLeaveDays(){
  const s = new Date($("lv-start").value), e = new Date($("lv-end").value);
  const days = Math.max(1, Math.round((e-s)/86400000)+1);
  if($("lv-days")) $("lv-days").value = isNaN(days)?1:days;
}
function saveLeave(id){
  const data = {
    mentorId: $("lv-mentor").value, start: $("lv-start").value, end: $("lv-end").value,
    days: +$("lv-days").value || 1, reason: $("lv-reason").value, status: $("lv-status").value
  };
  if(id){
    const idx = DB.leaves.findIndex(l=>l.id===id);
    DB.leaves[idx] = Object.assign(DB.leaves[idx], data);
  } else {
    data.id = uid("lv"); data.createdAt = Date.now();
    DB.leaves.push(data);
    pushNotif(`درخواست مرخصی برای ${mentorById(data.mentorId)?.name||''} ثبت شد`, "<i class='fas fa-umbrella-beach'></i>");
  }
  saveAll(); closeModal("modal-generic");
  renderAdminLeaves(); renderAdminDashboard();
  showToast("<i class='fas fa-circle-check'></i> مرخصی ذخیره شد");
}
function deleteLeave(id){
  askConfirm("حذف مرخصی", "این درخواست مرخصی حذف می‌شود.", ()=>{
    DB.leaves = DB.leaves.filter(l=>l.id!==id); saveAll();
    closeModal("modal-generic"); renderAdminLeaves(); renderAdminDashboard();
    showToast("<i class='fas fa-trash'></i> حذف شد");
  });
}
function openLeaveDetail(id){
  const l = DB.leaves.find(x=>x.id===id);
  if(l) openLeaveForm(l);
}

/* ============================================================
   11) PROJECTS
============================================================ */
let projectFilter = "all";
function renderAdminProjects(){ $("tab-a-projects").innerHTML = projectsTabHtml(true); }
function renderViewerProjects(){ $("tab-v-projects").innerHTML = projectsTabHtml(false); }

function projectsTabHtml(isAdmin){
  return `
    <div style="font-size:17px;font-weight:800;margin-bottom:14px;">پروژه‌ها</div>
    <div class="segmented" id="proj-filter-${isAdmin?'a':'v'}">
      ${[["all","همه"],["todo","در صف"],["in_progress","در حال انجام"],["review","بازبینی"],["done","تکمیل"]]
        .map(([k,l])=>`<button class="${projectFilter===k?'active':''}" onclick="setProjectFilter('${k}',this,'${isAdmin?'a':'v'}')">${l}</button>`).join("")}
    </div>
    <div id="proj-list-${isAdmin?'a':'v'}">${projectListHtml(isAdmin)}</div>
    ${isAdmin ? `<button class="fab" onclick="openProjectForm()" title="پروژه جدید">＋</button>` : ""}
  `;
}
function setProjectFilter(k, btn, who){
  projectFilter = k;
  btn.parentElement.querySelectorAll("button").forEach(b=>b.classList.remove("active"));
  btn.classList.add("active");
  $("proj-list-"+who).innerHTML = projectListHtml(who==="a");
}
function projectListHtml(isAdmin){
  let list = DB.projects.slice().sort((a,b)=>b.createdAt-a.createdAt);
  if(projectFilter!=="all") list = list.filter(p=>p.status===projectFilter);
  if(!list.length) return `<div class="card">${emptyState("<i class='fas fa-folder-open'></i>","پروژه‌ای یافت نشد")}</div>`;
  return list.map(p=>`
    <div class="card mt-2" onclick="${isAdmin?`openProjectForm(${escapeForAttr(p)})`:`openProjectView('${p.id}')`}">
      <div class="flex-between">
        <div style="display:flex;align-items:center;gap:8px;">
          <span class="prio-dot prio-${p.priority||'mid'}"></span>
          <div style="font-weight:800;font-size:14px;">${escapeHtml(p.title)}</div>
        </div>
        <span class="badge badge-${STATUS_COLORS[p.status]||'blue'}">${STATUS_LABELS[p.status]||p.status}</span>
      </div>
      <div class="muted mt-2" style="font-size:12.5px;line-height:1.8;">${escapeHtml(p.description||'').slice(0,110)}${(p.description||'').length>110?'…':''}</div>
      <div class="progress-bar"><i style="width:${p.progress||0}%'></i></div>
      <div class="flex-between mt-2" style="font-size:11px;color:var(--tx3);">
        <span><i class='fas fa-user'></i> ${escapeHtml(p.owner||'تعیین‌نشده')}</span>
        <span>${faDigits(p.progress||0)}٪ پیشرفت</span>
      </div>
    </div>
  `).join("");
}
function escapeForAttr(p){ return "'"+p.id+"'"; }
function openProjectView(id){
  const p = DB.projects.find(x=>x.id===id); if(!p) return;
  const html = `
    <div class="modal-head"><h3><i class='fas fa-folder-open'></i> ${escapeHtml(p.title)}</h3><button class="modal-close" onclick="closeModal('modal-generic')"><i class='fas fa-xmark'></i></button></div>
    <div class="tag-row mb-2">
      <span class="badge badge-${STATUS_COLORS[p.status]}">${STATUS_LABELS[p.status]}</span>
      <span class="badge badge-gold">اولویت ${PRIO_LABELS[p.priority]}</span>
    </div>
    <div style="font-size:13.5px;line-height:1.9;color:var(--tx2);">${nl2br(p.description)}</div>
    <div class="progress-bar mt-3"><i style="width:${p.progress||0}%'></i></div>
    <div class="muted mt-2" style="font-size:12px;">${faDigits(p.progress||0)}٪ پیشرفت</div>
    <div class="card mt-3" style="font-size:12.5px;line-height:2;">
      مسئول: ${escapeHtml(p.owner||'-')}<br>
      تاریخ شروع: ${p.startDate?faDigits(p.startDate):'-'}<br>
      ددلاین: ${p.deadline?faDigits(p.deadline):'-'}
    </div>
    ${p.notes ? infoBlock("<i class='fas fa-note-sticky'></i> یادداشت‌ها", p.notes) : ""}
  `;
  genericModal(html, "440px");
}
function openProjectForm(id){
  const p = id ? DB.projects.find(x=>x.id===id) : {};
  const html = `
    <div class="modal-head"><h3><i class='fas fa-folder-open'></i> ${id?'ویرایش':'پروژه جدید'}</h3><button class="modal-close" onclick="closeModal('modal-generic')"><i class='fas fa-xmark'></i></button></div>
    <div class="field"><label>عنوان</label><input type="text" id="pr-title" value="${escapeHtml(p.title||'')}"></div>
    <div class="field"><label>توضیح</label><textarea id="pr-desc">${escapeHtml(p.description||'')}</textarea></div>
    <div class="field-row">
      <div class="field"><label>مسئول</label><input type="text" id="pr-owner" value="${escapeHtml(p.owner||'')}"></div>
      <div class="field"><label>اولویت</label>
        <select id="pr-priority">
          <option value="low" ${p.priority==='low'?'selected':''}>کم</option>
          <option value="mid" ${(!p.priority||p.priority==='mid')?'selected':''}>متوسط</option>
          <option value="high" ${p.priority==='high'?'selected':''}>بالا</option>
        </select>
      </div>
    </div>
    <div class="field-row">
      <div class="field"><label>وضعیت</label>
        <select id="pr-status">
          ${Object.entries(STATUS_LABELS).map(([k,l])=>`<option value="${k}" ${p.status===k?'selected':''}>${l}</option>`).join("")}
        </select>
      </div>
      <div class="field"><label>درصد پیشرفت</label><input type="number" id="pr-progress" min="0" max="100" value="${p.progress||0}"></div>
    </div>
    <div class="field-row">
      <div class="field"><label>تاریخ شروع</label><input type="date" id="pr-start" value="${p.startDate||''}"></div>
      <div class="field"><label>ددلاین</label><input type="date" id="pr-deadline" value="${p.deadline||''}"></div>
    </div>
    <div class="field"><label>یادداشت‌ها</label><textarea id="pr-notes">${escapeHtml(p.notes||'')}</textarea></div>
    <div style="display:flex;gap:10px;">
      ${id ? `<button class="btn btn-danger" onclick="deleteProject('${id}')">حذف</button>` : ""}
      <button class="btn btn-gold btn-block" onclick="saveProject(${id?`'${id}'`:'null'})">ذخیره پروژه</button>
    </div>
  `;
  genericModal(html, "460px");
}
function saveProject(id){
  const data = {
    title: $("pr-title").value.trim() || "پروژه بدون‌نام",
    description: $("pr-desc").value, owner: $("pr-owner").value,
    priority: $("pr-priority").value, status: $("pr-status").value,
    progress: Math.max(0,Math.min(100,+$("pr-progress").value||0)),
    startDate: $("pr-start").value, deadline: $("pr-deadline").value, notes: $("pr-notes").value
  };
  if(id){
    const idx = DB.projects.findIndex(p=>p.id===id);
    DB.projects[idx] = Object.assign(DB.projects[idx], data);
  } else {
    data.id = uid("p"); data.createdAt = Date.now();
    DB.projects.push(data);
    pushNotif(`پروژه «${data.title}» ایجاد شد`, "<i class='fas fa-folder-open'></i>");
  }
  saveAll(); closeModal("modal-generic");
  renderAdminProjects(); renderAdminDashboard();
  showToast("<i class='fas fa-circle-check'></i> پروژه ذخیره شد");
}
function deleteProject(id){
  askConfirm("حذف پروژه", "این پروژه برای همیشه حذف می‌شود.", ()=>{
    DB.projects = DB.projects.filter(p=>p.id!==id); saveAll();
    closeModal("modal-generic"); renderAdminProjects(); renderAdminDashboard();
    showToast("<i class='fas fa-trash'></i> پروژه حذف شد");
  });
}

/* ============================================================
   12) ADMIN SETTINGS
============================================================ */
function renderAdminSettings(){
  const html = `
    <div style="font-size:17px;font-weight:800;margin-bottom:14px;">تنظیمات</div>

    <div class="section-title"><span>مدیریت منتورها</span></div>
    <div class="card" style="padding:6px 14px;" id="settings-mentor-list">${mentorSettingsRows()}</div>
    <button class="btn btn-ghost btn-block mt-2" onclick="openMentorForm()"><i class='fas fa-plus'></i> افزودن منتور</button>

    <div class="section-title"><span>تغییر رمزهای عبور</span></div>
    <div class="card mt-2">
      <div class="field"><label>رمز مشاهده‌گر (عمومی)</label><input type="text" id="set-pass-viewer" value="${escapeHtml(DB.creds.viewerPass)}"></div>
      <div class="field"><label>رمز مدیر (مخفی)</label><input type="text" id="set-pass-admin" value="${escapeHtml(DB.creds.adminPass)}"></div>
      <button class="btn btn-navy btn-block" onclick="saveCredentials()">ذخیره رمزها</button>
    </div>

    <div class="section-title"><span>پشتیبان‌گیری از داده‌ها</span></div>
    <div class="card mt-2">
      <p class="muted" style="font-size:12.5px;line-height:1.9;margin-bottom:12px;">از تمام گزارش‌ها، مرخصی‌ها و پروژه‌ها یک فایل پشتیبان تهیه کنید یا از فایل قبلی بازیابی کنید.</p>
      <div style="display:flex;gap:10px;">
        <button class="btn btn-ghost btn-block" onclick="exportBackup()"><i class='fas fa-download'></i> خروجی JSON</button>
        <button class="btn btn-ghost btn-block" onclick="$('import-file').click()"><i class='fas fa-upload'></i> بازیابی</button>
      </div>
      <input type="file" id="import-file" accept=".json" class="hidden" onchange="importBackup(event)">
    </div>

    <div class="section-title"><span>درباره برنامه</span></div>
    <div class="card mt-2 muted" style="font-size:12px;line-height:2;">
      گزارش منتورینگ — آکادمی سبحان صمدی<br>
      نسخه ${APP_VERSION} · ذخیره‌سازی محلی (Offline-first PWA)<br>
      تمام داده‌ها روی همین دستگاه نگهداری می‌شوند و هیچ گزارشی به‌جز با تأیید مدیر حذف نخواهد شد.
    </div>
  `;
  $("tab-a-settings").innerHTML = html;
}
function mentorSettingsRows(){
  if(!DB.mentors.length) return emptyState("<i class='fas fa-users'></i>","منتوری ثبت نشده");
  return DB.mentors.map(m=>`
    <div class="row-item" onclick="openMentorForm('${m.id}')">
      <div class="row-avatar">${initials(m.name)}</div>
      <div class="row-main">
        <div class="row-title">${escapeHtml(m.name)}</div>
        <div class="row-sub">${escapeHtml(m.role)}</div>
      </div>
      <div class="row-aside">${m.active!==false?'<span class="badge badge-green">فعال</span>':'<span class="badge badge-red">غیرفعال</span>'}</div>
    </div>
  `).join("");
}
function openMentorForm(id){
  const m = id ? mentorById(id) : {};
  const html = `
    <div class="modal-head"><h3><i class='fas fa-user'></i> ${id?'ویرایش منتور':'منتور جدید'}</h3><button class="modal-close" onclick="closeModal('modal-generic')"><i class='fas fa-xmark'></i></button></div>
    <div class="field"><label>نام و نام‌خانوادگی</label><input type="text" id="mt-name" value="${escapeHtml(m.name||'')}"></div>
    <div class="field"><label>سمت</label><input type="text" id="mt-role" value="${escapeHtml(m.role||'منتور')}"></div>
    <div class="field">
      <label>وضعیت</label>
      <select id="mt-active">
        <option value="1" ${m.active!==false?'selected':''}>فعال</option>
        <option value="0" ${m.active===false?'selected':''}>غیرفعال</option>
      </select>
    </div>
    <div style="display:flex;gap:10px;">
      ${id ? `<button class="btn btn-danger" onclick="deleteMentor('${id}')">حذف</button>` : ""}
      <button class="btn btn-gold btn-block" onclick="saveMentor(${id?`'${id}'`:'null'})">ذخیره</button>
    </div>
  `;
  genericModal(html, "400px");
}
function saveMentor(id){
  const data = { name: $("mt-name").value.trim(), role: $("mt-role").value.trim()||"منتور", active: $("mt-active").value==="1" };
  if(!data.name){ showToast("نام منتور را وارد کنید"); return; }
  if(id){
    const idx = DB.mentors.findIndex(m=>m.id===id);
    DB.mentors[idx] = Object.assign(DB.mentors[idx], data);
  } else {
    data.id = uid("m"); DB.mentors.push(data);
  }
  saveAll(); closeModal("modal-generic");
  renderAdminSettings(); renderAdminReportForm(); renderAdminDashboard(); renderAdminLeaves();
  showToast("<i class='fas fa-circle-check'></i> ذخیره شد");
}
function deleteMentor(id){
  askConfirm("حذف منتور", "این منتور حذف می‌شود؛ گزارش‌های قبلی او در آرشیو باقی می‌مانند.", ()=>{
    DB.mentors = DB.mentors.filter(m=>m.id!==id); saveAll();
    closeModal("modal-generic");
    renderAdminSettings(); renderAdminReportForm(); renderAdminDashboard(); renderAdminLeaves();
    showToast("<i class='fas fa-trash'></i> منتور حذف شد");
  });
}
function saveCredentials(){
  const v = $("set-pass-viewer").value.trim(), a = $("set-pass-admin").value.trim();
  if(!v || !a){ showToast("هر دو رمز را وارد کنید"); return; }
  if(v===a){ showToast("رمز مدیر و مشاهده‌گر باید متفاوت باشند"); return; }
  DB.creds.viewerPass = v; DB.creds.adminPass = a; saveAll();
  showToast("<i class='fas fa-lock'></i> رمزهای عبور به‌روزرسانی شد");
}
function exportBackup(){
  const data = { mentors:DB.mentors, reports:DB.reports, leaves:DB.leaves, projects:DB.projects, exportedAt:Date.now(), version:APP_VERSION };
  const blob = new Blob([JSON.stringify(data,null,2)], {type:"application/json"});
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob); a.download = `mentoring-backup-${todayISO()}.json`;
  document.body.appendChild(a); a.click(); a.remove();
  showToast("<i class='fas fa-download'></i> فایل پشتیبان دانلود شد");
}
function importBackup(ev){
  const file = ev.target.files[0]; if(!file) return;
  const reader = new FileReader();
  reader.onload = ()=>{
    try{
      const data = JSON.parse(reader.result);
      askConfirm("بازیابی پشتیبان", "داده‌های فعلی با داده‌های فایل پشتیبان جایگزین می‌شوند. ادامه می‌دهید؟", ()=>{
        if(data.mentors) DB.mentors = data.mentors;
        if(data.reports) DB.reports = data.reports;
        if(data.leaves) DB.leaves = data.leaves;
        if(data.projects) DB.projects = data.projects;
        saveAll(); enterApp();
        showToast("<i class='fas fa-circle-check'></i> بازیابی با موفقیت انجام شد");
      });
    }catch(e){ showToast("<i class='fas fa-triangle-exclamation'></i> فایل نامعتبر است"); }
  };
  reader.readAsText(file);
}

/* ============================================================
   13) VIEWER — DASHBOARD (read-only)
============================================================ */
function renderViewerDashboard(){
  const reports = sortedReports();
  const lastReport = reports[0];
  const onLeave = leavesToday().length;
  const html = `
    <div style="position:relative;z-index:1;">
      <div style="font-size:18px;font-weight:800;">گزارش منتورینگ</div>
      <div class="muted" style="font-size:12.5px;margin-top:2px;">${fmtDate(Date.now())}</div>
    </div>

    <div class="stat-grid mt-3">
      <div class="card stat-card">
        <div class="si" style="background:rgba(201,168,76,.14);color:var(--green-light);"><i class='fas fa-file-pen'></i></div>
        <div class="sv">${faDigits(reports.length)}</div>
        <div class="sl">گزارش ثبت‌شده</div>
      </div>
      <div class="card stat-card">
        <div class="si" style="background:var(--blue-bg);color:var(--blue);"><i class='fas fa-folder-open'></i></div>
        <div class="sv">${faDigits(DB.projects.filter(p=>p.status!=='done').length)}</div>
        <div class="sl">پروژه فعال</div>
      </div>
      <div class="card stat-card">
        <div class="si" style="background:var(--green-bg);color:var(--green);"><i class='fas fa-users'></i></div>
        <div class="sv">${faDigits(activeMentors().length)}</div>
        <div class="sl">منتور فعال</div>
      </div>
      <div class="card stat-card">
        <div class="si" style="background:var(--amber-bg);color:var(--amber);"><i class='fas fa-umbrella-beach'></i></div>
        <div class="sv">${faDigits(onLeave)}</div>
        <div class="sl">مرخصی امروز</div>
      </div>
    </div>

    <div class="section-title"><span>روند ثبت گزارش</span></div>
    <div class="card chart-wrap"><canvas id="chart-v-trend"></canvas></div>

    <div class="section-title"><span>آخرین گزارش</span></div>
    <div class="card">${lastReport ? reportSummaryRow(lastReport) : emptyState("<i class='fas fa-folder'></i>","هنوز گزارشی ثبت نشده")}</div>
  `;
  $("tab-v-dashboard").innerHTML = html;
  const buckets = lastNWeeksBuckets(6);
  reports.forEach(r=>{ const wk = isoWeekKey(r.createdAt); if(buckets[wk]!==undefined) buckets[wk]++; });
  drawBarChart($("chart-v-trend"), Object.keys(buckets).map((k,i)=>"ه"+faDigits(i+1)), Object.values(buckets));
}

/* ============================================================
   14) INIT
============================================================ */
function applyExistingSession(){
  const s = lsGet(STORE_KEYS.session, null);
  if(s && s.role){ session = s; enterApp(); }
  else { $("screen-login").classList.add("active"); }
}

function showFatalError(msg){
  let box = document.getElementById("fatal-err-box");
  if(!box){
    box = document.createElement("div");
    box.id = "fatal-err-box";
    box.style.cssText = "position:fixed;inset:0;z-index:99999;background:#0a0e23;color:#fff;direction:rtl;"
      +"font-family:Vazirmatn,sans-serif;padding:22px;overflow:auto;line-height:1.9;";
    document.body.appendChild(box);
  }
  box.innerHTML = `
    <div style="font-size:17px;font-weight:800;color:#f1626a;margin-bottom:10px;"><i class='fas fa-triangle-exclamation'></i> خطایی در اجرای برنامه رخ داد</div>
    <div style="font-size:13px;color:#aab0d4;margin-bottom:16px;">لطفاً متن زیر را برای رفع مشکل ارسال کنید:</div>
    <pre style="white-space:pre-wrap;word-break:break-word;background:rgba(255,255,255,.06);border-radius:12px;padding:14px;font-size:12px;color:#fff;">${escapeHtml(String(msg))}</pre>
    <button onclick="localStorage.clear();location.reload();" style="margin-top:18px;background:#c9a84c;color:#1a1304;border:none;border-radius:12px;padding:12px 18px;font-weight:700;font-size:13.5px;">پاک‌سازی داده‌های محلی و تلاش مجدد</button>
  `;
}
window.addEventListener("error", e=>{ showFatalError((e.error && e.error.stack) || e.message); });
window.addEventListener("unhandledrejection", e=>{ showFatalError(e.reason); });

window.addEventListener("DOMContentLoaded", ()=>{
  try{
    loadAll();
    setupHiddenAdminGesture();
    applyExistingSession();
    registerSW();
  }catch(err){
    showFatalError(err.stack || err.message || err);
  }
});
window.addEventListener("resize", ()=>{
  // re-render active chart-bearing tab to keep canvases crisp on rotate
  if(session.role==="admin"){
    if($("tab-a-dashboard").classList.contains("active")) renderAdminDashboard();
  } else if(session.role==="viewer"){
    if($("tab-v-dashboard").classList.contains("active")) renderViewerDashboard();
  }
});

function renderNotifList(){
  $("notif-list").innerHTML = DB.notifs.length ? DB.notifs.map(n=>`
    <div class="row-item">
      <div class="row-avatar" style="background:rgba(255,255,255,.05);">${n.icon}</div>
      <div class="row-main">
        <div class="row-title" style="font-size:13px;font-weight:600;">${escapeHtml(n.text)}</div>
        <div class="row-sub">${fmtDateShort(n.ts)}</div>
      </div>
    </div>`).join("") : emptyState("<i class='fas fa-bell'></i>","اعلانی وجود ندارد");
  DB.notifs.forEach(n=>n.read=true); saveAll();
  $("notif-dot").classList.remove("show");
}
document.addEventListener("click", e=>{
  if(e.target.closest('[onclick*="modal-notifs"]')) renderNotifList();
});

function registerSW(){
  if("serviceWorker" in navigator){
    window.addEventListener("load", ()=>{
      navigator.serviceWorker.register("sw.js").catch(()=>{});
    });
  }
}
