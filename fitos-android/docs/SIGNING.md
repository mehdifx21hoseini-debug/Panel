# کلید امضا

اندروید فقط وقتی نسخه جدید را روی نسخه قبلی نصب می‌کند که هر دو با **یک کلید** امضا شده باشند.
اگر کلید گم شود، نسخه جدید نصب نمی‌شود و باید اپ قبلی را حذف کرد (یعنی از دست رفتن داده‌ها، مگر پشتیبان داشته باشید).

## فایل‌ها

- `fitos-release.jks`: خود کلید (RSA 4096، نام مستعار `fitos`، اعتبار ۱۰۰ سال)
- `keystore.properties`: مسیر و رمزها

هیچ‌کدام در مخزن git نیستند (`.gitignore`). دو نسخه نگه دارید: یکی پیش خودتان (مثلاً گوگل‌درایو یا فلش، جدا از گوشی) و یکی در GitHub Secrets.

## ساخت محلی

`keystore.properties` را کنار `settings.gradle.kts` بگذارید و مسیر `storeFile` را درست کنید:

```properties
storeFile=/path/to/fitos-release.jks
storePassword=...
keyAlias=fitos
keyPassword=...
```

## GitHub Secrets

در مخزن: Settings ← Secrets and variables ← Actions ← New repository secret. این چهار مورد را بسازید:

| نام | مقدار |
|---|---|
| `FITOS_KEYSTORE_BASE64` | خروجی `base64 -w0 fitos-release.jks` |
| `FITOS_KEYSTORE_PASSWORD` | `storePassword` |
| `FITOS_KEY_ALIAS` | `fitos` |
| `FITOS_KEY_PASSWORD` | `keyPassword` |

بعد از آن، workflow نسخه امضاشده می‌سازد. بدون این‌ها، APK بدون امضا ساخته می‌شود که قابل نصب نیست.
