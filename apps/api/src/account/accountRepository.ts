import type { Kysely } from "kysely";
import type { Database } from "../db.ts";
import type { AccountType } from "../ingest/parsers/types.ts";

const LAST4 = /^\d{4}$/;

/**
 * Accounts are discovered from messages: the first SMS mentioning "HDFC credit card XX1234"
 * creates that account; no manual setup.
 *
 * Race-safe "find or create": INSERT ... ON CONFLICT DO NOTHING, then SELECT. A naive
 * "SELECT, then INSERT if missing" lets two concurrent requests both see "missing" and both
 * insert (one then fails on the unique constraint). Here the database arbitrates.
 *
 * @param trx must be the caller's transaction, so the account and the transaction that
 *            references it commit (or roll back) together.
 */
export async function findOrCreateAccount(
  trx: Kysely<Database>,
  institution: string,
  accountType: AccountType,
  last4: string,
): Promise<number> {
  if (!LAST4.test(last4)) {
    // Defence in depth: the DB has the same CHECK, but failing here is clearer and guarantees
    // we never even attempt to send a full card number to the database.
    throw new Error("last4 must be exactly 4 digits");
  }
  await trx
    .insertInto("accounts")
    .values({ institution, account_type: accountType, last4 })
    .onConflict((oc) => oc.columns(["institution", "account_type", "last4"]).doNothing())
    .execute();
  const row = await trx
    .selectFrom("accounts")
    .select("id")
    .where("institution", "=", institution)
    .where("account_type", "=", accountType)
    .where("last4", "=", last4)
    .executeTakeFirstOrThrow();
  return row.id;
}
