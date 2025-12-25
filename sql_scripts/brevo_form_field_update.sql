-- ---------------------------------------------------------------------------
-- 1) Add field_type to brevo_config.field_tm
-- ---------------------------------------------------------------------------
ALTER TABLE brevo_config.field_tm
ADD COLUMN IF NOT EXISTS field_type TEXT NOT NULL DEFAULT 'TEXT';

-- Optional safety (note schema-qualified table name)
ALTER TABLE brevo_config.field_tm
ADD CONSTRAINT ck_field_type
CHECK (field_type IN ('TEXT', 'NOMINAL'));


-- ---------------------------------------------------------------------------
-- 2) Create brevo_config.form_field_rule_tr
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS brevo_config.form_field_rule_tr (
  form_id     UUID NOT NULL REFERENCES brevo_config.form_tm(form_id),
  field_id    UUID NOT NULL REFERENCES brevo_config.field_tm(field_id),

  rule_kind   TEXT NOT NULL, -- LENGTH / VALUE
  min_value   NUMERIC NULL,
  max_value   NUMERIC NULL,

  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

  PRIMARY KEY (form_id, field_id, rule_kind)
);

-- Optional safety
ALTER TABLE brevo_config.form_field_rule_tr
ADD CONSTRAINT ck_rule_kind
CHECK (rule_kind IN ('LENGTH', 'VALUE'));

ALTER TABLE brevo_config.form_field_rule_tr
ADD CONSTRAINT ck_rule_min_le_max
CHECK (min_value IS NULL OR max_value IS NULL OR min_value <= max_value);

CREATE INDEX IF NOT EXISTS ix_rule_form_field
ON brevo_config.form_field_rule_tr(form_id, field_id);
