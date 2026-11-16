# ADR-0003: Thin clients; all parsing on the server

- **Status:** Accepted
- **Date:** 2026-11-03

## Context

Messages arrive from several clients: the iOS Shortcut, a future Android capture module, and Gmail polling. Banks change SMS formats without notice.

## Decision

Clients **only forward raw text** (sender, body, time, source). Parsing, deduplication and categorization happen **on the server**.

## Consequences

- ➕ A parser fix ships once, on the server, and applies to every user at once, with no app update or re-sideloading APKs.
- ➕ Clients stay trivial: the iOS Shortcut has a single HTTP action.
- ➕ Raw text is preserved, so improved parsers can **re-parse history** (the `UNPARSED` inbox).
- ➖ Raw message text travels to and is stored on the server, so HTTPS and at-rest protection are mandatory. The Android notification capture must filter on the device by app allowlist, so non-financial notifications never leave the phone.
- ➖ Capture needs network access; the Android module must queue messages offline and retry later. Retries are safe because of dedup layer 1.
