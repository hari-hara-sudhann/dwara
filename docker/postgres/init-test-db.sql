-- Runs only on a fresh Postgres data directory (docker-entrypoint-initdb.d).
-- Creates the database the test profile points at, owned by the app user.
-- The dev database named by POSTGRES_DB is created by the image itself.
SELECT 'CREATE DATABASE dwara_test OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'dwara_test')\gexec
