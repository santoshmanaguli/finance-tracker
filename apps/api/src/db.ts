import { type ColumnType, type Generated, Kysely, PostgresDialect } from "kysely";
import pg from "pg";

/*
 * Postgres BIGINT (type OID 20) can exceed JavaScript's safe integer range, so node-postgres
 * returns it as a STRING by default. Our BIGINTs are ids and paise amounts, which stay far
 * below 2^53 (~ Rs 90 trillion in paise). We parse to number, but refuse silently-wrong
 * values: an out-of-range number would be a corrupted amount, so crash loudly instead.
 */
pg.types.setTypeParser(pg.types.builtins.INT8, (value) => {
  const n = Number(value);
  if (!Number.isSafeInteger(n)) {
    throw new RangeError(`BIGINT ${value} exceeds Number.MAX_SAFE_INTEGER`);
  }
  return n;
});

/*
 * Table types for Kysely. They mirror db/migrations by hand; the migrations are the source
 * of truth and the integration tests catch drift. (kysely-codegen could generate these
 * from the live schema; not worth another tool at this size.)
 *
 * Generated<T>  = has a DB default, optional on insert.
 * ColumnType<Select, Insert, Update> where the three differ.
 */
type Timestamp = ColumnType<Date, Date | string | undefined, Date | string>;

export interface AccountsTable {
  id: Generated<number>;
  institution: string;
  account_type: "CREDIT_CARD" | "DEBIT_CARD" | "SAVINGS" | "UPI";
  last4: string;
  nickname: string | null;
  created_at: Timestamp;
}

export interface CategoriesTable {
  id: Generated<number>;
  name: string;
}

export interface MerchantRulesTable {
  id: Generated<number>;
  pattern: string;
  match_type: "EXACT" | "CONTAINS";
  category_id: number;
  priority: Generated<number>;
  created_by: "SYSTEM" | "USER";
  created_at: Timestamp;
}

export interface TransactionsTable {
  id: Generated<number>;
  account_id: number;
  amount_paise: number;
  direction: "DEBIT" | "CREDIT";
  merchant_raw: string | null;
  merchant_norm: string | null;
  reference_no: string | null;
  txn_time: ColumnType<Date, Date, Date>;
  category_id: number;
  category_source: "DEFAULT" | "RULE" | "USER" | "LLM";
  source: "SMS" | "EMAIL" | "STATEMENT";
  raw_text: string;
  version: Generated<number>;
  created_at: Timestamp;
  updated_at: Timestamp;
}

export interface RawMessagesTable {
  id: Generated<number>;
  source: "SMS" | "EMAIL" | "STATEMENT";
  sender: string;
  body: string;
  received_at: ColumnType<Date, Date, Date>;
  content_hash: string;
  parse_status: "PARSED" | "UNPARSED" | "IGNORED" | "DUPLICATE";
  parse_error: string | null;
  transaction_id: number | null;
  created_at: Timestamp;
}

export interface Database {
  accounts: AccountsTable;
  categories: CategoriesTable;
  merchant_rules: MerchantRulesTable;
  transactions: TransactionsTable;
  raw_messages: RawMessagesTable;
}

export type Db = Kysely<Database>;

export function createDb(connectionString: string, poolMax: number): Db {
  return new Kysely<Database>({
    dialect: new PostgresDialect({
      pool: new pg.Pool({ connectionString, max: poolMax }),
    }),
  });
}
