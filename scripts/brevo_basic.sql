-- ============================================================
-- BREVO Config: config + runtime (minimal, practical)
-- PostgreSQL 12+ recommended
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS brevo_config;
CREATE SCHEMA IF NOT EXISTS brevo_runtime;

-- ============================================================
-- 1) CONFIG TABLES (brevo_config)
-- ============================================================

-- Journeys
CREATE TABLE IF NOT EXISTS brevo_config.journey_tm (
  journey_id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  journey_code text NOT NULL UNIQUE,
  journey_name text NOT NULL,
  is_active    boolean NOT NULL DEFAULT true,
  created_at   timestamptz NOT NULL DEFAULT now()
);

-- Groups (Steps)
CREATE TABLE IF NOT EXISTS brevo_config.group_tm (
  group_id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  group_no   int  NOT NULL UNIQUE,          -- the step number: 1,2,3,5 etc.
  group_name text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Journey → Groups (ordered)
CREATE TABLE IF NOT EXISTS brevo_config.journey_group_tr (
  journey_id uuid NOT NULL REFERENCES brevo_config.journey_tm(journey_id) ON DELETE CASCADE,
  group_id   uuid NOT NULL REFERENCES brevo_config.group_tm(group_id) ON DELETE RESTRICT,
  position   int  NOT NULL,                 -- order within the journey
  PRIMARY KEY (journey_id, group_id),
  UNIQUE (journey_id, position)
);

-- Forms
CREATE TABLE IF NOT EXISTS brevo_config.form_tm (
  form_id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  form_code   text NOT NULL UNIQUE,         -- e.g. FORM_A
  form_name   text NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now()
);

-- Fields
CREATE TABLE IF NOT EXISTS brevo_config.field_tm (
  field_id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  field_code  text NOT NULL UNIQUE,         -- e.g. FIELD_A1
  field_name  text NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now()
);

-- Form → Fields
CREATE TABLE IF NOT EXISTS brevo_config.form_field_tr (
  form_id     uuid NOT NULL REFERENCES brevo_config.form_tm(form_id) ON DELETE CASCADE,
  field_id    uuid NOT NULL REFERENCES brevo_config.field_tm(field_id) ON DELETE RESTRICT,
  is_required boolean NOT NULL DEFAULT true,
  sort_order  int NOT NULL DEFAULT 1,
  PRIMARY KEY (form_id, field_id)
);

-- Group → Forms
CREATE TABLE IF NOT EXISTS brevo_config.group_form_tr (
  group_id   uuid NOT NULL REFERENCES brevo_config.group_tm(group_id) ON DELETE CASCADE,
  form_id    uuid NOT NULL REFERENCES brevo_config.form_tm(form_id) ON DELETE RESTRICT,
  sort_order int NOT NULL DEFAULT 1,
  PRIMARY KEY (group_id, form_id)
);

-- Parent form → child form mapping (your fpft concept)
CREATE TABLE IF NOT EXISTS brevo_config.field_parameter_form_tr (
  parent_form_tm_id uuid NOT NULL REFERENCES brevo_config.form_tm(form_id) ON DELETE CASCADE,
  child_form_tm_id  uuid NOT NULL REFERENCES brevo_config.form_tm(form_id) ON DELETE CASCADE,
  PRIMARY KEY (parent_form_tm_id, child_form_tm_id)
);

-- Group invalidation rule:
-- "if TRIGGER group is updated, WIPE invalidated group(s) for that journey"
CREATE TABLE IF NOT EXISTS brevo_config.group_invalidation_rule_tr (
  rule_id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  journey_id           uuid NOT NULL REFERENCES brevo_config.journey_tm(journey_id) ON DELETE CASCADE,
  trigger_group_id     uuid NOT NULL REFERENCES brevo_config.group_tm(group_id) ON DELETE RESTRICT,
  invalidated_group_id uuid NOT NULL REFERENCES brevo_config.group_tm(group_id) ON DELETE RESTRICT,
  created_at           timestamptz NOT NULL DEFAULT now(),
  UNIQUE (journey_id, trigger_group_id, invalidated_group_id)
);

-- ============================================================
-- 2) RUNTIME TABLES (brevo_runtime)
-- ============================================================

-- A customer onboarding "session" (persisted so they can resume later)
CREATE TABLE IF NOT EXISTS brevo_runtime.onboarding_session (
  session_id       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  journey_id       uuid NOT NULL REFERENCES brevo_config.journey_tm(journey_id) ON DELETE RESTRICT,

  -- optional identifiers (keep nullable until you need them)
  customer_ref     text NULL,   -- e.g. CIF, user_id, etc.

  status           text NOT NULL DEFAULT 'IN_PROGRESS'
                  CHECK (status IN ('IN_PROGRESS','READY_FOR_SUBMISSION','CANCELLED')),

  current_group_no int NOT NULL DEFAULT 1,  -- where the UI should land
  version          int NOT NULL DEFAULT 0,  -- optimistic concurrency for writes

  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now()
);

