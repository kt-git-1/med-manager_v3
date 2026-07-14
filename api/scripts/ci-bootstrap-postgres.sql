-- Minimal Supabase-owned database objects required by the production
-- migrations when they run against a plain PostgreSQL service in CI.
CREATE SCHEMA IF NOT EXISTS auth;

CREATE OR REPLACE FUNCTION auth.uid()
RETURNS uuid
LANGUAGE sql
STABLE
AS $$ SELECT NULL::uuid $$;

CREATE OR REPLACE FUNCTION auth.role()
RETURNS text
LANGUAGE sql
STABLE
AS $$ SELECT 'service_role'::text $$;

CREATE PUBLICATION supabase_realtime;

CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
