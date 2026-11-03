# Kosh: *every rupee, accounted for.*

Kosh is a personal finance app for Android. It tracks every expense, bank balance, EMI and loan in one place, and can capture UPI and bank transactions automatically from SMS. It works offline, backs up privately to your own account, and can mirror everything into a Google Sheet.

Built with Kotlin and Jetpack Compose. The design follows an ink-and-ember style: warm dark surfaces, an ember-orange accent, Instrument Serif headings, Geist for text and Geist Mono for labels.

---

## Features

| Area | What it does |
|---|---|
| **Expenses & income** | Amount, category, account, date and time, payee, note, payment mode. Search, filters, a day-grouped timeline, swipe to delete with undo, duplicate, and "make recurring". |
| **Accounts** | Bank, savings, cash, credit card, UPI wallet and investment accounts. Balances are computed from transactions. Transfers. "Update balance" matches the real bank balance without affecting reports. |
| **Net worth** | Assets (balances and money lent) minus liabilities (card dues, money borrowed, EMI principal left), with a breakdown. |
| **EMIs** | Reducing-balance EMI calculator, full repayment schedule, paid and remaining amounts, principal outstanding, overdue warnings, optional auto-pay on the due date, reminders with a "Mark paid" button. |
| **Loans** | Money lent and borrowed, per person. Repayments and top-ups, simple-interest accrual, due dates, settle or reopen, and a "Send reminder" share message. |
| **Recurring & bills** | Rent, subscriptions, SIPs, salary and so on, weekly to yearly. Each one either adds itself on the date or reminds you with Paid / Skip buttons. One-tap starters. |
| **Budgets** | Overall and per-category monthly budgets, safe-to-spend per day, a month-end projection, and alerts at 80% and 100%. |
| **SMS auto-detect** | Reads bank, card and UPI SMS on the device and records each transaction automatically. The category is learned from the merchant or guessed from keywords. The notification has **Change** and **Undo**, and possible duplicates go to a review inbox. OTPs, promotions, payment requests and reminders are ignored. |
| **Reports** | Category donut with budgets, 6-month income vs. expense chart, daily spending, top payees, savings rate. |
| **Home-screen widget** | This month's spend, quick-category shortcuts, and +Expense / +Income / Transfer buttons. A quick-add panel opens over the home screen with its own number keypad. Each widget has its own settings: account, categories, theme, opacity, fingerprint. |
| **Account & cloud backup** | Email/password or Google sign-in (Firebase Auth). Every change backs up to Cloud Firestore within seconds, offline changes queue, and signing in on a new phone restores everything. |
| **Google Sheets sync** | Optional one-way sync of every record to your own Google Sheet through a small Apps Script you deploy yourself. |
| **Excel export** | `.xlsx` (one tab per data type) or transactions `.csv`, for any period. |
| **Privacy & security** | Fingerprint / device-PIN app lock. SMS text never leaves the phone. Firestore rules give each account access to its own data only. |
| **Polish** | 2-second animated logo on launch, dark mode by default (light mode available), and text that adapts to narrow screens and large font sizes. |

---

## Tech stack

- **Kotlin 2.0**, **Jetpack Compose** (Material 3), single-activity Navigation
- **Room** (SQLite) with migrations, the source of truth, offline-first
- **WorkManager** for sync, reminders, recurring posting and the daily log
- **Jetpack Glance** for the home-screen widget
- **Firebase Auth** + **Cloud Firestore** for the account and backup
- **Credential Manager** for Google sign-in
- **DataStore** for settings, **BiometricPrompt** for the app lock
- No other third-party libraries. Charts, the `.xlsx` writer and the SMS parser are written in the project.

Money is stored as `Long` minor units (paise), so there are no floating-point rounding errors.

---

## Project structure

