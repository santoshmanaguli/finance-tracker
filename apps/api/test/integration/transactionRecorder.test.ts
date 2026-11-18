import type { Kysely } from "kysely";
import { afterAll, beforeAll, beforeEach, describe, expect, inject, it } from "vitest";
import { findOrCreateAccount } from "../../src/account/accountRepository.ts";
import type { Database, Db } from "../../src/db.ts";
import type { Source } from "../../src/ingest/contentHash.ts";
import { type Observation, recordTransaction } from "../../src/transaction/transactionRecorder.ts";
import { createTestDb, resetIngestTables, withRollback } from "../support/db.ts";

/** Dedup layers 2 and 3, tested directly against the recorder. Every test rolls back. */
const T0 = new Date("2026-11-04T04:45:00Z");
const minutes = (n: number) => new Date(T0.getTime() + n * 60_000);
const WINDOW = 15;

describe("recordTransaction (dedup layers 2 and 3)", () => {
  let db: Db;

  beforeAll(() => {
    db = createTestDb(inject("databaseUrl"));
  });
  afterAll(async () => {
    await db.destroy();
  });
  beforeEach(async () => {
    await resetIngestTables(db);
  });

  /** Runs a scenario in a rolled-back transaction with a fresh HDFC card account. */
  const scenario = (fn: (ctx: Ctx) => Promise<void>) =>
    withRollback(db, async (trx) => {
      const card = await findOrCreateAccount(trx, "HDFC", "CREDIT_CARD", "1234");
      await fn(new Ctx(trx, card));
    });

  // ---------- layer 2: reference number ----------

  it("same reference from another source matches", () =>
    scenario(async (c) => {
      const sms = await c.record(50000, "UPI123", T0, "SMS");
      const email = await c.record(50000, "UPI123", minutes(2), "EMAIL");
      expect(email).toEqual({ transactionId: sms.transactionId, created: false });
    }));

  it("different references are different transactions even if everything else matches", () =>
    scenario(async (c) => {
      await c.record(50000, "UPI111", T0, "SMS");
      expect((await c.record(50000, "UPI222", T0, "EMAIL")).created).toBe(true);
    }));

  // ---------- layer 3: fuzzy, cross-source ----------

  it("ref-less SMS and email within the window match, and the email's ref is adopted", () =>
    scenario(async (c) => {
      const sms = await c.recordAndLink(9900, null, T0, "SMS");
      const email = await c.record(9900, "AUTH789", minutes(10), "EMAIL");

      expect(email).toEqual({ transactionId: sms.transactionId, created: false });
      const row = await c.trx
        .selectFrom("transactions")
        .select("reference_no")
        .where("id", "=", sms.transactionId)
        .executeTakeFirstOrThrow();
      expect(row.reference_no).toBe("AUTH789");
    }));

  it("outside the window is a new transaction", () =>
    scenario(async (c) => {
      await c.recordAndLink(9900, null, T0, "SMS");
      expect((await c.record(9900, null, minutes(16), "EMAIL")).created).toBe(true);
    }));

  it("the same source never fuzzy-matches (two real Rs 20 chai payments)", () =>
    scenario(async (c) => {
      await c.recordAndLink(2000, null, T0, "SMS");
      expect((await c.record(2000, null, minutes(5), "SMS")).created).toBe(true);
    }));

  it("two swipes and two emails pair up one-to-one by closest time", () =>
    scenario(async (c) => {
      const swipe1 = await c.recordAndLink(2000, null, T0, "SMS");
      const swipe2 = await c.recordAndLink(2000, null, minutes(5), "SMS");

      const email1 = await c.recordAndLink(2000, null, minutes(1), "EMAIL");
      const email2 = await c.record(2000, null, minutes(2), "EMAIL");

      expect(email1.transactionId).toBe(swipe1.transactionId);
      // swipe1 already has an EMAIL, so email2 must go to swipe2 even though swipe1 is closer.
      expect(email2.transactionId).toBe(swipe2.transactionId);
    }));

  it("a different account never matches", () =>
    scenario(async (c) => {
      await c.recordAndLink(9900, null, T0, "SMS");
      const other = await findOrCreateAccount(c.trx, "SBI", "DEBIT_CARD", "1234");
      const email = await recordTransaction(
        c.trx,
        { ...c.obs(9900, null, T0, "EMAIL"), accountId: other },
        WINDOW,
      );
      expect(email.created).toBe(true);
    }));
});

class Ctx {
  readonly trx: Kysely<Database>;
  readonly accountId: number;
  private hashSeq = 0;

  constructor(trx: Kysely<Database>, accountId: number) {
    this.trx = trx;
    this.accountId = accountId;
  }

  obs(amountPaise: number, ref: string | null, when: Date, source: Source): Observation {
    return {
      accountId: this.accountId,
      amountPaise,
      direction: "DEBIT",
      merchantRaw: "SOME MERCHANT",
      referenceNo: ref,
      txnTime: when,
      source,
      rawText: "raw text",
    };
  }

  record(amountPaise: number, ref: string | null, when: Date, source: Source) {
    return recordTransaction(this.trx, this.obs(amountPaise, ref, when, source), WINDOW);
  }

  /** In production, ingest stores the raw message linked to the transaction; simulate that. */
  async recordAndLink(amountPaise: number, ref: string | null, when: Date, source: Source) {
    const outcome = await this.record(amountPaise, ref, when, source);
    await this.trx
      .insertInto("raw_messages")
      .values({
        source,
        sender: "TEST",
        body: "raw",
        received_at: when,
        content_hash: String(++this.hashSeq).padStart(64, "0"),
        parse_status: "PARSED",
        transaction_id: outcome.transactionId,
      })
      .execute();
    return outcome;
  }
}
