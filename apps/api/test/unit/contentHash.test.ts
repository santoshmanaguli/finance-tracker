import { describe, expect, it } from "vitest";
import { contentHash } from "../../src/ingest/contentHash.ts";

describe("contentHash", () => {
  it("is a stable hex sha256", () => {
    expect(contentHash("SMS", "AX-HDFCBK", "hello")).toMatch(/^[0-9a-f]{64}$/);
  });

  it("ignores operator prefix and surrounding whitespace", () => {
    expect(contentHash("SMS", "AX-HDFCBK", "hello")).toBe(contentHash("SMS", "JD-HDFCBK-S", "  hello\n"));
  });

  it("differs by source and sender", () => {
    const base = contentHash("SMS", "AX-HDFCBK", "hello");
    expect(contentHash("EMAIL", "AX-HDFCBK", "hello")).not.toBe(base);
    expect(contentHash("SMS", "AX-SBIINB", "hello")).not.toBe(base);
  });
});
