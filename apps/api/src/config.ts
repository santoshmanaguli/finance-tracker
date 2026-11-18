import { z } from "zod";

/**
 * All configuration comes from environment variables (12-factor app, factor III):
 * the same Docker image runs locally, in CI and in production; only the environment changes.
 *
 * Validated ONCE at startup with Zod. A typo'd or missing variable crashes the process
 * immediately with a clear message, instead of failing on the first real request at 2 am.
 */
const ConfigSchema = z.object({
  // Defaults match docker-compose.yml and are for LOCAL DEVELOPMENT ONLY.
  DATABASE_URL: z.url().default("postgres://finance:finance@localhost:5432/finance"),
  DB_POOL_MAX: z.coerce.number().int().positive().default(5),
  HOST: z.string().default("0.0.0.0"),
  PORT: z.coerce.number().int().positive().default(8080),
  LOG_LEVEL: z.enum(["fatal", "error", "warn", "info", "debug", "trace"]).default("info"),
  // Shared secret for POST /api/ingest/sms. Empty = not configured = reject everything (fail closed).
  INGEST_API_TOKEN: z.string().default(""),
  // Dedup layer 3: SMS and email for the same swipe match if within this many minutes.
  DEDUP_WINDOW_MINUTES: z.coerce.number().int().positive().default(15),
});

export type Config = z.infer<typeof ConfigSchema>;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const result = ConfigSchema.safeParse(env);
  if (!result.success) {
    // Print which variables are wrong, never their values (they may be secrets).
    const problems = result.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ");
    throw new Error(`Invalid configuration: ${problems}`);
  }
  return result.data;
}
