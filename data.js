/* ============================================================
   SEED DATA — گزارش منتورینگ / آکادمی سبحان صمدی
   این فایل فقط مقادیر پیش‌فرض اولیه (seed) را نگه می‌دارد.
   تمام داده‌های واقعی برنامه در localStorage ذخیره و مدیریت می‌شوند
   تا هیچ گزارشی از دست نرود و آرشیو همیشه پایدار بماند.
============================================================ */

const APP_VERSION = "2.0.0";

// رمزهای ورود (قابل تغییر از پنل تنظیمات مدیر)
const DEFAULT_CREDENTIALS = {
  viewerPass: "sobhan1404",
  adminPass:  "mentor1404"
};

// حداکثر مرخصی سالانه هر منتور (روز)
const MAX_LEAVE_DAYS = 15;

// لیست اولیه منتورها — قابل افزودن/ویرایش/حذف از پنل مدیریت
const SEED_MENTORS = [
  { id:"m1", name:"مهدی حسینی",      role:"سرمنتور", active:true },
  { id:"m2", name:"ابوالفضل محمودی",  role:"منتور",    active:true },
  { id:"m3", name:"سبحان غفاری",      role:"منتور",    active:true },
  { id:"m4", name:"سعید نظری",        role:"منتور",    active:true },
];

// مقادیر پیش‌فرض اولین گزارش هفتگی هر منتور (برای راحتی ثبت سریع)
const SEED_REPORT_DEFAULTS = {
  "m1":{ startTime:"8:30",  messages:90,  newReplies:3, scientific:10, clarity:9,  tone:9, education:10, followup:10, extra:9, note:"مدیریت عملکرد منتورینگ - پیگیری اپ و اکسل" },
  "m2":{ startTime:"9:00",  messages:100, newReplies:2, scientific:9,  clarity:9,  tone:8, education:10, followup:10, extra:10,note:"تلگرام پشتیبانی ۱۵۷" },
  "m3":{ startTime:"9:30",  messages:130, newReplies:4, scientific:9,  clarity:10, tone:8, education:10, followup:10, extra:9, note:"تماس برای استخدام - سایت" },
  "m4":{ startTime:"9:00",  messages:100, newReplies:3, scientific:9,  clarity:9,  tone:8, education:10, followup:10, extra:8, note:"کمک به تلگرام ابوالفضل - بررسی استیتمنت‌های طلا" },
};

// نمونه پروژه اولیه (ساختار قابل توسعه)
const SEED_PROJECTS = [
  {
    id:"p1",
    title:"بازطراحی اپلیکیشن گزارش منتورینگ",
    description:"ساخت نسخه PWA حرفه‌ای برای ثبت و آرشیو گزارش‌های هفتگی، مرخصی‌ها و پروژه‌ها.",
    owner:"مهدی حسینی",
    status:"in_progress", // todo | in_progress | review | done
    priority:"high",      // low | mid | high
    progress:40,
    startDate:"",
    deadline:"",
    notes:"",
    createdAt:Date.now()
  }
];
