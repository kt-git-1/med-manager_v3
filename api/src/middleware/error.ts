export type HttpError = {
  status: number;
  code: string;
  message: string;
};

export function toHttpError(error: unknown): HttpError {
  if (error instanceof Error && "statusCode" in error) {
    return {
      status: (error as Error & { statusCode: number }).statusCode,
      code: "auth_error",
      message: error.message
    };
  }
  return {
    status: 500,
    code: "internal_error",
    message: "Unexpected error"
  };
}

export function errorResponse(error: unknown) {
  const httpError = toHttpError(error);
  return new Response(JSON.stringify({ error: httpError.code, message: httpError.message }), {
    status: httpError.status,
    headers: { "content-type": "application/json" }
  });
}
