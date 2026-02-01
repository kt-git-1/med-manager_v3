# Test DB Strategy (Integration)

- Use a dedicated test database (`DATABASE_URL` in `.env.test`).
- Apply migrations before integration tests.
- Clean up data between tests (truncate tables or reset schema).

For local runs, you can:
- Use Supabase local if available, or
- Run Postgres via docker-compose for tests.
