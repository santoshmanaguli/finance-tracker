# iOS Shortcut: forward bank SMS to the backend

iOS doesn't let apps read SMS, but a **personal automation** in the Shortcuts app can run when a message arrives and pass that message along. The phone just forwards the text, and all the parsing happens on the server.

> **Before you start:** the backend has to be reachable over **HTTPS** from your phone. That happens in Phase 7 (AWS). Until then, test with `curl` (see the end of this page) or use a tunnel (discussed in the Phase 2 summary).
>
> Menu names below are from iOS 17/18. Apple renames things now and then, so look for the closest match if something has moved.

## Design: one worker Shortcut, several thin triggers

The trigger condition "Message Contains" accepts **one keyword per automation**. To catch "debited", "credited", "spent" and "UPI" you therefore need several automations. To keep things manageable:

- **One normal Shortcut**, called `Forward Bank SMS`, holds the URL, the token and the HTTP call.
- **Each automation** has a single action: *Run Shortcut → Forward Bank SMS*.

When you rotate the token or change the URL, you edit one place. The software principle here is called DRY ("don't repeat yourself").

An SMS that contains both "UPI" and "debited" fires two automations and gets sent twice. That's fine: the backend's content-hash dedup (layer 1) answers the second request with `ALREADY_RECEIVED`.

## Step 1: generate the token (on your laptop)

```bash
openssl rand -base64 48
```
Put the output in `.env` as `INGEST_API_TOKEN=...` and restart the app. Treat it like a password.

## Step 2: create the worker Shortcut `Forward Bank SMS`

1. Open **Shortcuts → Shortcuts tab → +**. Name it **Forward Bank SMS**.
2. Tap the **(i)** / details button and turn on **Receive input from: Shortcuts** (sometimes shown as "Use as Quick Action" / "Receive What's On Screen", depending on the iOS version). Set the accepted type to **Messages** (or *Anything*).
3. Add the action **Get Contents of URL**.
   - **URL:** `https://<your-host>/api/ingest/sms`
   - Tap **Show More**:
     - **Method:** `POST`
     - **Headers → Add new header:** key `X-Api-Token`, value `<your token>`
     - **Request Body:** `JSON`. Add three fields, all of type **Text**:

       | Key | Value (tap the field, then pick the variable) |
       |---|---|
       | `sender` | **Shortcut Input** → tap it → choose **Sender** |
       | `body` | **Shortcut Input** → tap it → choose **Content** |
       | `receivedAt` | **Current Date** → tap it → **Date Format: ISO 8601**, *Include Time* on |

     Choosing JSON makes Shortcuts set `Content-Type: application/json` for you.
4. Optional, but useful while debugging: add **Show Result** after it, so you can see the server's JSON reply. Remove it once everything works, because otherwise you'll get a popup for every SMS.

## Step 3: create one automation per keyword

Repeat for each of: `debited`, `credited`, `spent`, `UPI`, and later any word your banks use (`withdrawn`, `txn`...).

1. **Shortcuts → Automation tab → + (New Automation) → Message.**
2. **Message Contains:** type the keyword. Leave **Sender** empty, because bank sender IDs (AX-HDFCBK) aren't contacts.
3. Select **Run Immediately**. This is the key setting: without it, iOS asks for confirmation every time.
   Turn **Notify When Run** off if you don't want a banner for every SMS.
4. **Next → New Blank Automation → Add Action → Run Shortcut**. Choose **Forward Bank SMS**, and set its input to **Shortcut Input**.
5. Tap **Done**.

## Step 4: verify on the device

1. Have someone send you an SMS containing "debited", or wait for a real one.
2. With *Show Result* still on, you should see JSON like
   `{"status":"CREATED", ...}`, or `UNPARSED` for formats we can't parse yet.
3. **Check the value of `sender`.** This is the one thing that can only be confirmed on a real phone. For bank sender IDs it should look like `AX-HDFCBK` or `JD-HDFCBK-S`. If it arrives empty or as something unexpected, tell me what you see, because parser routing depends on it.

## Security notes

- **Never share this Shortcut** through iCloud links or the share sheet. The exported Shortcut contains your token in plain text. If you suspect it leaked, generate a new token and update `.env` and the Shortcut.
- The token only grants **write access to ingest**. It can't read any of your data.
- Always use HTTPS. Over plain HTTP, anyone on the same Wi-Fi could read both the token and your bank SMS.

## Testing without the phone

```bash
curl -i https://<your-host>/api/ingest/sms \
  -H "X-Api-Token: $INGEST_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sender":"AX-HDFCBK","body":"<a masked sample SMS>","receivedAt":"2026-11-04T10:16:00+05:30"}'
```
