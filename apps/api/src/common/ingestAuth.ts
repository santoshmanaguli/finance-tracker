import { createHash, timingSafeEqual } from "node:crypto";
import type { FastifyReply, FastifyRequest } from "fastify";

export const INGEST_TOKEN_HEADER = "x-api-token";

/**
 * Authenticates machine-to-machine calls (the iOS Shortcut) with a static shared token.
 * (Replaced by per-device tokens in Step 3; see docs/plans/phase-2.5-multi-user.md.)
 *
 * - Fail closed: no configured token means every request is rejected. A missing env var in
 *   production must never mean "open to the internet".
 * - Constant-time comparison: a plain `===` returns at the first differing character, so
 *   response timing leaks how much of a guess was right. timingSafeEqual compares every byte.
 *   It requires equal lengths, so both sides are hashed to 32 bytes first (this also avoids
 *   leaking the token's length).
 */
export function createIngestAuthHook(expectedToken: string) {
  const expectedDigest = expectedToken ? sha256(expectedToken) : null;

  return async function ingestAuth(request: FastifyRequest, reply: FastifyReply) {
    const presented = request.headers[INGEST_TOKEN_HEADER];
    const ok =
      expectedDigest !== null &&
      typeof presented === "string" &&
      timingSafeEqual(sha256(presented), expectedDigest);
    if (!ok) {
      // Don't say whether the header was missing or wrong: no hints for an attacker.
      request.log.warn({ ip: request.ip }, "rejected ingest request: missing or invalid token");
      return reply
        .code(401)
        .type("application/problem+json")
        .send({ type: "about:blank", title: "Unauthorized", status: 401 });
    }
  };
}

function sha256(value: string): Buffer {
  return createHash("sha256").update(value, "utf8").digest();
}
