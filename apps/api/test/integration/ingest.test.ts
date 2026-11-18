import type { FastifyInstance } from "fastify";
import { sql } from "kysely";
import { afterAll, beforeAll, beforeEach, describe, expect, inject, it } from "vitest";
import type { Db } from "../../src/db.ts";
import { createTestDb, resetIngestTables } from "../support/db.ts";
import { buildTestApp, TEST_TOKEN } from "../support/testApp.ts";

/**
 * End to end, in-process: auth hook -> route -> service -> parser -> dedup -> real Postgres.
 * app.inject() sends a real HTTP request through Fastify's full pipeline without opening a port.
 */
const SWIGGY_SMS = "Rs 1,234.50 debited from Card XX1234 at SWIGGY on 04-11-2026 10:15";

describe("POST /api/ingest/sms", () => {
  let db: Db;
  let app: FastifyInstance;

  beforeAll(async () => {
    db = createTestDb(inject("databaseUrl"));
    app = await buildTestApp(db, inject("databaseUrl"));
  });
  afterAll(async () => {
    await app.close();
    await db.destroy();
  });
  beforeEach(async () => {
    await resetIngestTables(db);
  });

  const post = (payload: unknown, token: string | null = TEST_TOKEN) =>
    app.inject({
      method: "POST",
      url: "/api/ingest/sms",
      headers: token === null ? {} : { "x-api-token": token },
      payload: payload as Record<string, unknown>,
    });

  const ingest = (sender: string, body: string) =>
    post({ sender, body, receivedAt: "2026-11-04T10:16:00+05:30" });

  const count = async (table: "raw_messages" | "transactions") =>
    Number(
      (
        await db
          .selectFrom(table)
          .select((eb) => eb.fn.countAll().as("n"))
          .executeTakeFirstOrThrow()
      ).n,
    );

  const onlyRawStatus = async () =>
    (await db.selectFrom("raw_messages").select("parse_status").executeTakeFirstOrThrow()).parse_status;

  // ---------- security ----------

  it("rejects a missing token", async () => {
    expect((await post({ sender: "AX-FAKEBK", body: SWIGGY_SMS }, null)).statusCode).toBe(401);
    expect(await count("raw_messages")).toBe(0);
  });

  it("rejects a wrong token", async () => {
    expect((await post({ sender: "AX-FAKEBK", body: SWIGGY_SMS }, "wrong")).statusCode).toBe(401);
  });

  it("rejects an invalid payload with problem details", async () => {
    const res = await post({ sender: "", body: "x" });
    expect(res.statusCode).toBe(400);
    expect(res.headers["content-type"]).toContain("application/problem+json");
  });

  // ---------- happy path ----------

  it("creates a categorized transaction", async () => {
    const res = await ingest("AX-FAKEBK", SWIGGY_SMS);
    expect(res.statusCode).toBe(201);
    expect(res.json()).toMatchObject({ status: "CREATED" });

    const row = await sql<Record<string, unknown>>`
      SELECT t.amount_paise, t.direction, t.merchant_norm, t.category_source, c.name AS category,
             a.institution, a.last4, t.txn_time = TIMESTAMPTZ '2026-11-04 10:15+05:30' AS time_ok
      FROM transactions t
      JOIN categories c ON c.id = t.category_id
      JOIN accounts a ON a.id = t.account_id`.execute(db);
    expect(row.rows).toEqual([
      {
        amount_paise: 123450,
        direction: "DEBIT",
        merchant_norm: "SWIGGY",
        category_source: "RULE",
        category: "Food",
        institution: "FAKEBANK",
        last4: "1234",
        time_ok: true,
      },
    ]);
    expect(await onlyRawStatus()).toBe("PARSED");
  });

  it("unknown merchant is Uncategorized", async () => {
    await ingest("AX-FAKEBK", "Rs 99 debited from Card XX1234 at RANDOM SHOP on 04-11-2026 11:00");
    const row = await db
      .selectFrom("transactions as t")
      .innerJoin("categories as c", "c.id", "t.category_id")
      .select(["c.name", "t.category_source"])
      .executeTakeFirstOrThrow();
    expect(row).toEqual({ name: "Uncategorized", category_source: "DEFAULT" });
  });

  // ---------- dedup layer 1 ----------

  it("a retried message is idempotent (even via another operator route)", async () => {
    await ingest("AX-FAKEBK", SWIGGY_SMS);
    const retry = await ingest("JD-FAKEBK", SWIGGY_SMS);

    expect(retry.statusCode).toBe(200);
    expect(retry.json()).toMatchObject({ status: "ALREADY_RECEIVED" });
    expect(await count("transactions")).toBe(1);
    expect(await count("raw_messages")).toBe(1);
  });

  it("two genuine payments of the same amount are both kept", async () => {
    await ingest("AX-FAKEBK", "Rs 20 debited from Card XX1234 at CHAI POINT on 04-11-2026 10:00");
    await ingest("AX-FAKEBK", "Rs 20 debited from Card XX1234 at CHAI POINT on 04-11-2026 10:05");
    expect(await count("transactions")).toBe(2);
  });

  it("concurrent duplicate requests: one row, and both clients get success", async () => {
    // Simulates two iOS automations ("UPI" and "debited") firing for the same SMS at once.
    for (let round = 0; round < 5; round++) {
      await resetIngestTables(db);
      const results = await Promise.all([ingest("AX-FAKEBK", SWIGGY_SMS), ingest("AX-FAKEBK", SWIGGY_SMS)]);
      const statuses = results.map((r) => r.json().status).sort();
      expect(statuses).toEqual(["ALREADY_RECEIVED", "CREATED"]);
      expect(await count("transactions")).toBe(1);
      expect(await count("raw_messages")).toBe(1);
    }
  });

  // ---------- never drop a message ----------

  it("unknown sender goes to the unparsed inbox", async () => {
    const res = await ingest("AX-NEWBNK", "Rs 500 spent on your card");
    expect(res.statusCode).toBe(200);
    expect(res.json()).toMatchObject({ status: "UNPARSED" });
    const rows = await sql<{ body: string }>`SELECT body FROM unparsed_messages`.execute(db);
    expect(rows.rows).toEqual([{ body: "Rs 500 spent on your card" }]);
  });

  it("a parser crash still stores the message", async () => {
    const res = await ingest("AX-BOOMBK", "anything");
    expect(res.statusCode).toBe(200);
    const rows = await sql<{ parse_error: string }>`SELECT parse_error FROM unparsed_messages`.execute(db);
    expect(rows.rows[0]?.parse_error).toContain("simulated parser bug");
  });

  it("an unrecognized format from a known bank is UNPARSED", async () => {
    await ingest("AX-FAKEBK", "Your card XX1234 statement is ready");
    expect(await onlyRawStatus()).toBe("UNPARSED");
  });

  it("OTP is ignored and not stored in clear", async () => {
    const res = await ingest("AX-FAKEBK", "482913 is your OTP for Rs 500 at AMAZON. Do not share.");
    expect(res.json()).toMatchObject({ status: "IGNORED" });
    const raw = await db.selectFrom("raw_messages").select("body").executeTakeFirstOrThrow();
    expect(raw.body).not.toContain("482913");
  });
});
