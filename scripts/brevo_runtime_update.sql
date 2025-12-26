-- Connect to DB: bootleg
-- Schema: bootleg_runtime

create schema if not exists bootleg_runtime;

-- 1) Durable onboarding case (truth)
create table if not exists bootleg_runtime.obs_application
(
    application_id    uuid primary key,
    journey_id        uuid        not null,
    customer_ref      text        not null,

    status            text        not null, -- IN_PROGRESS | READY_FOR_FINALISATION | COMPLETED | CANCELLED
    current_group_no  int         not null,
    furthest_group_no int         not null,

    version           int         not null default 0,

    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create index if not exists idx_obs_app_customer_journey
    on bootleg_runtime.obs_application (customer_ref, journey_id);

create index if not exists idx_obs_app_status
    on bootleg_runtime.obs_application (status);


-- 2) Session handle (TTL lives here)
create table if not exists bootleg_runtime.obs_session
(
    session_id     uuid primary key,
    application_id uuid        not null references bootleg_runtime.obs_application (application_id),

    status         text        not null, -- ACTIVE | EXPIRED | CLOSED
    expires_at     timestamptz not null,

    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

create index if not exists idx_obs_session_app
    on bootleg_runtime.obs_session (application_id);

create index if not exists idx_obs_session_expires
    on bootleg_runtime.obs_session (expires_at);


-- 3) Per-group latest submission + validity
-- Not started = no row
create table if not exists bootleg_runtime.obs_group_state
(
    application_id     uuid        not null references bootleg_runtime.obs_application (application_id),
    group_no           int         not null,

    status             text        not null, -- VALIDATED | INVALIDATED
    payload            jsonb       not null,

    submission_version int         not null default 0,

    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),

    primary key (application_id, group_no)
);

create index if not exists idx_obs_group_state_app
    on bootleg_runtime.obs_group_state (application_id);

create index if not exists idx_obs_group_state_status
    on bootleg_runtime.obs_group_state (status);

-- Optional: only if you later need to query inside payload a lot
-- create index if not exists idx_obs_group_state_payload_gin
--   on bootleg_runtime.obs_group_state using gin (payload);
