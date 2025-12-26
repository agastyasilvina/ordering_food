-- Applications: lookup by (customer_ref, journey_id, status) and order by updated_at
CREATE INDEX IF NOT EXISTS ix_obs_application_lookup_open
    ON bootleg_runtime.obs_application (customer_ref, journey_id, status, updated_at DESC);

-- Sessions: findActive(sessionId) and expireAllActiveByApplicationId(applicationId)
CREATE INDEX IF NOT EXISTS ix_obs_session_by_app_status
    ON bootleg_runtime.obs_session (application_id, status);

CREATE INDEX IF NOT EXISTS ix_obs_session_active_by_id
    ON bootleg_runtime.obs_session (session_id)
    WHERE status = 'ACTIVE';

-- Group state: list states for an application quickly
CREATE INDEX IF NOT EXISTS ix_obs_group_state_by_app
    ON bootleg_runtime.obs_group_state (application_id);
