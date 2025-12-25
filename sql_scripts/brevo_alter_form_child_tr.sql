BEGIN;

-- Rename the old table to the new name (only if the old one exists)
DO $$
BEGIN
  IF to_regclass('brevo_config.field_parameter_form_tr') IS NOT NULL
     AND to_regclass('brevo_config.form_child_tr') IS NULL THEN
    ALTER TABLE brevo_config.field_parameter_form_tr
      RENAME TO form_child_tr;
  END IF;
END $$;

-- Sanity check: show what exists now
SELECT
  to_regclass('brevo_config.form_child_tr')            AS form_child_tr,
  to_regclass('brevo_config.field_parameter_form_tr')  AS old_name;

COMMIT;
