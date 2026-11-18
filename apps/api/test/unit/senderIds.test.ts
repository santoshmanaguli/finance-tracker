import { describe, expect, it } from "vitest";
import { normalizeSender } from "../../src/ingest/parsers/senderIds.ts";

describe("normalizeSender", () => {
  it.each([
    ["AX-HDFCBK", "HDFCBK"],
    ["JD-HDFCBK", "HDFCBK"], // different operator, same bank
    ["VM-HDFCBK-S", "HDFCBK"], // TRAI service suffix
    ["ad-sbiinb-t", "SBIINB"], // case-insensitive
    ["HDFCBK", "HDFCBK"], // already bare
    ["+919876543210", "+919876543210"], // phone numbers pass through unchanged
  ])("%s -> %s", (sender, expected) => {
    expect(normalizeSender(sender)).toBe(expected);
  });
});
