### 1) DB tables (source of truth)

* `journey_tm` says what journeys exist (and active)
* mapping table (e.g. `journey_form_tr`) says what **groups** exist for a journey
* form/field/rule tables define the actual **FormDefinition** + **FieldDefinition** + **FieldRule**

### 2) `GroupLoader` (builder)

Given `(journeyCode, groupNo)` it queries DB and **builds**:

* `GroupDefinition`
  ↳ contains `List<FormDefinition>`
  ↳ each form contains `List<FieldDefinition>`
  ↳ each field contains `List<FieldRule>` (length/value rules etc.)

So yes: **loading a GroupDefinition loads the forms/fields/rules as part of it** (because the loader assembles it).

### 3) `GroupDefinitionProvider` (cache wrapper)

This does not invent config. It just says:

* “If we already loaded `(journeyCode, groupNo)` recently, return cached `GroupDefinition`”
* otherwise call `GroupLoader.loadGroup(...)` and cache it

### 4) `/config/refresh` (admin-ish endpoint)

This endpoint is just a **trigger**:

* find all `groupNo` for `journeyCode`
* evict cache for each group
* call `provider.get(journeyCode, groupNo)` to force reload

So refresh doesn’t “list the journey” (unless you add another endpoint for that). It refreshes config for a journey.

### 5) Validation uses cached config

When user calls `/payload/isValid`:

* it calls `GroupValidationService`
* which calls `provider.get(journeyCode, groupNo)` (cached)
* then picks the right `FormDefinition` for each `FormSubmission`
* then calls `FormValidationService.validate(formDef, formSub)`
* rules are already inside `formDef` → no extra DB calls

So yes: **validator uses the cached GroupDefinition, which already contains FormDefinition and FieldRules**.

---

## The simplest picture (who calls who)

**Refresh path**

```
/config/refresh?journeyCode=J1
  -> GroupKeyRepository (get groupNos)
  -> provider.evict(J1, groupNo)
  -> provider.get(J1, groupNo)  // loads via GroupLoader and caches
```

**Validation path**

```
/payload/isValid?journeyCode=J1&groupNo=2
  -> jsonSubmissionMapper (GroupSubmission)
  -> GroupValidationService
      -> provider.get(J1, 2)   // cached GroupDefinition incl forms/fields/rules
      -> find FormDefinition by formCode
      -> FormValidationService.validate(formDef, formSub)
```

No DB queries inside `FormValidationService`. It only reads the definitions.

---

## Where the confusion comes from

You were thinking “refresh lists journeys”. That’s a *different endpoint*.

You can have two endpoints:

1. `GET /config/journeys` → list journeys (from `journey_tm`)
2. `POST /config/refresh?journeyCode=...` → refresh cached group definitions for that journey

They’re separate jobs.

---

### One-line truth

`/config/refresh` pre-warms the cache, and `/payload/isValid` consumes that cache; rules are loaded as part of GroupDefinition building.

