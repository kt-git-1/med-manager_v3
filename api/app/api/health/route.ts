export async function GET() {
  return new Response(JSON.stringify({ status: "ok" }), {
    headers: {
      "cache-control": "no-store",
      "content-type": "application/json"
    }
  });
}
