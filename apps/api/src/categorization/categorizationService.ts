import type { Kysely } from "kysely";
import type { Database } from "../db.ts";

export const UNCATEGORIZED = "Uncategorized";

export interface CategoryDecision {
  categoryId: number;
  source: "RULE" | "DEFAULT";
}

/**
 * Decides a category for a NORMALIZED merchant name.
 * Today: first matching rule wins (ordered by priority, then id), else "Uncategorized".
 *
 * Loads all rules per call: with tens of rules and ~10 SMS/day that's fine and always fresh.
 * Cache only when a measurement says so; premature caching brings stale-data bugs.
 *
 * Extension point for the LLM fallback (Step 12): when no rule matches, keep the ingest fast
 * (Uncategorized) and enrich ASYNCHRONOUSLY. A slow network call must never hold a DB
 * transaction open.
 *
 * @param db the caller's transaction, so reads are consistent with the ingest's writes
 */
export async function categorize(
  db: Kysely<Database>,
  merchantNorm: string | null,
): Promise<CategoryDecision> {
  if (merchantNorm) {
    // One query for all rules; we only need category_id, so no join (and no N+1 lookups).
    const rules = await db
      .selectFrom("merchant_rules")
      .select(["pattern", "match_type", "category_id"])
      .orderBy("priority", "asc")
      .orderBy("id", "asc")
      .execute();
    for (const rule of rules) {
      const hit =
        rule.match_type === "EXACT" ? merchantNorm === rule.pattern : merchantNorm.includes(rule.pattern);
      if (hit) return { categoryId: rule.category_id, source: "RULE" };
    }
  }
  const uncategorized = await db
    .selectFrom("categories")
    .select("id")
    .where("name", "=", UNCATEGORIZED)
    .executeTakeFirst();
  if (!uncategorized) {
    // Fail loudly: a missing seed row is a deployment bug, not something to paper over.
    throw new Error(`Category '${UNCATEGORIZED}' missing: have the migrations been applied?`);
  }
  return { categoryId: uncategorized.id, source: "DEFAULT" };
}
