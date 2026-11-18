import type { FastifyBaseLogger } from "fastify";
import { findOrCreateAccount } from "../account/accountRepository.ts";
import type { Clock } from "../common/clock.ts";
import type { Db } from "../db.ts";
import { recordTransaction } from "../transaction/transactionRecorder.ts";
import { contentHash, type Source } from "./contentHash.ts";
import { isOtp, type ParserRegistry } from "./parsers/registry.ts";
import { assertNever } from "./parsers/types.ts";

export interface IngestCommand {
  sender: string;
  body: string;
  receivedAt: Date | null;
}

export type IngestStatus =
  /** New transaction recorded. */
  | "CREATED"
  /** Parsed, and matched a transaction already known from another source. */
  | "MATCHED_EXISTING"
  /** This exact message was received before (e.g. the Shortcut fired twice). No-op. */
  | "ALREADY_RECEIVED"
  /** Deliberately skipped (OTP, promo). */
  | "IGNORED"
  /** Stored in the unparsed inbox for review. */
  | "UNPARSED";

export interface IngestResult {
  status: IngestStatus;
  rawMessageId: number;
  transactionId: number | null;
}

export interface IngestDeps {
  db: Db;
  parsers: ParserRegistry;
  clock: Clock;
  dedupWindowMinutes: number;
  log: FastifyBaseLogger;
}

const SOURCE: Source = "SMS";

/**
 * The ingest use case, in order:
 *  1. Layer 1 dedup: seen this exact content before? Return the earlier result (idempotent).
 *  2. Parse. EVERY outcome is stored; nothing is dropped.
 *  3. For a parsed transaction: resolve the account, then dedup layers 2/3 or create.
 * All in ONE database transaction: the raw message, a new account and a new transaction
 * commit together or not at all. No half-written state.
 */
export async function ingestSms(deps: IngestDeps, cmd: IngestCommand): Promise<IngestResult> {
  let result: IngestResult;
  try {
    result = await ingestOnce(deps, cmd);
  } catch (err) {
    // Two identical requests at the same instant (e.g. the "UPI" and "debited" automations both
    // fire for one SMS) can both pass the "seen before?" check; the database's unique index then
    // rejects the second one (23505). Retrying ONCE is safe: the retry sees the committed winner
    // and answers ALREADY_RECEIVED / MATCHED_EXISTING, so the client gets a clean 200, not 409.
    if (!isUniqueViolation(err)) throw err;
    deps.log.info("concurrent duplicate detected; retrying ingest once");
    result = await ingestOnce(deps, cmd);
  }
  // Never log the SMS body: it's financial data. Ids are enough to investigate.
  deps.log.info({ sender: cmd.sender, ...result }, "ingested sms");
  return result;
}

function isUniqueViolation(err: unknown): boolean {
  return typeof err === "object" && err !== null && (err as { code?: unknown }).code === "23505";
}

async function ingestOnce(deps: IngestDeps, cmd: IngestCommand): Promise<IngestResult> {
  const receivedAt = cmd.receivedAt ?? deps.clock.now();
  const hash = contentHash(SOURCE, cmd.sender, cmd.body);

  return deps.db.transaction().execute(async (trx): Promise<IngestResult> => {
    const seen = await trx
      .selectFrom("raw_messages")
      .select(["id", "transaction_id"])
      .where("content_hash", "=", hash)
      .executeTakeFirst();
    if (seen) {
      return { status: "ALREADY_RECEIVED", rawMessageId: seen.id, transactionId: seen.transaction_id };
    }

    const parsed = deps.parsers.parse(cmd.sender, cmd.body, receivedAt);
    const base = { source: SOURCE, sender: cmd.sender, received_at: receivedAt, content_hash: hash };

    switch (parsed.kind) {
      case "parsed": {
        const t = parsed.transaction;
        const accountId = await findOrCreateAccount(trx, t.institution, t.accountType, t.last4);
        const outcome = await recordTransaction(
          trx,
          {
            accountId,
            amountPaise: t.amountPaise,
            direction: t.direction,
            merchantRaw: t.merchantRaw,
            referenceNo: t.referenceNo,
            txnTime: t.txnTime ?? receivedAt,
            source: SOURCE,
            rawText: cmd.body,
          },
          deps.dedupWindowMinutes,
        );
        const raw = await trx
          .insertInto("raw_messages")
          .values({
            ...base,
            body: cmd.body,
            parse_status: outcome.created ? "PARSED" : "DUPLICATE",
            transaction_id: outcome.transactionId,
          })
          .returning("id")
          .executeTakeFirstOrThrow();
        return {
          status: outcome.created ? "CREATED" : "MATCHED_EXISTING",
          rawMessageId: raw.id,
          transactionId: outcome.transactionId,
        };
      }
      case "ignored": {
        // Don't keep OTP text at rest: it's sensitive and has no value to us.
        const stored = isOtp(cmd.body) ? "[redacted: OTP]" : cmd.body;
        const raw = await trx
          .insertInto("raw_messages")
          .values({ ...base, body: stored, parse_status: "IGNORED", parse_error: parsed.reason })
          .returning("id")
          .executeTakeFirstOrThrow();
        return { status: "IGNORED", rawMessageId: raw.id, transactionId: null };
      }
      case "failed": {
        const raw = await trx
          .insertInto("raw_messages")
          .values({ ...base, body: cmd.body, parse_status: "UNPARSED", parse_error: parsed.reason })
          .returning("id")
          .executeTakeFirstOrThrow();
        return { status: "UNPARSED", rawMessageId: raw.id, transactionId: null };
      }
      default:
        return assertNever(parsed);
    }
  });
}
