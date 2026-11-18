import { buildApp } from "./app.ts";
import { loadConfig } from "./config.ts";
import { createDb } from "./db.ts";

const config = loadConfig();
const db = createDb(config.DATABASE_URL, config.DB_POOL_MAX);
const app = await buildApp({ config, db });

/*
 * Graceful shutdown. Docker / Kubernetes send SIGTERM before killing a container:
 * stop accepting new requests, let in-flight ones finish, close the DB pool, then exit.
 * Without this, a deploy can cut a request in half.
 */
let shuttingDown = false;
for (const signal of ["SIGTERM", "SIGINT"] as const) {
  process.on(signal, async () => {
    if (shuttingDown) return;
    shuttingDown = true;
    app.log.info({ signal }, "shutting down");
    try {
      await app.close();
      await db.destroy();
      process.exit(0);
    } catch (err) {
      app.log.error({ err }, "error during shutdown");
      process.exit(1);
    }
  });
}

try {
  await app.listen({ host: config.HOST, port: config.PORT });
} catch (err) {
  app.log.fatal({ err }, "failed to start");
  await db.destroy();
  process.exit(1);
}
