# Readme.md

mvn -Pnative spring-boot:build-image
some info: https://docs.spring.io/spring-framework/reference/core/aot.html

## Payload contract (single shape for everything)

### `PUT /runtime/sessions/{sessionId}/groups/{groupNo}`

```json
{
  "payloadVersion": 1,
  "submissions": [
    {
      "formCode": "FORM_A",
      "fields": {
        "FIELD_A1": "x",
        "FIELD_A2": "y"
      }
    },
    {
      "formCode": "FORM_B",
      "fields": {
        "FIELD_A1": "z",
        "FIELD_B2": "w"
      }
    }
  ]
}
```

### Child-form group (Group 2: FORM_C → choose FORM_CB)

No separate “selection” key. The chosen child submission simply declares its parent:

```json
{
  "payloadVersion": 1,
  "submissions": [
    {
      "formCode": "FORM_CB",
      "parentFormCode": "FORM_C",
      "fields": {
        "FIELD_CB1": "salary"
      }
    }
  ]
}
```

### Combined Payload

Here, we combine the payload

```json
{
  "payloadVersion": 1,
  "submissions": [
    {
      "formCode": "FORM_A",
      "fields": {
        "FIELD_A1": "x",
        "FIELD_A2": "y"
      }
    },
    {
      "formCode": "FORM_B",
      "fields": {
        "FIELD_A1": "z",
        "FIELD_B2": "w"
      }
    },
    {
      "formCode": "FORM_CB",
      "parentFormCode": "FORM_C",
      "fields": {
        "FIELD_CB1": "salary"
      }
    }
  ]
}
```

### In Regards to REDIS

### Postgre for v1

* **Single source of truth** for application + group payloads (`obs_application`, `obs_group_state`)
* **Concurrency safety** via `SELECT … FOR UPDATE` (transaction + lock)
* **Session TTL** using `expires_at` + `touch()` updates
* **Resume / restart** semantics driven by statuses (`IN_PROGRESS`, `READY_FOR_FINALISATION`, `SUPERSEDED`)
* **Auditability** (we don’t lose history like we would with ephemeral Redis keys)
* **Config (Forms and Fields)** are dynamic

For NOW (v1) **pure Postgres** is fine.

### Additional Redis (later)

Not correctness. Mostly **performance + ergonomics**:

* Faster session lookups / TTL handling (native expiry)
* Distributed cache for `JourneyPlan` across instances
* Locks (but you already lock rows in Postgres)
* Rate limiting / idempotency keys (nice, not required)

### When Redis becomes “worth it”

* Running **multiple service instances** and you want shared cache / consistent TTL behaviour without hitting DB as
  much.
* If we need **very high QPS** on submit/current calls.
* We want **hard TTL expiry** without a DB cleanup job (Redis expires keys automatically).

### One small thing to note without Redis

The `expires_at` is enforced only when user query it. For now that’s fine. For later clean up, add a simple scheduled
job later that marks old sessions expired. Not urgent.

This repo avoids premature complexity. The DB-first approach to observe and analysed “get the flow working” move.
