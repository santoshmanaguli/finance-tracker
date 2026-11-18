import { type Kysely, sql } from "kysely";
import { categorize } from "../categorization/categorizationService.ts";
import { normalizeMerchant } from "../categorization/merchantNormalizer.ts";
import type { Database } from "../db.ts";
import type { Source } from "../ingest/contentHash.ts";
import type { Direction } from "../ingest/parsers/types.ts";

/** What a parser observed, resolved to an account. A command object instead of ten arguments. */
export interface Observation {
  accountId: number;
  amountPaise: number;
  direction: Direction;
  merchantRaw: string | null;
  referenceNo: string | null;
  txnTime: Date;
  source: Source;
  rawText: string;
}

export interface RecordOutcome {
  transactionId: number;
  created: boolean;
}

/**
 * Turns an observation into the golden-record transaction: matches an existing one
 * (dedup layers 2 and 3) or creates a new categorized one. See docs/decisions/0005.
 * (Layer 1, the identical-message retry, happens earlier in ingest via the content hash.)
 *
 * @param trx the caller's transaction: dedup reads and the insert must see the same state.
 */
export async function recordTransaction(
  trx: Kysely<Database>,
  obs: Observation,
  dedupWindowMinutes: number,
): Promise<RecordOutcome> {
  const existingId = await findExisting(trx, obs, dedupWindowMinutes);
  if (existingId !== null) {
    return { transactionId: existingId, created: false };
  }

  const merchantNorm = normalizeMerchant(obs.merchantRaw);
  const decision = await categorize(trx, merchantNorm);
  const inserted = await trx
    .insertInto("transactions")
    .values({
      account_id: obs.accountId,
      amount_paise: obs.amountPaise,
      direction: obs.direction,
      merchant_raw: obs.merchantRaw,
      merchant_norm: merchantNorm,
      reference_no: obs.referenceNo,
      txn_time: obs.txnTime,
      category_id: decision.categoryId,
      category_source: decision.source,
      source: obs.source,
      raw_text: obs.rawText,
    })
    .returning("id")
    .executeTakeFirstOrThrow();
  return { transactionId: inserted.id, created: true };
}

async function findExisting(
  trx: Kysely<Database>,
  obs: Observation,
  windowMinutes: number,
): Promise<number | null> {
  // Layer 2: a reference number is a hard identity.
  if (obs.referenceNo !== null) {
    const byRef = await trx
      .selectFrom("transactions")
      .select("id")
      .where("account_id", "=", obs.accountId)
      .where("direction", "=", obs.direction)
      .where("reference_no", "=", obs.referenceNo)
      .executeTakeFirst();
    if (byRef) return byRef.id;
  }

  // Layer 3: fuzzy match, only against evidence from a DIFFERENT source, and only against rows
  // WITHOUT a reference (a row with a different reference is by definition a different payment).
  //
  // Two real Rs 20 chai payments 5 minutes apart arrive as two SMS: same source, so they never
  // fuzzy-match each other. When their two emails arrive later, NOT EXISTS ensures email #2
  // pairs with the second payment instead of the first one (which already has an EMAIL).
  // Closest in time wins; id breaks ties deterministically. Uses ix_transactions_account_amount_time.
  const windowMs = windowMinutes * 60_000;
  const from = new Date(obs.txnTime.getTime() - windowMs);
  const to = new Date(obs.txnTime.getTime() + windowMs);
  const candidate = await trx
    .selectFrom("transactions as t")
    .select("t.id")
    .where("t.account_id", "=", obs.accountId)
    .where("t.amount_paise", "=", obs.amountPaise)
    .where("t.direction", "=", obs.direction)
    .where("t.txn_time", ">=", from)
    .where("t.txn_time", "<=", to)
    .where("t.reference_no", "is", null)
    .where(({ not, exists, selectFrom }) =>
      not(
        exists(
          selectFrom("raw_messages as r")
            .select(sql`1`.as("one"))
            .whereRef("r.transaction_id", "=", "t.id")
            .where("r.source", "=", obs.source),
        ),
      ),
    )
    .orderBy(sql`abs(extract(epoch from (t.txn_time - ${obs.txnTime}::timestamptz)))`)
    .orderBy("t.id")
    .limit(1)
    .executeTakeFirst();

  if (!candidate) return null;

  // Enrich the golden record: a later source may know the reference the first one lacked.
  if (obs.referenceNo !== null) {
    await trx
      .updateTable("transactions")
      .set({ reference_no: obs.referenceNo, updated_at: sql<Date>`now()` })
      .where("id", "=", candidate.id)
      .where("reference_no", "is", null)
      .execute();
  }
  return candidate.id;
}
