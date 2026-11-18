import Fastify, { type FastifyInstance } from "fastify";
import { type Clock, systemClock } from "./common/clock.ts";
import { registerErrorHandler } from "./common/errors.ts";
import { registerHealthRoutes } from "./common/health.ts";
import type { Config } from "./config.ts";
import type { Db } from "./db.ts";
import { ingestRoutes } from "./ingest/ingestRoutes.ts";
import { bankParsers } from "./ingest/parsers/index.ts";
import { createParserRegistry } from "./ingest/parsers/registry.ts";
import type { SmsParser } from "./ingest/parsers/types.ts";

export interface AppOptions {
  config: Config;
  db: Db;
  /** Overridable for tests (fake bank parsers). Defaults to the production parsers. */
  parsers?: readonly SmsParser[];
  clock?: Clock;
  /** false in tests to keep output quiet. */
  logger?: boolean;
}

/**
 * Builds the app WITHOUT listening on a port. Tests call this and use app.inject() to send
 * requests in-process; server.ts calls it and then listens.
 *
 * Dependency injection by plain function arguments: no DI container, no decorators.
 * Everything a component needs is passed in, which is what makes it testable.
 */
export async function buildApp(opts: AppOptions): Promise<FastifyInstance> {
  const app = Fastify({
    logger:
      opts.logger === false
        ? false
        : {
            level: opts.config.LOG_LEVEL,
            // Structured JSON logs. Secrets must never reach log storage.
            redact: ['req.headers["x-api-token"]', "req.headers.authorization", "req.headers.cookie"],
          },
    bodyLimit: 16 * 1024, // an SMS is ~1 KB; reject anything absurd early
  });

  registerErrorHandler(app);
  registerHealthRoutes(app, opts.db);

  const parsers = createParserRegistry(opts.parsers ?? bankParsers, app.log);
  app.log.info({ senderCodes: parsers.senderCodes }, "sms parsers registered");

  await app.register(ingestRoutes, {
    prefix: "/api/ingest",
    ingestToken: opts.config.INGEST_API_TOKEN,
    deps: {
      db: opts.db,
      parsers,
      clock: opts.clock ?? systemClock,
      dedupWindowMinutes: opts.config.DEDUP_WINDOW_MINUTES,
      log: app.log,
    },
  });

  if (!opts.config.INGEST_API_TOKEN) {
    app.log.warn("INGEST_API_TOKEN is not set: all /api/ingest requests will be rejected");
  }
  return app;
}
