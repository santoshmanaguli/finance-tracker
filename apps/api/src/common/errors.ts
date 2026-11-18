import { STATUS_CODES } from "node:http";
import type { FastifyError, FastifyInstance, FastifyReply } from "fastify";
import { ZodError } from "zod";

/**
 * One place that turns errors into HTTP responses, in RFC 9457 "Problem Details" format
 * (application/problem+json): a standard error shape every client can rely on.
 */
export function registerErrorHandler(app: FastifyInstance): void {
  app.setErrorHandler((error: FastifyError | ZodError | Error, request, reply) => {
    if (error instanceof ZodError) {
      return problem(reply, 400, "Bad Request", "Request validation failed", {
        errors: error.issues.map((i) => ({ path: i.path.join("."), message: i.message })),
      });
    }
    // Postgres unique_violation. In this app it almost always means two identical requests
    // raced past the "already exists?" check. The first one won and its data is safe.
    if (isPgError(error) && error.code === "23505") {
      request.log.info({ constraint: error.constraint }, "unique violation (likely concurrent duplicate)");
      return problem(reply, 409, "Conflict", "Conflicting or duplicate data");
    }
    // Fastify's own client errors (malformed JSON, wrong content type, body too large...).
    const status = "statusCode" in error && typeof error.statusCode === "number" ? error.statusCode : 500;
    if (status < 500) {
      return problem(reply, status, STATUS_CODES[status] ?? "Error", error.message);
    }
    // Unexpected: log everything server-side, reveal nothing to the client.
    request.log.error({ err: error }, "unhandled error");
    return problem(reply, 500, "Internal Server Error", "Unexpected error");
  });

  app.setNotFoundHandler((_request, reply) => problem(reply, 404, "Not Found", "No such endpoint"));
}

function problem(
  reply: FastifyReply,
  status: number,
  title: string,
  detail: string,
  extra: Record<string, unknown> = {},
) {
  return reply
    .code(status)
    .type("application/problem+json")
    .send({ type: "about:blank", title, status, detail, ...extra });
}

interface PgError extends Error {
  code: string;
  constraint?: string;
}

function isPgError(error: unknown): error is PgError {
  return error instanceof Error && typeof (error as Partial<PgError>).code === "string";
}
