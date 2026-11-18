import type { FastifyInstance } from "fastify";
import { afterAll, beforeAll, describe, expect, inject, it } from "vitest";
import { createDb, type Db } from "../../src/db.ts";
import { createTestDb } from "../support/db.ts";
import { buildTestApp } from "../support/testApp.ts";

describe("health probes", () => {
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

  it("liveness is UP", async () => {
    const res = await app.inject({ method: "GET", url: "/health/live" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ status: "UP" });
  });

  it("readiness is UP when the database is reachable", async () => {
    const res = await app.inject({ method: "GET", url: "/health/ready" });
    expect(res.statusCode).toBe(200);
    expect(res.body).not.toMatch(/postgres|version|host/i); // no leaked details
  });

  it("readiness is DOWN (503) when the database is unreachable", async () => {
    const deadDb = createDb("postgres://nobody:nothing@127.0.0.1:1/none", 1);
    const deadApp = await buildTestApp(deadDb, "postgres://nobody:nothing@127.0.0.1:1/none");
    try {
      const res = await deadApp.inject({ method: "GET", url: "/health/ready" });
      expect(res.statusCode).toBe(503);
      // Liveness must NOT depend on the database: restarting the app won't fix the DB.
      expect((await deadApp.inject({ method: "GET", url: "/health/live" })).statusCode).toBe(200);
    } finally {
      await deadApp.close();
      await deadDb.destroy();
    }
  });
});
