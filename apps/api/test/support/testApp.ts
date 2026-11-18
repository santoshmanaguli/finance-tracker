import type { FastifyInstance } from "fastify";
import { buildApp } from "../../src/app.ts";
import { loadConfig } from "../../src/config.ts";
import type { Db } from "../../src/db.ts";
import { explodingParser, fakeBankParser } from "./fakeParsers.ts";

export const TEST_TOKEN = "test-token-0123456789abcdefghijklmnopqrstuvwxyz";

export async function buildTestApp(db: Db, databaseUrl: string): Promise<FastifyInstance> {
  const config = loadConfig({ DATABASE_URL: databaseUrl, INGEST_API_TOKEN: TEST_TOKEN });
  return buildApp({ config, db, parsers: [fakeBankParser, explodingParser], logger: false });
}
