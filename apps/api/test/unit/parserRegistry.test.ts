import { describe, expect, it } from "vitest";
import { createParserRegistry } from "../../src/ingest/parsers/registry.ts";
import { explodingParser, fakeBankParser } from "../support/fakeParsers.ts";

describe("parser registry", () => {
  const registry = createParserRegistry([fakeBankParser, explodingParser]);
  const now = new Date("2026-11-04T05:00:00Z");

  it("routes by sender header regardless of operator prefix", () => {
    const result = registry.parse(
      "JD-FAKEBK-S",
      "Rs 250 debited from Card XX1234 at ZOMATO on 04-11-2026 10:15",
      now,
    );

    expect(result.kind).toBe("parsed");
    if (result.kind !== "parsed") return;
    expect(result.transaction.amountPaise).toBe(25000);
    expect(result.transaction.last4).toBe("1234");
    // 10:15 IST = 04:45 UTC
    expect(result.transaction.txnTime).toEqual(new Date("2026-11-04T04:45:00Z"));
  });

  it("unknown sender is failed, not dropped", () => {
    expect(registry.parse("AX-NOPEBK", "anything", now)).toEqual({
      kind: "failed",
      reason: "No parser for sender code NOPEBK",
    });
  });

  it("OTP is ignored before any parser runs", () => {
    expect(registry.parse("AX-BOOMBK", "123456 is your OTP for txn of Rs 500", now)).toEqual({
      kind: "ignored",
      reason: "OTP",
    });
  });

  it("parser exception becomes a failed result", () => {
    const result = registry.parse("AX-BOOMBK", "boom", now);
    expect(result.kind).toBe("failed");
    expect(result.kind === "failed" && result.reason).toContain("simulated parser bug");
  });

  it("two parsers claiming the same sender fail at startup", () => {
    expect(() => createParserRegistry([fakeBankParser, fakeBankParser])).toThrow(/FAKEBK/);
  });
});
