import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { createIngestAuthHook } from "../common/ingestAuth.ts";
import { type IngestDeps, ingestSms } from "./ingestService.ts";

/**
 * Request schema: the API contract, separate from the DB schema, so each can evolve
 * independently and clients can't set fields they shouldn't. Size limits stop a buggy or
 * hostile client from storing megabytes per "SMS".
 * Moves to packages/shared in Step 7 so the app can reuse it.
 */
export const SmsIngestRequest = z.object({
  sender: z.string().trim().min(1).max(64),
  body: z
    .string()
    .min(1)
    .max(2000)
    .refine((s) => s.trim().length > 0, "must not be blank"),
  /** ISO-8601 with offset, e.g. 2026-11-04T10:15:30+05:30. Optional. */
  receivedAt: z.iso.datetime({ offset: true }).optional(),
});

/**
 * HTTP adapter only: authenticate, validate, translate to a command, translate the result
 * to a status code. No business logic here.
 */
export async function ingestRoutes(
  app: FastifyInstance,
  opts: { deps: IngestDeps; ingestToken: string },
): Promise<void> {
  // Encapsulated plugin: this hook applies ONLY to routes registered in this plugin, so a new
  // ingest route can't "forget" auth, and health routes stay public.
  app.addHook("onRequest", createIngestAuthHook(opts.ingestToken));

  app.post("/sms", async (request, reply) => {
    const req = SmsIngestRequest.parse(request.body); // ZodError -> 400 via the error handler
    const result = await ingestSms(
      { ...opts.deps, log: request.log },
      {
        sender: req.sender,
        body: req.body,
        receivedAt: req.receivedAt ? new Date(req.receivedAt) : null,
      },
    );
    // 201 when a new transaction was created; 200 for every other successful outcome
    // (a retry or a duplicate is still success for the client: that's idempotency).
    return reply.code(result.status === "CREATED" ? 201 : 200).send(result);
  });
}
