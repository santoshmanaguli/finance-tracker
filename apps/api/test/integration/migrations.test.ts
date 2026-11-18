import { sql } from "kysely";
import { afterAll, beforeAll, beforeEach, describe, expect, inject, it } from "vitest";
import type { Db } from "../../src/db.ts";
import { createTestDb, pgErrorCode, resetIngestTables } from "../support/db.ts";

/**
 * The schema is code, so it gets tests. These prove the constraints our dedup and security
 * guarantees rely on actually exist in the real database.
 */
describe("database schema", () => {
  let db: Db;
  let accountId: number;
  let categoryId: number;

  beforeAll(() => {
    db = createTestDb(inject("databaseUrl"));
  });
  afterAll(async () => {
    await db.destroy();
  });

  beforeEach(async () => {
    await resetIngestTables(db);
    accountId = (
      await db
        .insertInto("accounts")
        .values({ institution: "HDFC", account_type: "CREDIT_CARD", last4: "1234" })
        .returning("id")
        .executeTakeFirstOrThrow()
    ).id;
    categoryId = (
      await db.selectFrom("categories").select("id").where("name", "=", "Food").executeTakeFirstOrThrow()
    ).id;
  });

  const insertTransaction = (amountPaise: number, ref: string | null) =>
    db
      .insertInto("transactions")
      .values({
        account_id: accountId,
        amount_paise: amountPaise,
        direction: "DEBIT",
        reference_no: ref,
        txn_time: new Date(),
        category_id: categoryId,
        category_source: "DEFAULT",
        source: "SMS",
        raw_text: "test",
        merchant_raw: null,
        merchant_norm: null,
      })
      .execute();

  const insertUnparsedRaw = (hash: string) =>
    db
      .insertInto("raw_messages")
      .values({
        source: "SMS",
        sender: "AX-HDFCBK",
        body: "some text",
        received_at: new Date(),
        content_hash: hash,
        parse_status: "UNPARSED",
      })
      .execute();

  it("all migrations applied", async () => {
    const rows = await sql<{
      version: string;
    }>`SELECT version FROM schema_migrations ORDER BY version`.execute(db);
    expect(rows.rows.map((r) => r.version)).toEqual(["20261103000000", "20261103000001"]);
  });

  it("seeds Uncategorized and SYSTEM rules pointing at real categories", async () => {
    const uncategorized = await db
      .selectFrom("categories")
      .select("id")
      .where("name", "=", "Uncategorized")
      .execute();
    expect(uncategorized).toHaveLength(1);

    const rules = await db
      .selectFrom("merchant_rules as r")
      .innerJoin("categories as c", "c.id", "r.category_id")
      .select(["r.pattern", "c.name"])
      .where("r.created_by", "=", "SYSTEM")
      .execute();
    expect(rules.length).toBeGreaterThanOrEqual(10);
    expect(rules).toContainEqual({ pattern: "SWIGGY", name: "Food" });
  });

  it("account rejects anything but four digits (23514 check_violation)", async () => {
    const code = await pgErrorCode(
      db
        .insertInto("accounts")
        .values({ institution: "SBI", account_type: "DEBIT_CARD", last4: "12AB" })
        .execute(),
    );
    expect(code).toBe("23514");
  });

  it("amount must be positive paise", async () => {
    expect(await pgErrorCode(insertTransaction(-500, "REF1"))).toBe("23514");
  });

  it("same reference on the same account and direction is rejected (23505 unique_violation)", async () => {
    await insertTransaction(49900, "UPI123456789012");
    expect(await pgErrorCode(insertTransaction(49900, "UPI123456789012"))).toBe("23505");
  });

  it("transactions without a reference are not constrained", async () => {
    // Two identical Rs 20 chai payments with no ref are both legitimate.
    await insertTransaction(2000, null);
    await insertTransaction(2000, null);
    const { count } = await db
      .selectFrom("transactions")
      .select((eb) => eb.fn.countAll<number>().as("count"))
      .executeTakeFirstOrThrow();
    expect(Number(count)).toBe(2);
  });

  it("identical raw message is rejected by content hash", async () => {
    await insertUnparsedRaw("a".repeat(64));
    expect(await pgErrorCode(insertUnparsedRaw("a".repeat(64)))).toBe("23505");
  });

  it("unparsed_messages view shows only unparsed", async () => {
    await insertUnparsedRaw("b".repeat(64));
    const rows = await sql`SELECT * FROM unparsed_messages`.execute(db);
    expect(rows.rows).toHaveLength(1);
  });

  it("merchant rule patterns must be uppercase", async () => {
    const code = await pgErrorCode(
      db
        .insertInto("merchant_rules")
        .values({ pattern: "swiggy", match_type: "CONTAINS", category_id: categoryId, created_by: "USER" })
        .execute(),
    );
    expect(code).toBe("23514");
  });
});
