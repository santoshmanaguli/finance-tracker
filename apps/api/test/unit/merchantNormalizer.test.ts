import { describe, expect, it } from "vitest";
import { normalizeMerchant } from "../../src/categorization/merchantNormalizer.ts";

describe("normalizeMerchant", () => {
  it.each([
    ["Swiggy Ltd", "SWIGGY LTD"],
    ["SWIGGY*BANGALORE", "SWIGGY BANGALORE"],
    ["swiggy.in@axisbank", "SWIGGY IN"],
    ["  Big   Basket  ", "BIG BASKET"],
    ["paytm-123456@paytm", "PAYTM 123456"],
  ])('"%s" -> "%s"', (raw, expected) => {
    expect(normalizeMerchant(raw)).toBe(expected);
  });

  it.each([null, undefined, "", "  ", "***", "@ybl"])("returns null for %j", (raw) => {
    expect(normalizeMerchant(raw)).toBeNull();
  });
});
