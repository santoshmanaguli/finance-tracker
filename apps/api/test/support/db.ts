import { type Kysely, sql } from "kysely";
import { createDb, type Database, type Db } from "../../src/db.ts";

export function createTestDb(url: string): Db {
  return createDb(url, 5);
}

/** Wipe everything ingest writes. Keeps seeded categories and SYSTEM rules from migrations. */
export async function resetIngestTables(db: Db): Promise<void> {
  await sql`TRUNCATE raw_messages, transactions, accounts RESTART IDENTITY CASCADE`.execute(db);
  await sql`DELETE FROM merchant_rules WHERE created_by = 'USER'`.execute(db);
}

class Rollback extends Error {}

/**
 * Runs `fn` inside a transaction that is ALWAYS rolled back, so a test leaves no rows behind.
 * Also lets us test code that requires the caller's transaction (like recordTransaction).
 */
export async function withRollback(db: Db, fn: (trx: Kysely<Database>) => Promise<void>): Promise<void> {
  try {
    await db.transaction().execute(async (trx) => {
      await fn(trx);
      throw new Rollback();
    });
  } catch (err) {
    if (!(err instanceof Rollback)) throw err;
  }
}

/** Postgres error code of a failed promise (e.g. 23505 unique_violation, 23514 check_violation). */
export async function pgErrorCode(promise: Promise<unknown>): Promise<string | undefined> {
  try {
    await promise;
    return undefined;
  } catch (err) {
    return (err as { code?: string }).code;
  }
}
