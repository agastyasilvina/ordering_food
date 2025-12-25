-- ============================================================
-- 0) WIPE (config only) - run inside the DB that contains brevo_config schema
-- ============================================================

TRUNCATE TABLE
  brevo_config.group_invalidation_rule_tr,
  brevo_config.journey_group_tr,
  brevo_config.group_form_tr,
  brevo_config.field_parameter_form_tr,
  brevo_config.form_field_rule_tr,
  brevo_config.form_field_tr,
  brevo_config.journey_tm,
  brevo_config.group_tm,
  brevo_config.form_tm,
  brevo_config.field_tm
RESTART IDENTITY
CASCADE;

-- ============================================================
-- 1) MASTER DATA
-- ============================================================

-- Journeys
INSERT INTO brevo_config.journey_tm (journey_code, journey_name)
VALUES
  ('JOURNEY_A', 'Journey A (1->2)'),
  ('JOURNEY_B', 'Journey B (1->2->4)');

-- Groups (steps)
INSERT INTO brevo_config.group_tm (group_no, group_name)
VALUES
  (1, 'Group 1'),
  (2, 'Group 2'),
  (4, 'Group 4');

-- Forms
INSERT INTO brevo_config.form_tm (form_code, form_name)
VALUES
  ('FORM_A',  'Form A'),
  ('FORM_B',  'Form B'),
  ('FORM_C',  'Form C (parent)'),
  ('FORM_CA', 'Form CA (child)'),
  ('FORM_CB', 'Form CB (child)'),
  ('FORM_CC', 'Form CC (child)'),
  ('FORM_D',  'Form D');

-- Fields (now includes field_type)
-- TEXT = string length rules
-- NOMINAL = numeric value rules
INSERT INTO brevo_config.field_tm (field_code, field_name, field_type)
VALUES
  ('FIELD_A1', 'Field A1', 'TEXT'),
  ('FIELD_A2', 'Field A2', 'NOMINAL'),
  ('FIELD_B2', 'Field B2', 'TEXT'),
  ('FIELD_CA1','Field CA1','TEXT'),
  ('FIELD_CB1','Field CB1','NOMINAL'),
  ('FIELD_CC1','Field CC1','TEXT'),
  ('FIELD_D1', 'Field D1', 'NOMINAL');

-- ============================================================
-- 2) FORM ↔ FIELD (required + order)
-- ============================================================

INSERT INTO brevo_config.form_field_tr (form_id, field_id, is_required, sort_order)
SELECT f.form_id, fld.field_id, v.is_required, v.sort_order
FROM (VALUES
  ('FORM_A','FIELD_A1', true, 1),
  ('FORM_A','FIELD_A2', true, 2),

  ('FORM_B','FIELD_A1', true, 1),
  ('FORM_B','FIELD_B2', false, 2),

  ('FORM_CA','FIELD_CA1', true, 1),
  ('FORM_CB','FIELD_CB1', true, 1),
  ('FORM_CC','FIELD_CC1', true, 1),

  ('FORM_D','FIELD_D1', true, 1)
) v(form_code, field_code, is_required, sort_order)
JOIN brevo_config.form_tm  f   ON f.form_code  = v.form_code
JOIN brevo_config.field_tm fld ON fld.field_code = v.field_code;

-- ============================================================
-- 3) RULES (separate table) - per form + field
-- ============================================================

-- For TEXT fields: LENGTH(min,max)
-- For NOMINAL fields: VALUE(min,max)
INSERT INTO brevo_config.form_field_rule_tr (form_id, field_id, rule_kind, min_value, max_value)
SELECT f.form_id, fld.field_id, v.rule_kind, v.min_value, v.max_value
FROM (VALUES
  -- FORM_A
  ('FORM_A','FIELD_A1','LENGTH', 1, 50),
  ('FORM_A','FIELD_A2','VALUE',  0, 1000000),

  -- FORM_B
  ('FORM_B','FIELD_A1','LENGTH', 1, 50),
  ('FORM_B','FIELD_B2','LENGTH', 0, 30),   -- optional field, but if present max 30

  -- Children of FORM_C
  ('FORM_CA','FIELD_CA1','LENGTH', 2, 80),
  ('FORM_CB','FIELD_CB1','VALUE',  1, 9999),
  ('FORM_CC','FIELD_CC1','LENGTH', 5, 200),

  -- FORM_D
  ('FORM_D','FIELD_D1','VALUE',  10, 999999999)
) v(form_code, field_code, rule_kind, min_value, max_value)
JOIN brevo_config.form_tm  f   ON f.form_code  = v.form_code
JOIN brevo_config.field_tm fld ON fld.field_code = v.field_code;

-- ============================================================
-- 4) PARENT → CHILD FORMS (FORM_C -> CA/CB/CC)
-- ============================================================

INSERT INTO brevo_config.field_parameter_form_tr (parent_form_tm_id, child_form_tm_id)
SELECT p.form_id, c.form_id
FROM brevo_config.form_tm p
JOIN brevo_config.form_tm c ON c.form_code IN ('FORM_CA','FORM_CB','FORM_CC')
WHERE p.form_code = 'FORM_C';

-- ============================================================
-- 5) GROUP → FORMS
-- ============================================================

INSERT INTO brevo_config.group_form_tr (group_id, form_id, sort_order)
SELECT g.group_id, f.form_id, v.sort_order
FROM (VALUES
  (1,'FORM_A',1),
  (1,'FORM_B',2),
  (2,'FORM_C',1),
  (4,'FORM_D',1)
) v(group_no, form_code, sort_order)
JOIN brevo_config.group_tm g ON g.group_no = v.group_no
JOIN brevo_config.form_tm  f ON f.form_code = v.form_code;

-- ============================================================
-- 6) JOURNEY → GROUPS (ordered)
-- ============================================================

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
JOIN brevo_config.group_tm   g ON g.group_no = v.group_no;

-- ============================================================
-- 7) INVALIDATION RULES (example)
-- ============================================================

INSERT INTO brevo_config.group_invalidation_rule_tr (journey_id, trigger_group_id, invalidated_group_id)
SELECT j.journey_id, g2.group_id, g4.group_id
FROM brevo_config.journey_tm j
JOIN brevo_config.group_tm g2 ON g2.group_no = 2
JOIN brevo_config.group_tm g4 ON g4.group_no = 4
WHERE j.journey_code = 'JOURNEY_B';
