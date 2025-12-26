-- Optional guardrails: prevent garbage status values
-- Comment out if you prefer "free text" during prototyping.

DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'chk_obs_application_status'
        ) THEN
            ALTER TABLE bootleg_runtime.obs_application
                ADD CONSTRAINT chk_obs_application_status
                    CHECK (status IN ('IN_PROGRESS', 'READY_FOR_FINALISATION', 'SUPERSEDED'));
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'chk_obs_session_status'
        ) THEN
            ALTER TABLE bootleg_runtime.obs_session
                ADD CONSTRAINT chk_obs_session_status
                    CHECK (status IN ('ACTIVE', 'EXPIRED'));
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'chk_obs_group_state_status'
        ) THEN
            ALTER TABLE bootleg_runtime.obs_group_state
                ADD CONSTRAINT chk_obs_group_state_status
                    CHECK (status IN ('VALIDATED', 'INVALIDATED'));
        END IF;
    END $$;
