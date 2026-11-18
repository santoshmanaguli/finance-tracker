import { IST_OFFSET } from "../../src/common/clock.ts";
import { toPaise } from "../../src/ingest/parsers/amounts.ts";
import { parsedTransaction, type SmsParser } from "../../src/ingest/parsers/types.ts";

/**
 * Test-only parsers for a FICTIONAL bank, so the whole pipeline (HTTP -> parse -> dedup ->
 * categorize -> DB) is tested without guessing real bank formats. The real HDFC / SBI / OneCard
 * parsers will be built from masked sample SMS.
 *
 * Format: "Rs 1,234.50 debited from Card XX1234 at SWIGGY on 04-11-2026 10:15. Ref 123456789012"
 */
const TXN =
  /Rs\.?\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?<dir>debited|credited)\s+(?:from|to)\s+Card\s+XX(?<last4>\d{4})(?:\s+at\s+(?<merchant>.+?))?\s+on\s+(?<d>\d{2})-(?<m>\d{2})-(?<y>\d{4}) (?<time>\d{2}:\d{2})(?:\.\s*Ref\s+(?<ref>\w+))?/i;

export const fakeBankParser: SmsParser = {
  name: "FakeBankParser",
  senderCodes: ["FAKEBK"],
  parse(body) {
    if (body.toLowerCase().includes("offer")) {
      return { kind: "ignored", reason: "promotional" };
    }
    const g = TXN.exec(body)?.groups;
    if (!g) {
      return { kind: "failed", reason: "FakeBank: no transaction pattern matched" };
    }
    // Bank SMS times are IST wall-clock times; convert to an absolute instant.
    const when = new Date(`${g.y}-${g.m}-${g.d}T${g.time}:00${IST_OFFSET}`);
    return parsedTransaction({
      institution: "FAKEBANK",
      accountType: "CREDIT_CARD",
      last4: g.last4 ?? "",
      amountPaise: toPaise(g.amount ?? ""),
      direction: g.dir?.toLowerCase() === "debited" ? "DEBIT" : "CREDIT",
      merchantRaw: g.merchant ?? null,
      referenceNo: g.ref ?? null,
      txnTime: when,
    });
  },
};

/** Always throws: proves a buggy parser cannot lose a message. */
export const explodingParser: SmsParser = {
  name: "ExplodingParser",
  senderCodes: ["BOOMBK"],
  parse() {
    throw new Error("simulated parser bug");
  },
};
