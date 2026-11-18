export type AccountType = "CREDIT_CARD" | "DEBIT_CARD" | "SAVINGS" | "UPI";
export type Direction = "DEBIT" | "CREDIT";

/** What a parser extracted from one message. */
export interface ParsedTransaction {
  /** e.g. "HDFC". The parser knows its own bank. */
  institution: string;
  accountType: AccountType;
  /** Last 4 digits ONLY. Parsers must never pass more. */
  last4: string;
  /** Positive integer paise; `direction` carries the sign. */
  amountPaise: number;
  direction: Direction;
  /** Merchant / payee as written, or null. */
  merchantRaw: string | null;
  /** UPI ref / transaction id, normalized, or null. */
  referenceNo: string | null;
  /** Time stated in the SMS, or null to fall back to the received time. */
  txnTime: Date | null;
}

/**
 * The three possible outcomes of parsing, as a discriminated union.
 * A `switch (result.kind)` with an exhaustiveness check (see assertNever) is verified by the
 * compiler: add a fourth kind and every unhandled switch fails to compile.
 * Expected outcomes ("this is a promo") are values, not exceptions.
 */
export type ParseResult =
  | { kind: "parsed"; transaction: ParsedTransaction }
  /** Recognized and deliberately skipped (OTP, promo, balance enquiry...). */
  | { kind: "ignored"; reason: string }
  /** Looked relevant but couldn't be parsed. Goes to the unparsed inbox. */
  | { kind: "failed"; reason: string };

/**
 * Strategy pattern: one implementation per bank. Adding a bank = adding one parser to the
 * list in parsers/index.ts; no other code changes.
 * Implementations must be stateless: compile regexes once at module level.
 */
export interface SmsParser {
  /** Human-readable name for logs and error messages, e.g. "HdfcSmsParser". */
  readonly name: string;
  /** Normalized sender codes, e.g. ["HDFCBK"]. See normalizeSender(). */
  readonly senderCodes: readonly string[];
  /** @param receivedAt use it to fill in a missing year or time */
  parse(body: string, receivedAt: Date): ParseResult;
}

export function assertNever(value: never): never {
  throw new Error(`Unhandled case: ${JSON.stringify(value)}`);
}

const LAST4 = /^\d{4}$/;

/** Validating constructor: a parser bug must not be able to produce an invalid transaction. */
export function parsedTransaction(t: ParsedTransaction): ParseResult {
  if (!LAST4.test(t.last4)) throw new Error("last4 must be exactly 4 digits");
  if (!Number.isSafeInteger(t.amountPaise) || t.amountPaise <= 0) {
    throw new Error("amountPaise must be a positive integer");
  }
  return { kind: "parsed", transaction: t };
}
