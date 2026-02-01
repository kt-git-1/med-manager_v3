export type HttpError = {
  status: number;
  code: string;
  message: string;
};

export function toHttpError(error: unknown): HttpError {
  if (error instanceof Error && "statusCode" in error) {
    const statusCode = (error as Error & { statusCode: number }).statusCode;
    return {
      status: statusCode,
      code: mapStatusCode(statusCode),
      message: error.message
    };
  }
  return {
    status: 500,
    code: "internal_error",
    message: "Unexpected error"
  };
}

function mapStatusCode(status: number): string {
  switch (status) {
    case 401:
      return "unauthorized";
    case 403:
      return "forbidden";
    case 404:
      return "not_found";
    case 409:
      return "conflict";
    case 422:
      return "validation_error";
    case 429:
      return "rate_limited";
    default:
      return "error";
  }
}

export function errorResponse(error: unknown) {
  const httpError = toHttpError(error);
  return new Response(JSON.stringify({ error: httpError.code, message: httpError.message }), {
    status: httpError.status,
    headers: { "content-type": "application/json" }
  });
}