-- Per-group saved payload.
-- Minimal approach: store JSONB so you can evolve without migrations every Tuesday.
CREATE TABLE IF NOT EXISTS brevo_runtime.onboarding_group_state (
  session_id  uuid NOT NULL REFERENCES brevo_runtime.onboarding_session(session_id) ON DELETE CASCADE,
  group_no    int  NOT NULL,
  is_complete boolean NOT NULL DEFAULT false,
  payload     jsonb NOT NULL DEFAULT '{}'::jsonb,
  updated_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (session_id, group_no)
);

CREATE INDEX IF NOT EXISTS idx_onboarding_group_state_session
  ON brevo_runtime.onboarding_group_state(session_id);

-- ============================================================
-- 3) SEED DATA (minimal demo)
-- ============================================================

-- Journeys
INSERT INTO brevo_config.journey_tm (journey_code, journey_name)
VALUES
  ('JOURNEY_A', 'Journey A (1->2)'),
  ('JOURNEY_B', 'Journey B (1->2->4)')
ON CONFLICT (journey_code) DO NOTHING;

-- Groups (steps)
INSERT INTO brevo_config.group_tm (group_no, group_name)
VALUES
  (1, 'Group 1'),
  (2, 'Group 2'),
  (4, 'Group 4')
ON CONFLICT (group_no) DO NOTHING;

-- Forms
INSERT INTO brevo_config.form_tm (form_code, form_name)
VALUES
  ('FORM_A',  'Form A'),
  ('FORM_B',  'Form B'),
  ('FORM_C',  'Form C (parent)'),
  ('FORM_CA', 'Form CA (child)'),
  ('FORM_CB', 'Form CB (child)'),
  ('FORM_CC', 'Form CC (child)'),
  ('FORM_D',  'Form D')
ON CONFLICT (form_code) DO NOTHING;

-- Fields
INSERT INTO brevo_config.field_tm (field_code, field_name)
VALUES
  ('FIELD_A1', 'Field A1'),
  ('FIELD_A2', 'Field A2'),
  ('FIELD_B2', 'Field B2'),
  ('FIELD_CA1','Field CA1'),
  ('FIELD_CB1','Field CB1'),
  ('FIELD_CC1','Field CC1'),
  ('FIELD_D1', 'Field D1')
ON CONFLICT (field_code) DO NOTHING;

-- Form → Field mappings (required=true by default)
INSERT INTO brevo_config.form_field_tr (form_id, field_id, sort_order)
SELECT f.form_id, fld.field_id, v.sort_order
FROM (VALUES
  ('FORM_A','FIELD_A1',1),
  ('FORM_A','FIELD_A2',2),
  ('FORM_B','FIELD_A1',1),
  ('FORM_B','FIELD_B2',2),
  ('FORM_CA','FIELD_CA1',1),
  ('FORM_CB','FIELD_CB1',1),
  ('FORM_CC','FIELD_CC1',1),
  ('FORM_D','FIELD_D1',1)
) v(form_code, field_code, sort_order)
JOIN brevo_config.form_tm f    ON f.form_code = v.form_code
JOIN brevo_config.field_tm fld ON fld.field_code = v.field_code
ON CONFLICT (form_id, field_id) DO NOTHING;

-- Parent → child forms for FORM_C
INSERT INTO brevo_config.field_parameter_form_tr (parent_form_tm_id, child_form_tm_id)
SELECT p.form_id, c.form_id
FROM brevo_config.form_tm p
JOIN brevo_config.form_tm c ON c.form_code IN ('FORM_CA','FORM_CB','FORM_CC')
WHERE p.form_code = 'FORM_C'
ON CONFLICT DO NOTHING;

-- Group → Forms
INSERT INTO brevo_config.group_form_tr (group_id, form_id, sort_order)
SELECT g.group_id, f.form_id, v.sort_order
FROM (VALUES
  (1,'FORM_A',1),
  (1,'FORM_B',2),
  (2,'FORM_C',1),
  (4,'FORM_D',1)
) v(group_no, form_code, sort_order)
JOIN brevo_config.group_tm g ON g.group_no = v.group_no
JOIN brevo_config.form_tm  f ON f.form_code = v.form_code
ON CONFLICT (group_id, form_id) DO NOTHING;

-- Journey → Groups (ordered)
INSERT INTO brevo_config.journey_group_tr (journey_id, group_id, position)
SELECT j.journey_id, g.group_id, v.position
FROM (VALUES
  ('JOURNEY_A', 1, 1),
  ('JOURNEY_A', 2, 2),
  ('JOURNEY_B', 1, 1),
  ('JOURNEY_B', 2, 2),
  ('JOURNEY_B', 4, 3)
) v(journey_code, group_no, position)
JOIN brevo_config.journey_tm j ON j.journey_code = v.journey_code
JOIN brevo_config.group_tm   g ON g.group_no = v.group_no
ON CONFLICT (journey_id, group_id) DO NOTHING;

-- Invalidation rule: if group 2 updated -> wipe group 4 (Journey B)
INSERT INTO brevo_config.group_invalidation_rule_tr (journey_id, trigger_group_id, invalidated_group_id)
SELECT j.journey_id, g2.group_id, g4.group_id
FROM brevo_config.journey_tm j
JOIN brevo_config.group_tm g2 ON g2.group_no = 2
JOIN brevo_config.group_tm g4 ON g4.group_no = 4
WHERE j.journey_code = 'JOURNEY_B'
ON CONFLICT DO NOTHING;
