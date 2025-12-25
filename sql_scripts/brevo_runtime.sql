-- Run this INSIDE the brevo_runtime database.
-- (Creating the database itself is usually done separately by a DBA/admin.)

-- ---------------------------------------------------------------------------
-- Application (durable onboarding case)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS application (
  application_id      UUID PRIMARY KEY,
  journey_code        TEXT NOT NULL,

  -- One of these might be null early; later you can attach customer_id
  customer_id         TEXT NULL,
  client_id           TEXT NULL,

  current_group_no    INT  NOT NULL,
  furthest_group_no   INT  NOT NULL,

  status              TEXT NOT NULL, -- IN_PROGRESS / COMPLETED / CANCELLED / EXPIRED
  created_at          TIMESTAMPTZ NOT NULL,
  updated_at          TIMESTAMPTZ NOT NULL
);

-- One active application per customer + journey (only when customer_id exists)
CREATE UNIQUE INDEX IF NOT EXISTS ux_app_active_customer_journey
  ON application(customer_id, journey_code)
  WHERE customer_id IS NOT NULL AND status = 'IN_PROGRESS';

-- Useful for pre-auth resume by client_id
CREATE INDEX IF NOT EXISTS ix_app_client_journey_status
  ON application(client_id, journey_code, status);


-- ---------------------------------------------------------------------------
-- Session (disposable runtime handle)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS session (
  session_id       UUID PRIMARY KEY,
  application_id   UUID NOT NULL REFERENCES application(application_id),
  status           TEXT NOT NULL, -- ACTIVE / ENDED
  created_at       TIMESTAMPTZ NOT NULL,
  ended_at         TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS ix_session_application
  ON session(application_id);


-- ---------------------------------------------------------------------------
-- Latest submission per group (simple "latest wins" model)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS group_submission (
  application_id   UUID NOT NULL REFERENCES application(application_id),
  group_no         INT  NOT NULL,
  payload_json     JSONB NOT NULL,
  version          INT  NOT NULL,
  updated_at       TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(application_id, group_no)
);


-- ---------------------------------------------------------------------------
-- State per group (invalidate later groups when earlier groups change)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS group_state (
  application_id     UUID NOT NULL REFERENCES application(application_id),
  group_no           INT  NOT NULL,
  state              TEXT NOT NULL, -- NOT_STARTED / VALID / INVALID / SKIPPED
  updated_at         TIMESTAMPTZ NOT NULL,
  invalidated_reason TEXT NULL,
  PRIMARY KEY(application_id, group_no)
);