```
app/src/main/java/com/neuralroots/kosh/
├── KoshApp.kt, MainActivity.kt   # app container (manual DI), single activity
├── data/
│   ├── db/        # Room entities, DAOs, database + migrations
│   ├── repo/      # FinanceRepository: every read and write goes through here
│   └── prefs/     # DataStore settings
├── cloud/         # Firebase auth, Firestore mapper, two-way sync + worker
├── sync/          # Google Sheets (Apps Script) sync
├── export/        # dependency-free .xlsx / .csv writer
├── sms/           # SMS parser, receiver, category guessing, notifications
├── recurring/     # recurring bills worker + notification actions
├── budget/        # budget alerts, daily logging reminder
├── reminder/      # EMI and loan reminders
├── widget/        # Glance widget, quick-add panel, widget settings
├── security/      # app lock
├── util/          # money, dates, EMI maths
└── ui/
    ├── theme/       # colours, fonts, responsive type scale
    ├── components/  # shared components (cards, charts, FitText, …)
    ├── nav/         # routes + nav host
    └── screens/     # home, transactions, accounts, emi, loans, recurring,
                     # budget, reports, inbox, settings, auth, splash, more
```

Every write goes through `FinanceRepository`, which runs it in a database transaction and queues the changed rows for upload (Firestore and, if configured, Google Sheets). It also refreshes the widget and checks budgets.

---

## Build

Requirements: **JDK 17**, Android SDK 35 (Android Studio or command-line tools).

```bash
# 1. Point Gradle at the SDK
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 2. Firebase config: create a Firebase project with Authentication
#    (Email/Password + Google) and Firestore, add an Android app with the
#    package name below, and put its google-services.json in app/.

# 3. Build
./gradlew assembleDebug              # debug APK
./gradlew testDebugUnitTest          # unit tests (SMS parser)
```

**Release builds** are signed with a local keystore described in `keystore.properties` (not committed):

```properties
storeFile=kosh-release.jks
storePassword=…
keyAlias=kosh
keyPassword=…
```

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

For Google sign-in, add the SHA-1 of your signing key to the Firebase Android app.

Package name: `com.neuralroots.kosh` · min SDK 26 (Android 8.0) · target SDK 35.

---

## Firebase setup

1. Create a Firebase project and enable **Authentication → Email/Password** and **Google**.
2. Create a **Cloud Firestore** database.
3. Deploy the security rules in [`firestore.rules`](firestore.rules). They give each signed-in user read/write access to `users/{uid}/…` only:
   ```bash
   firebase deploy --only firestore:rules
   ```
4. Add an Android app (`com.neuralroots.kosh`) with your signing SHA-1, download `google-services.json` into `app/`, and rebuild.

Data layout: `users/{uid}/{accounts | categories | transactions | emis | emi_payments | people | loans | loan_entries | recurring}/{uuid}`. Records refer to each other by uuid, so a restore rebuilds everything on a fresh install. Deletions sync as tombstones, and the newest `updatedAt` wins.

---

## Google Sheets sync (optional)

1. Open a new sheet (sheets.new) → **Extensions → Apps Script**.
2. In the app: **More → Google Sheets sync → Copy script**, then paste it in place of the sample code. The script already includes your private key.
3. **Deploy → New deployment → Web app**, with *Execute as: Me* and *Who has access: Anyone*, then authorise.
4. Paste the web-app URL into the app and tap **Test connection**.

Each data type gets its own tab, plus a *Summary* tab with monthly totals. The script source is in [`apps-script/Code.gs`](apps-script/Code.gs).

---

## SMS auto-detect

Turn it on in **Settings → Auto-detect from SMS** (needs the SMS permission).

- Only bank, card and UPI alerts from bank sender IDs are read. Personal messages, OTPs, promotions, payment requests, upcoming mandates and failed transactions are skipped.
- The amount, debit or credit, account last 4 digits, merchant/UPI ID and reference number are extracted. The account is matched by its **last 4 digits** (set these on each account).
- **Record automatically** (default) saves the transaction straight away. Turn it off to review each one in **More → Detected from SMS**.
- Parser tests: `app/src/test/java/com/neuralroots/kosh/sms/SmsParserTest.kt`.

---

## License

Fonts: Geist and Geist Mono (Vercel), and Instrument Serif. All are under the SIL Open Font License.
