import { describe, expect, it } from "vitest";
import { toPaise } from "../../src/ingest/parsers/amounts.ts";

describe("toPaise", () => {
  it.each([
    ["500", 50000],
    ["500.5", 50050],
    ["500.50", 50050],
    ["0.01", 1],
    ["1,234.56", 123456],
    ["1,23,456.78", 12345678], // Indian lakh grouping
    ["  2,000.00 ", 200000],
    ["1234.56", 123456], // the classic float bug: 1234.56 * 100 = 123455.99999999999
  ])('"%s" -> %i paise', (text, expected) => {
    expect(toPaise(text)).toBe(expected);
  });

  it.each(["", " ", "abc", "-50", "1.234", "1e3", "Rs.50", "1..2"])(
    'rejects "%s" (not a plain amount)',
    (text) => {
      expect(() => toPaise(text)).toThrow(RangeError);
    },
  );
});
