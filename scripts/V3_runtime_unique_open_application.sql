-- 1) Cleanup: if multiple "open" apps exist for the same (customer_ref, journey_id),
-- mark all but the newest as SUPERSEDED (so the unique index won't fail).

WITH ranked AS (
    SELECT
        application_id,
        ROW_NUMBER() OVER (
            PARTITION BY customer_ref, journey_id
            ORDER BY updated_at DESC, created_at DESC
            ) AS rn
    FROM bootleg_runtime.obs_application
    WHERE status IN ('IN_PROGRESS', 'READY_FOR_FINALISATION')
)
UPDATE bootleg_runtime.obs_application a
SET status = 'SUPERSEDED',
    updated_at = now(),
    version = version + 1
FROM ranked r
WHERE a.application_id = r.application_id
  AND r.rn > 1;

-- 2) Enforce "only one open app" via partial unique index
CREATE UNIQUE INDEX IF NOT EXISTS ux_obs_application_one_open_per_customer_journey
    ON bootleg_runtime.obs_application (customer_ref, journey_id)
    WHERE status IN ('IN_PROGRESS', 'READY_FOR_FINALISATION');
