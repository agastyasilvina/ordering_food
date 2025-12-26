-- Add journey_code to runtime application so runtime can validate flow using journeyCode
-- and so snapshots can return journeyCode without extra config lookups.

ALTER TABLE bootleg_runtime.obs_application
    ADD COLUMN IF NOT EXISTS journey_code text;

-- Best-effort backfill from config (only works if brevo_config is in same DB)
UPDATE bootleg_runtime.obs_application a
SET journey_code = j.journey_code
FROM brevo_config.journey_tm j
WHERE a.journey_id = j.journey_id
  AND a.journey_code IS NULL;

-- Optional: enforce NOT NULL once you're confident all rows are backfilled.
-- (Leave commented while you are iterating/testing)
-- ALTER TABLE bootleg_runtime.obs_application
--   ALTER COLUMN journey_code SET NOT NULL;
