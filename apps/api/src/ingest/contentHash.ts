import { createHash } from "node:crypto";
import { normalizeSender } from "./parsers/senderIds.ts";

export type Source = "SMS" | "EMAIL" | "STATEMENT";

/**
 * Dedup layer 1: a fingerprint of the message CONTENT, used as an idempotency key.
 *
 * Deliberately excluded: the received timestamp (a retried Shortcut call carries a new one)
 * and the operator prefix of the sender (AX- vs JD- is routing, not content).
 * The newline separator prevents ambiguity: ("AB","C") and ("A","BC") hash differently.
 */
export function contentHash(source: Source, sender: string, body: string): string {
  const canonical = `${source}\n${normalizeSender(sender)}\n${body.trim()}`;
  return createHash("sha256").update(canonical, "utf8").digest("hex");
}
