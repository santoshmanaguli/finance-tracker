import type { FastifyBaseLogger } from "fastify";
import { normalizeSender } from "./senderIds.ts";
import type { ParseResult, SmsParser } from "./types.ts";

/** OTPs are never useful here and are sensitive. Checked before any bank parser runs. */
const OTP = /\bOTP\b|one[- ]time password/i;

export function isOtp(body: string): boolean {
  return OTP.test(body);
}

export interface ParserRegistry {
  parse(sender: string, body: string, receivedAt: Date): ParseResult;
  readonly senderCodes: readonly string[];
}

/**
 * Routes a message to the parser for its sender, and guarantees the "never drop a message"
 * rule: whatever happens inside a parser, the caller gets a ParseResult.
 *
 * Fails at STARTUP if two parsers claim the same sender code: better than discovering it on
 * the first real SMS in production.
 */
export function createParserRegistry(parsers: readonly SmsParser[], log?: FastifyBaseLogger): ParserRegistry {
  const bySenderCode = new Map<string, SmsParser>();
  for (const parser of parsers) {
    for (const code of parser.senderCodes) {
      const previous = bySenderCode.get(code);
      if (previous) {
        throw new Error(`Sender code ${code} claimed by both ${previous.name} and ${parser.name}`);
      }
      bySenderCode.set(code, parser);
    }
  }

  return {
    senderCodes: [...bySenderCode.keys()],
    parse(sender, body, receivedAt) {
      if (isOtp(body)) {
        return { kind: "ignored", reason: "OTP" };
      }
      const code = normalizeSender(sender);
      const parser = bySenderCode.get(code);
      if (!parser) {
        return { kind: "failed", reason: `No parser for sender code ${code}` };
      }
      try {
        return parser.parse(body, receivedAt);
      } catch (err) {
        // A bug in one parser must not lose the message or fail the request.
        log?.warn({ err, senderCode: code }, "parser threw");
        const message = err instanceof Error ? err.message : String(err);
        return { kind: "failed", reason: `${parser.name} error: ${message}` };
      }
    },
  };
}
