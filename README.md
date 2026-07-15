# 🔇 Quietly

> A privacy-first, offline-only Android app-usage auditor.
> No cloud. No accounts. No ads. Just you and your data.

---

## What it does

| Feature | Detail |
|---|---|
| **Usage dashboard** | See every app’s screen time for today, this week, or this month |
| **Per-app goals** | Set a daily time limit per app; get a local notification at 90% |
| **Insights** | 7-day top-5 ranking and “suggest to remove” if avg > 2 h/day |
| **Encrypted storage** | Room DB + Android Keystore-backed `EncryptedSharedPreferences` |
| **No network** | Zero internet permission; app metadata resolved from the system PackageManager |

---

## Architecture

```
app/
├── data/
│   ├── db/                ← Room entities, DAOs, QuietlyDatabase
│   ├── repository/        ← UsageRepositoryImpl, GoalRepositoryImpl
│   ├── source/            ← UsageStatsSource (reads UsageStatsManager)
│   └── prefs/             ← SecurePreferences (EncryptedSharedPreferences)
├── di/                    ← Hilt modules
├── domain/repository/     ← Interfaces consumed by ViewModels
├── ui/
│   ├── components/        ← Shared Compose components
│   ├── dashboard/         ← Today screen
│   ├── apps/              ← Full app list
│   ├── goals/             ← Daily limit manager
│   ├── insights/          ← 7-day trends & recommendations
│   ├── onboarding/        ← Usage-access permission gate
│   ├── settings/          ← Retention window slider
│   └── navigation/        ← NavGraph
└── worker/                ← DailySyncWorker + GoalReminderWorker
```

**Stack:** Kotlin · Jetpack Compose · Hilt · Room · WorkManager · AndroidX Security-Crypto

---

## Getting started

```bash
git clone https://github.com/Razor573/Quietly.git
cd Quietly
./gradlew :app:assembleDebug
```

Install the APK from `app/build/outputs/apk/debug/app-debug.apk`, then:

1. Open Quietly
2. Tap **Grant Usage Access** and enable it in Settings
3. Tap **I’ve granted it — continue**

---

## Privacy

- `android.permission.PACKAGE_USAGE_STATS` — reads screen time locally
- `android.permission.INTERNET` is **not requested**
- `allowBackup="false"` and full cloud-backup exclusion rules prevent data leaking via Android backups
- All prefs are stored in AES-256-GCM `EncryptedSharedPreferences`

---

## CI

GitHub Actions builds the debug APK and runs unit tests on every push to `main`.
Artifact is uploaded and retained for 14 days.

---

*Made by Vagif Novruzov*
