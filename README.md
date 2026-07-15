# 🔇 Quietly

> A privacy-first Android app-importance and optimization assistant.
> No cloud. No accounts. No ads. Your data stays on your device.

---

## What it does

Quietly is not a screen-time tracker. It is an **app-importance assistant** — it analyses 90 days of your usage behaviour, applies a scoring model, and tells you which apps are genuinely essential, which are optional, and which are safe to remove or limit.

| Feature | Detail |
|---|---|
| **90-day importance engine** | Scores every app 0–100 based on usage time, active days, session frequency, recency, and category |
| **Importance labels** | Essential · Useful · Optional · Low value — each with an explanation string |
| **Protected categories** | Finance, Banking, Security, and Productivity apps always score ≥ 70 and are excluded from removal suggestions |
| **Split recommendations** | **Remove** (low importance, low recency) and **Limit** (high usage, distraction-prone) are separate lists — never conflated |
| **Reason strings** | Every recommendation includes a plain-English explanation: *"Unused for 68 days; category: Games; low importance."* |
| **Per-app overrides** | Mark any app as Essential, Focus Drain, Ignore, or Exclude — the engine adapts |
| **Usage dashboard** | See every app's screen time for today, this week, or this month |
| **Per-app goals** | Set a daily time limit; get a local notification at 90 % |
| **Encrypted storage** | Room DB (v4) + Android Keystore-backed `EncryptedSharedPreferences` (AES-256-GCM) |
| **No network by default** | `INTERNET` permission is **not requested** for core features; online metadata is strictly opt-in |
| **Optional metadata** | A Settings toggle (default OFF) enables minimal online app-category enrichment with local caching |

---

## Importance score

The engine computes a 0–100 score per app:

| Component | Max pts | Signal |
|---|---|---|
| Total foreground time | 30 | Avg daily ms, capped at 2 h/day |
| Active days | 25 | Days with any usage in 90-day window |
| Session frequency | 20 | Avg launches per active day |
| Recency | 15 | Linear decay from 0 to 90 days since last use |
| Category base value | 10 | Finance = 10 · Productivity = 8 · Social = 4 · Games = 2 |

Protected categories (Finance, Security, Accessibility) receive a score floor of **70** regardless of usage, and are never shown in Remove lists.

---

## Architecture

```
app/
├── data/
│   ├── db/                ← Room v4: AppUsageEntity, GoalEntity, AppOverrideEntity
│   ├── repository/        ← UsageRepositoryImpl (90-day queries, overrides)
│   ├── source/            ← UsageStatsSource (reads UsageStatsManager)
│   └── prefs/             ← SecurePreferences (retention, analysis window, online toggle)
├── di/                    ← Hilt: DatabaseModule, RepositoryModule
├── domain/
│   ├── ImportanceEngine.kt ← Pure scoring logic (no Android deps)
│   ├── UserOverride.kt     ← (co-located in ImportanceEngine.kt)
│   └── repository/        ← UsageRepository interface
├── ui/
│   ├── components/        ← Shared Compose components
│   ├── dashboard/         ← Today screen
│   ├── apps/              ← Full app list
│   ├── goals/             ← Daily limit manager
│   ├── insights/          ← Scored insights: Remove / Limit / Protected / All apps
│   ├── onboarding/        ← Usage-access permission gate
│   ├── settings/          ← Retention, analysis window, online metadata toggle
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
3. Tap **I've granted it — continue**
4. After a few days of data collection, open **Insights** for scored recommendations

---

## Privacy

- `android.permission.PACKAGE_USAGE_STATS` — reads screen time locally only
- `android.permission.INTERNET` is **not requested** unless the optional online metadata toggle is enabled in Settings
- `allowBackup="false"` and full cloud-backup exclusion rules prevent data leaking via Android backups
- All preferences are stored in AES-256-GCM `EncryptedSharedPreferences` (Android Keystore-backed)
- The Room database stores only aggregated daily usage rows; no content, messages, or personal data is ever read
- Online metadata lookup (opt-in, default OFF): requests are minimal, results are cached locally, nothing is sent to third parties

---

## Database migrations

| Version | Change |
|---|---|
| 1 → 2 | Added `launchCount` to `app_usage` |
| 2 → 3 | Added `category` to `app_usage`; `appLabel` + `reminderEnabled` to `goals` |
| 3 → 4 | Added `lastSeenEpochDay` to `app_usage`; created `app_overrides` table |

---

## CI / CD

GitHub Actions (`ci.yml`) runs on every push to `main`:

1. Runs unit tests first (`testDebugUnitTest`)
2. Builds the debug APK (`assembleDebug`)
3. Uploads the APK as a workflow artifact (retained 30 days)
4. Creates a versioned GitHub Release with the APK attached

Release tags follow the pattern `v{versionName}-build.{run_number}-{short_sha}` to guarantee uniqueness.

---

*Made by Vagif Novruzov*
