-- Dev-only bootstrap for local Postgres. These passwords are for local development
-- ONLY and must never be used outside a developer machine.
--
-- No ALTER DEFAULT PRIVILEGES here on purpose: every Flyway migration grants table
-- privileges explicitly, so the decision ledger role can stay INSERT/SELECT-only.

CREATE ROLE parallax_owner LOGIN PASSWORD 'owner-dev';
CREATE ROLE parallax_app   LOGIN PASSWORD 'app-dev';
CREATE ROLE accounts_owner LOGIN PASSWORD 'owner-dev';
CREATE ROLE accounts_app   LOGIN PASSWORD 'app-dev';
CREATE DATABASE parallax OWNER parallax_owner;
CREATE DATABASE accounts OWNER accounts_owner;
\connect parallax
ALTER SCHEMA public OWNER TO parallax_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO parallax_app;
\connect accounts
ALTER SCHEMA public OWNER TO accounts_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO accounts_app;
