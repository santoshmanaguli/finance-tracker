import type { FastifyInstance } from "fastify";
import { sql } from "kysely";
import type { Db } from "../db.ts";

/**
 * Kubernetes-style probes (used by Docker healthchecks now, k3s in Step 9):
 * - liveness:  "is the process alive?" Never touches dependencies. A failing liveness probe
 *              RESTARTS the container, and restarting won't fix a database outage.
 * - readiness: "can it serve traffic right now?" Checks the database. A failing readiness
 *              probe only takes the instance out of rotation until it recovers.
 * Responses carry no details (no DB version, no hostnames): they're public endpoints.
 */
export function registerHealthRoutes(app: FastifyInstance, db: Db): void {
  app.get("/health/live", async () => ({ status: "UP" }));

  app.get("/health/ready", async (request, reply) => {
    try {
      await sql`SELECT 1`.execute(db);
      return { status: "UP" };
    } catch (err) {
      request.log.warn({ err }, "readiness check failed");
      return reply.code(503).send({ status: "DOWN" });
    }
  });
}
