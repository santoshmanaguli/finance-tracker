# Capturing transactions on an iPhone

## Can an iPhone app read SMS?

**No.** Apple blocks it deliberately, for privacy. No App Store app, React Native app, Flutter app or paid developer account can read your SMS or another app's notifications. Even Apple's SMS spam-filter extension runs in a sealed sandbox: it can classify a message but cannot store or forward it.

## What we do instead

| Route | How | Automatic? | Phase |
|---|---|---|---|
| **Shortcuts automation** | iOS runs a personal automation when an SMS containing a keyword arrives, and posts it to our backend | ✅ Apple's one sanctioned opening | 2 ([guide](ios-shortcut.md)) |
| **Bank email alerts** | Enable email alerts in each bank's net banking; the backend reads them from Gmail | ✅ | 6 |
| **Monthly statements** | Upload the PDF or CSV; reconciliation finds anything missed | Monthly | 5 |
| **Manual quick entry** | Cash and edge cases | Manual | 4 |
| **Account Aggregator (RBI)** | Consent-based bank data sharing | ✅ | ❌ Only regulated entities can be data users; not available to individuals |

Three independent sources (SMS, email, statement), deduplicated, give reliability that no single route can: Shortcuts can break after iOS updates, emails get delayed, statements are monthly.

## How do App Store finance apps work?

**Distribution.** Developers join the **Apple Developer Program** (USD 99/year), build in Xcode, distribute test builds through **TestFlight**, then pass **App Review** to publish. iPhones install only Apple-signed apps; there is no APK equivalent (except limited alternative stores in the EU).

**Data.** This is the general industry pattern, not any specific app's internals:

| Source | Used by |
|---|---|
| Reading SMS | **Android only.** Indian expense apps that auto-read SMS do it on their Android versions |
| Account Aggregator | Regulated companies (banks, NBFCs, wealth platforms) with user consent |
| Email parsing | Apps that passed Google's restricted-scope verification (a paid security assessment) |
| Statement upload, manual entry | Almost all, as a fallback |
| Open-banking APIs | Other countries (Plaid in the US, PSD2 in the EU/UK) |

**Inside, they look like this project:** a thin mobile app → backend API → ingestion from several sources → parsing → deduplication → categorization (rules + ML) → database → dashboards and alerts. Production fintechs add regulatory compliance, audits and fraud controls on top.
