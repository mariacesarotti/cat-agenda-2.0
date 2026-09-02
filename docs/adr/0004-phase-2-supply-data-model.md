# 0004: Phase 2 Supply/Restock Data Model

## Status
Accepted

## Context
P2-3 and P2-4 need the app to determine which generic household supplies
(food, litter, flea/tick prevention, medication, etc.) a household needs,
at what cadence, and to track *actual* restock timing per item so the
system tells the owner when they'll really run out — not just infer it from
cat count/age/conditions alone. This is genuinely new persistent state:
Phase 1 has no supply entities at all.

Three things need a shape:
1. What counts as a "trackable supply item" (a fixed set vs. an open/data-
   driven catalog).
2. How "cat count/age/conditions → which items, what cadence" is decided.
3. How "restocked → timer reset" is persisted per item type, per household.

## Options Considered

**Option A — Fixed enum catalog + coded rules + a per-user tracker entity**
- `SupplyItemType` is a Java enum (e.g. `FOOD`, `LITTER`,
  `FLEA_TICK_PREVENTION`, `MEDICATION`, ...) — the same pattern already
  established by `VaccineType` in Phase 1.
- A new `SupplyTracker` entity/table: `(id, user_id FK NOT NULL, item_type,
  last_restocked_at nullable, created_at)`, one row per user per item type
  that has ever been marked restocked. Keyed to `User`, not `Cat`, because
  P2-3 frames this as *household* need (shared litter box, shared food),
  not something one cat owns.
- "Which item types apply + what cadence" (driven by cat count, age,
  conditions) lives as Java logic in a `SupplyReasoningService` (see ADR
  0003) — plain code, not database-driven configuration.
- Pros: reuses a pattern already proven in this codebase (enum + tracked
  entity, exactly like `VaccineType`/`VaccinationRecord`); type-safe,
  compiler-checked — no risk of a bad catalog row silently breaking
  reasoning; trivial to unit test; no new infrastructure.
- Cons: adding a new generic item type later means a code change and
  redeploy, not a data change; if the rule set (condition → item, cadence
  adjustments) grows very large, coded rules can get harder to read than a
  declarative table — though the acceptance criteria describe on the order
  of a handful of rules, not dozens.

**Option B — DB-driven catalog + rules table**
- `SupplyItemType` becomes a table (`id, key, display_name,
  base_cadence_days`) instead of an enum.
- A `SupplyRule` table maps triggers ("has condition X," "cat count ≥ N,"
  "age ≥ N") to item types and cadence adjustments — effectively a small,
  content-managed rules engine.
- `SupplyTracker` shaped the same as in Option A.
- Pros: new item types/rules addable via data, no redeploy; cleaner
  separation between "what triggers what" and "how it's computed" if the
  rule set grows complex later.
- Cons: meaningfully more ceremony (migrations, seed data, rule-row
  correctness, a small in-house rules engine to build and maintain) for a
  catalog that P2-3's acceptance criteria deliberately keep small and slow-
  changing ("generic categories only... never a specific brand or
  product"). Solves a problem — frequent catalog churn — that this app's
  scope doesn't actually have.

## Decision
Option A: fixed `SupplyItemType` enum, coded rules in `SupplyReasoningService`
(see ADR 0003), and a `SupplyTracker` entity keyed to `User`.

```
User (existing, ADR 0001)
   1
   │ owns
   *
SupplyTracker (id, user_id FK NOT NULL, item_type, last_restocked_at, created_at)
   UNIQUE (user_id, item_type)
```

`SupplyItemType` is a Java enum living in the new `supply` package,
following the same "enum + escape hatch not needed here" pattern as
`VaccineType` (no free-text variant needed — P2-3 explicitly restricts this
to generic categories, a closed set). A `SupplyTracker` row is created the
first time an item is marked restocked for a given user (the mark-restocked
action itself is Phase 4 UI, but the underlying service method belongs to
Phase 2 per P2-4). If no row exists yet for a given user/item-type pair,
`SupplyReasoningService` treats `last_restocked_at` as absent and falls back
to a defined default so the item still appears in "what's needed" output
(P2-4's AC) — the exact fallback value is an open item, see the Phase 2
tech-task doc.

Household scope is treated as `== User` (1:1) for now, the same
simplification Phase 1 already makes for cat ownership.

## Consequences
- Reuses a pattern the client has already learned in Phase 1
  (`VaccineType`/`VaccinationRecord`), reducing the amount of genuinely new
  modeling to absorb in Phase 2.
- Adding a new supply category (e.g. a future "dental care" item) is a
  small, reviewable code change (new enum constant + rule), consistent with
  how new vaccine types would be added today — not a schema migration, but
  also not a silent data edit that bypasses code review.
- Keeps the "generic categories only, never a brand" constraint structurally
  enforced: the enum can't accidentally hold a brand name the way a free-
  text catalog table could.
- `SupplyTracker` rows are keyed to `user_id`, matching the ownership model
  ADR 0001 already established for `Cat` — each user's supply tracking is
  fully independent, no shared row another user could read or write. This
  holds up unchanged with many concurrent users.
- Explicitly treats "household" as synonymous with "user" for now. If Phase
  8's future multi-owner layer ever needs shared households (e.g. two
  people tracking one litter box together), that requires an additive
  `Household` concept layered above `User` — the same posture ADR 0001
  already established for cat ownership, not a rework of this table.
- Trade-off accepted: if the supply catalog or its rules need to become
  user-editable or admin-configurable later (not currently a requirement),
  that would mean revisiting this decision toward Option B rather than
  extending Option A in place.
