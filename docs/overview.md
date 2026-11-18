# Project overview

## The goal

**For the user:** every rupee spent is captured automatically, deduplicated, and categorized. There is a quick way to record *what it was for*, and everything is visible in an app on the iPhone (and on Android, for friends).

**For the builder:** one realistic project covering backend engineering, data integrity, multi-user security, containers, cloud deployment, infrastructure as code, CI/CD, observability and, later, AI infrastructure. It is meant as a portfolio piece on the path to Platform / Cloud Infrastructure Engineering.

## The core problem

Banks tell you about a transaction in several places, none of them complete or reliable on its own:

| Source | Strength | Weakness |
|---|---|---|
| SMS alert | Instant | Can't be read by iPhone apps; formats differ per bank; sometimes missing |
| Email alert | Reliable, often has more detail | Sometimes delayed; needs Gmail access |
| Monthly statement | Complete and authoritative | Arrives once a month |

So the system has to:
1. **Capture** from every source it can.
2. **Parse** each bank's format into structured data.
3. **Deduplicate**: the same payment may arrive three times.
4. **Categorize**: "SWIGGY" means Food.
5. **Reconcile** against statements to catch anything missed.
6. **Present** it, and let the user annotate *what it was for*.

## Who uses it

- **Primary:** the owner, on an **iPhone**. Capture is the iOS Shortcut plus email plus statements. The app is the Expo web build installed to the home screen.
- **Secondary (later):** a few invited friends, mostly on **Android**. Capture is a native SMS and notification reader in the APK. The app is the Android APK, installed directly (not through the Play Store).

## Status (2026-11-18)

> **Direction change:** the backend is being ported from Java to **Node + TypeScript** ([ADR-0009](decisions/0009-node-typescript-fastify.md)) on the `node-port` branch, with free hosting ([ADR-0010](decisions/0010-free-hosting.md)). The owner now focuses on DevOps + AI/LLM: the AI writes the application code, the owner builds the infrastructure. The Java version lives on the `archive/java-v0` branch. 


| Area | State |
|---|---|
| Backend (Node port of Phases 1–2): ingest endpoint, parser framework, dedup, categorization | ✅ Built on `node-port`, 72 tests passing; awaiting review + merge |
| Category seed data | ✅ Done (13 categories, 21 merchant rules) |
| Bank-specific parsers: HDFC, SBI, OneCard | ⏳ Waiting for masked sample SMS |
| CI pipeline (Step 2) | Next: owner's hands-on step |
| Multi-user + Google sign-in (Step 3) | 📝 Planned, see [plan](plans/phase-2.5-multi-user.md) |
| Everything else | See the [roadmap](roadmap.md) |

## How this is being built

- Code is written in a **Claude Code cloud session**: a temporary Linux container that runs the tests (including real Postgres via Testcontainers) and pushes to GitHub. **GitHub is the durable copy**; the container can be discarded at any time.
- The owner reviews each step, runs it locally, and builds the **infrastructure** by hand (CI, deploys, Terraform, Kubernetes, observability), marked `TODO(santosh)` where relevant.
- Work proceeds phase by phase. Each phase stops for review before the next one starts.
