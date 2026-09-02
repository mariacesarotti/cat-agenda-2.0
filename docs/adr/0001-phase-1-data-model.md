# 0001: Phase 1 Data Model — User, Cat, Vaccination, and Condition Records

## Status
Accepted

## Context
Phase 1 needs to persist user accounts, cat profiles, per-cat known conditions,
and per-cat vaccination history. The vaccination requirement is specifically
that a cat can have multiple records of the *same* vaccine type over time
(booster history) — a single "last vaccinated" field per vaccine type is not
sufficient (P1-10).

Separately, CLAUDE.md establishes a constraint that is not a Phase 1 feature
itself but must shape the Phase 1 schema: cats must belong to an owning user
from day one, because Phase 8 (a future multi-owner social layer, out of
scope now) would require reworking the schema if ownership were bolted on
later instead of modeled from the start.

## Options Considered

**Option A — Normalized relational tables**
`User` 1—* `Cat`, `Cat` 1—* `VaccinationRecord`, `Cat` 1—* `Condition`, each
child table FK-linked to its parent, `Cat.user_id` required (`NOT NULL`).
- Pros: matches the true shape of the data (vaccination/condition history is
  a list of events, not a fixed set of fields); adding a new vaccine type
  later requires no schema change; queryable/indexable for Phase 2's
  reasoning engine (e.g. "all cats overdue for rabies"); referential
  integrity enforced by the database; standard JPA `@OneToMany`/`@ManyToOne`
  mapping.
- Cons: more entities/mappings to learn up front than a single-table design.

**Option B — JSON columns on `Cat` for vaccinations/conditions**
`Cat` gets `vaccinations_json` / `conditions_json` (PostgreSQL `JSONB`)
instead of child tables.
- Pros: fewer tables initially.
- Cons: no relational integrity on entries inside the JSON; querying "what's
  overdue across my household" (needed in Phase 2) means loading and parsing
  JSON in application code instead of letting the database filter; editing
  or deleting a single record (P1-11) means read-modify-write on the whole
  blob instead of a targeted row operation.

**Option C — Normalized tables plus a denormalized "last vaccinated" cache
column on `Cat`**
Same as Option A, plus convenience columns like `last_rabies_date` on `Cat`.
- Pros: could save a join on a hot-path read.
- Cons: directly conflicts with P1-10's requirement that no single "last
  vaccinated" field is sufficient if it risks becoming a de facto source of
  truth; introduces a cache-invalidation problem (every vaccination write
  must remember to update the cache); premature optimization at this scale.

## Decision
Option A: normalized relational tables.

```
User (id, email UNIQUE, password_hash, created_at)
   1
   │ owns
   *
Cat (id, user_id FK NOT NULL, name, age, sex, spayed_neutered, created_at)
   1                                   1
   │                                   │
   *                                   *
VaccinationRecord                  Condition
(id, cat_id FK, vaccine_type,      (id, cat_id FK, name, notes)
 date_administered)
```

`User` → `Cat` is a required foreign key (every cat has exactly one owning
user, no orphan/global cats). `Cat` → `VaccinationRecord` and `Cat` →
`Condition` are both one-to-many, supporting repeated entries (e.g. multiple
rabies boosters over time) with no cap and no schema change needed to add a
new vaccine type or condition name.

## Consequences
- Makes it easy to later query and reason over care data set-wise (Phase 2
  depends on this: due-date computation per vaccine type, per-cat and
  per-household aggregation).
- Makes ownership scoping straightforward and consistent: every cat, and
  everything hanging off a cat, is reachable only through `user_id`, so
  service-layer code can enforce "a user can only see their own cats" with a
  single, consistent pattern rather than special-casing it per feature.
- Leaves room for Phase 8 (future, out of scope) to extend ownership without
  a rework: today ownership is 1:1 (one `user_id` per cat). A future
  multi-owner model (e.g. shared households, following) would be *additive*
  — most likely a join table such as `CatOwnership(cat_id, user_id, role)`
  layered on top, or a separate visibility/sharing concept — rather than a
  migration that touches every existing row and every existing query, which
  is what would be required if cats were modeled as a flat/global list today.
- Costs more upfront entities/mappings to write and test than a single-table
  design, and requires learning JPA relationship annotations
  (`@OneToMany`/`@ManyToOne`) early, but this is core, reusable Spring Data
  knowledge rather than one-off complexity.
