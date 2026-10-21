# پایگاه داده

Room، فایل `fitos.db` در حافظه‌ی خصوصی اپ. کل Schema نسخه‌ی ۱ از مرحله ۱ ساخته شده و هر بخش در مرحله‌ی خودش جدول‌هایش را پر می‌کند.
Schema هر نسخه در `core/data/schemas/` ثبت و در تست‌ها بررسی می‌شود.

## قراردادها

- شناسه‌ها UUID متنی‌اند (پروفایل یک ردیف ثابت با شناسه‌ی `self` است).
- زمان‌ها میلی‌ثانیه‌ی UTC هستند، همراه با `zoneId` هرجا روز محلی مهم است.
- روز تقویمی (`day`، `effectiveFrom` و…) همان `LocalDate.toEpochDay()` است که **در لحظه‌ی ثبت** ذخیره می‌شود، تا تغییر قوانین منطقه‌ی زمانی هیچ ثبتی را به روز دیگر نبرد.
- واحدها ثابت‌اند: کیلوگرم، سانتی‌متر، گرم، میلی‌لیتر، ثانیه.
- enumها با کد ثابت انگلیسی کوچک ذخیره می‌شوند (مثلاً `fat_loss`)، نه نام کلاس.
- مقدار نامعلوم `null` است، نه صفر.
- ردیف‌های مهم با `deletedAt` حذف نرم می‌شوند.
- ستون `source` (`manual`، `import`، `health_connect`) روی همه‌ی ثبت‌ها هست.

## جدول‌ها (نسخه ۱)

| حوزه | جدول‌ها |
|---|---|
| پروفایل و هدف | `profile`، `nutrition_target`، `weight_goal` |
| تمرین | `exercise`، `exercise_muscle`، `program`، `program_day`، `day_variant`، `planned_exercise`، `progression_rule`، `program_change_log`، `workout_session`، `session_exercise`، `set_log`، `personal_record` |
| تغذیه | `food`، `food_portion`، `recipe_ingredient`، `food_log`، `saved_meal`، `saved_meal_item`، `meal_plan`، `meal_plan_item` |
| بدن | `weight_entry`، `measurement_site`، `body_measurement`، `progress_photo` |
| عادت‌ها | `habit`، `habit_log`، `water_log`، `sleep_log`، `activity_log`، `reminder` |
| سیستم | `weekly_report`، `return_plan_event`، `consent` |

جزئیات هر ستون در توضیحات کلاس‌های `core/data/.../db/entity/` نوشته شده است.

- گزارش هفتگی (مرحله ۸) ذخیره نمی‌شود و هر بار از داده‌ها ساخته می‌شود؛ جدول `weekly_report` خالی می‌ماند و برای نسخه‌ای رزرو است که بخواهد عکس فوری گزارش‌ها را نگه دارد.
- `nutrition_target.assumptionsJson` از نسخه‌ی ۰٫۹٫۰ برای هدف‌هایی که از بررسی گزارش هفتگی آمده‌اند چهار فیلد اختیاری دارد (`adaptiveDays`، `adaptiveIntake`، `adaptiveRate`، `adaptivePreviousKcal`). نسخه‌های قبلی این فیلدها را نادیده می‌گیرند، پس Schema عوض نشده است.
- تنظیمات گزارش (آخرین هفته‌ی دیده‌شده، هفته‌ای که پیشنهادش کنار گذاشته شد) در DataStore هستند، نه در پایگاه داده.

## تغییر Schema

1. نسخه را در `FitDatabase.VERSION` بالا ببر و یک `Migration` به `Migrations.ALL` اضافه کن.
2. Build کن تا `schemas/…/<نسخه>.json` ساخته شود و آن را commit کن.
3. در `PersistenceTest` یک تست `runMigrationsAndValidate` برای نسخه‌ی جدید بنویس.

هرگز `fallbackToDestructiveMigration` استفاده نمی‌شود.

## کپی پیش از ارتقا

`DatabaseUpgradeGuard` پیش از باز کردن پایگاه داده، نسخه‌ی فایل را از سرآیند SQLite می‌خواند (بدون باز کردن آن). اگر قرار است ارتقا انجام شود، فایل‌های `fitos.db` و `-wal` و `-shm` را در پوشه‌ی `databases/pre-upgrade/` کپی می‌کند. سه کپی آخر نگه داشته می‌شوند.
