# Phase 2 — Technical Task Breakdown

Prepared by the Tech Lead from BACKLOG.md's Phase 2 stories (P2-1 through
P2-5), following the decisions recorded in:
- `docs/adr/0003-phase-2-reasoning-engine-architecture.md` (on-demand
  computation, dedicated `reasoning` package)
- `docs/adr/0004-phase-2-supply-data-model.md` (fixed `SupplyItemType` enum,
  coded rules, `SupplyTracker` entity)
- `docs/adr/0005-phase-2-cat-outdoor-access-field.md` (nullable
  `Cat.outdoorAccess`, added now to support S1/S3 below)

This doc also includes a **Phase 1 addendum** (Section 0): `CheckupRecord`
(P1-12, P1-13). Those stories were added to the Phase 1 backlog section
after Phase 1 had already shipped — see the placement note on P1-12 in
`BACKLOG.md` — because they're care-data tracking, not reasoning, even
though the need for them was only discovered while building Phase 2 (this
doc previously flagged that gap as "GAP-1"; it's now fully resolved and
removed below). Tracked here, where the gap was found and the fix was
coordinated, rather than reopening the already-handed-off
`docs/phase-1-tech-tasks.md`.

Package layout, all following package-by-feature per
`.claude/skills/spring-boot-conventions`:
- `com.catagenda.cat` — gains `CheckupRecord` (Section 0) and
  `Cat.outdoorAccess` (Section 1), alongside the existing `Condition`/
  `VaccinationRecord`.
- `com.catagenda.supply` — new persistent state (restock tracking, Section
  2). Same shape as `cat`: entity, repository, service, ownership scoped to
  `User` the same way `CatService` scopes to `User`.
- `com.catagenda.reasoning` — no persistent state of its own (per ADR
  0003). Reads from `cat` and `supply`, produces DTOs (Section 3).

Phase 2 ships no UI of its own (confirmed by the Scrum Master's sprint
plan — Sprint 4 covers Phase 2 as an engineering-only sprint feeding Sprint
5's calendar and Sprint 6's cart); the P1-12/P1-13 addendum does ship UI
(it's a Phase 1-style CRUD feature on the cat profile, same as conditions/
vaccinations). This resolves P2-5's open scope question from BACKLOG.md:
**no standalone summary screen in this phase** — P2-5 is validated through
service-level tests now, and through the Phase 3/4 UIs once built.

Status: not started. No implementation/scaffolding has been written yet —
this is the task list only. All originally open items (S1–S4, and GAP-1)
are now resolved and baked into the tasks below. **Zero open items remain
blocking implementation.**

---

## 0. Phase 1 addendum — `CheckupRecord` (P1-12, P1-13)

Mirrors `VaccinationRecord`'s shape and pattern exactly, minus the
type/enum dimension (a checkup isn't typed, just dated).

- **T0.1** — `CheckupRecord` entity in the `cat` package: `id`, `cat`
  (`@ManyToOne` to `Cat`, `cat_id` FK not null), `visitDate` (`LocalDate`,
  not null). Same shape as `VaccinationRecord` minus `vaccineType`/
  `otherDescription` — a checkup has no type dimension.
- **T0.2** — `CheckupRecordRepository extends JpaRepository<CheckupRecord,
  Long>` with `findByCat(Cat cat)`, `findByIdAndCat(Long id, Cat cat)`, and
  `findFirstByCatOrderByVisitDateDesc(Cat cat)` — the last one is the query
  Section 3's reasoning logic depends on directly (P2-1's "anchor to most
  recent checkup record").
- **T0.3** — `CheckupService`: `add`/`update`/`delete`, ownership-scoped
  through `CatService.getOwnedOrThrow` first — same convention
  `VaccinationService` already establishes (a `CheckupRecord` is only ever
  reachable via a cat the requesting user owns). Also
  `getMostRecentForCat(Cat cat) -> Optional<CheckupRecord>` for the
  reasoning layer to consume (Section 3, T3.2).
- **T0.4** — Add/edit/delete checkup flow (P1-12, P1-13): controller + form
  + template, wired into the cat profile view; records listed reverse-
  chronologically; on-brand empty state when none logged; delete requires
  confirmation, consistent with the existing pattern for vaccination
  records (P1-11).
- **T0.5** — Flyway migration: new `checkup_record` table (`cat_id` FK not
  null, `visit_date` not null).
- **T0.6** — `CheckupService` unit tests: ownership scoping, multiple
  records persist independently (visit history, not a single "last
  checkup" field — same requirement shape as P1-10's vaccination history),
  `getMostRecentForCat` returns the correct record after add/edit/delete
  (P1-13's AC: reasoning must reflect whichever record is now most recent
  after a correction), empty-state condition.

## 1. `cat` package extension — outdoor access field (supports S1, S3)

- **T1.1** — Add `outdoorAccess` (`Boolean`, nullable) to the `Cat` entity,
  following the exact same optionality convention as `spayedNeutered`
  (ADR 0005). No default value on the entity — absence means "not
  confirmed," not "false."
- **T1.2** — Flyway migration: `ALTER TABLE cat ADD COLUMN outdoor_access
  BOOLEAN;` (nullable, no default — existing cats end up `NULL`, per ADR
  0005).
- **T1.3** — Extend `CatForm` with the new field; add it to the create/edit
  templates (P1-4/P1-7) and the profile view (P1-6), with an on-brand
  "not set yet" empty-state treatment consistent with how `age`/`sex` are
  already handled when unset (Phase 1 T2.4's pattern).
- **T1.4** — `CatService.applyForm(...)` sets `outdoorAccess` from the form
  alongside the other optional fields; add/extend unit test coverage for
  null-safe handling (a cat with `outdoorAccess == null` behaves like any
  other cat with unset optional fields, no exceptions).

## 2. `supply` package (P2-3, P2-4)

- **T2.1** — `SupplyItemType` enum: `FOOD, LITTER, MEDICATION,
  FLEA_TICK_PREVENTION` (resolved per S1 below — final catalog, not a
  placeholder).
- **T2.2** — `SupplyTracker` entity: `id`, `owner` (`@ManyToOne` to `User`,
  not null — same convention as `Cat.owner`), `itemType` (enum, not null),
  `lastRestockedAt` (nullable `LocalDate` — absent means "never marked
  restocked," resolved as "due now" per S4 below), `createdAt`. DB-level
  unique constraint on `(owner, item_type)`.
- **T2.3** — `SupplyTrackerRepository extends JpaRepository<SupplyTracker,
  Long>` with `findByOwner(User owner)` and
  `findByOwnerAndItemType(User owner, SupplyItemType itemType)`.
- **T2.4** — `SupplyService`: `markRestocked(User owner, SupplyItemType
  itemType, LocalDate restockedOn)` — creates the tracker row if it doesn't
  exist yet, otherwise updates `lastRestockedAt`. This is the service
  method Phase 4's "mark as restocked" UI (P4-2) will call later; Phase 2
  only needs the method to exist and be correct, no controller/view yet.
  Also `getForOwner(User owner)` returning all tracker rows for the
  reasoning layer to read.
- **T2.5** — `SupplyService` unit tests: first restock creates a row,
  second restock on the same item type updates rather than duplicates,
  ownership scoping (user A cannot read/update user B's tracker rows).

**S1 resolved — supply catalog, triggers, and cadence (Tech Lead judgment,
per client direction that this is implementation-level, not a domain
fact):**

| Item type | Trigger | Base cadence | Scaling |
|---|---|---|---|
| `FOOD` | always applicable (every household with ≥1 cat) | 30 days for 1 cat | `max(10, 30 - (catCount-1)*5)` days — more cats drain food faster |
| `LITTER` | always applicable | 21 days for 1 cat | `max(7, 21 - (catCount-1)*4)` days |
| `MEDICATION` | at least one cat has ≥1 flagged `Condition` | 30 days | not scaled by count — one household-level medication restock cadence; `relevantCats` = cats with conditions |
| `FLEA_TICK_PREVENTION` | at least one cat has `outdoorAccess == true` | 30 days for 1 outdoor-access cat | `max(14, 30 - (outdoorCatCount-1)*5)` days, scaled by count of outdoor-access cats only |

These are starting values, deliberately simple and tunable — no product
claims, generic categories only, consistent with P2-3's AC.

## 3. `reasoning` package (P2-1, P2-2, P2-3, P2-4, P2-5)

- **T3.1** — DTOs for reasoning output: `DueCheckup(Cat cat, LocalDate
  dueDate, boolean overdue)`, `DueVaccine(Cat cat, VaccineType vaccineType,
  LocalDate dueDate, boolean overdue)`, `DueSupplyItem(SupplyItemType
  itemType, LocalDate dueDate, boolean overdue, List<Cat> relevantCats)`,
  `DueSoonSummary(List<DueCheckup>, List<DueVaccine>,
  List<DueSupplyItem>)`.

  *(Tech Lead convention call, not a stop-and-wait decision: implement
  these as Java `record`s rather than classes with getters/setters — first
  use of `record` in this project. A `record` is a concise, immutable data
  carrier where the constructor, accessors, `equals`/`hashCode`, and
  `toString` are generated from the field list, which fits DTOs that are
  built once and only read afterward — unlike `CatForm`/`VaccinationForm`,
  which stay plain mutable classes because Thymeleaf's form binding needs
  no-arg construction + setters.)*

- **T3.2** — `CheckupReasoningService.computeNextCheckup(Cat cat) ->
  DueCheckup` (P2-1). **Cadence rule (S2, resolved):**
  - 12-month cadence for an adult cat (`age` known and `< 10`) with no
    flagged conditions, **and** for a cat with `age == null` and no flagged
    conditions (unknown age treated as adult, per client direction —
    consistent with Phase 1 T2.4's note that missing age is a real case to
    handle gracefully).
  - 6-month cadence if `age != null && age >= 10`, **or** the cat has any
    flagged `Condition` — condition overrides age (known or unknown), per
    AAHA/AAFP guidance that chronic conditions call for more frequent
    visits regardless of life stage.

  **Anchor point (updated — replaces the earlier `Cat.createdAt` workaround
  now that P1-12/P1-13 exist):** look up
  `checkupService.getMostRecentForCat(cat)` (T0.3).
  - If a `CheckupRecord` exists: `nextDue = mostRecentVisitDate +
    cadence`. No rolling-forward normalization — like `VaccinationRecord`,
    this is now real historical data, so an old checkup showing as "8
    months overdue" is accurate, useful information (feeds P3-4's overdue
    flagging), not an artifact to smooth over. This brings checkup
    reasoning in line with how vaccine reasoning (T3.3) already treats real
    records — the same pattern applied consistently, now that both have a
    real anchor.
  - If no `CheckupRecord` exists yet: `dueDate = today`, `overdue = true` —
    same "never recorded = due now" posture as S4's supply fallback, per
    client direction, for consistency across the reasoning engine. This
    satisfies P2-1's "no prior checkup recorded still produces a reasonable
    due date" AC.

- **T3.3** — `VaccinationReasoningService.computeNextDueDates(Cat cat) ->
  List<DueVaccine>` (P2-2). **Recommended set and intervals (S3,
  resolved):**

  | Vaccine type | In "recommended set"? | Interval | Notes |
  |---|---|---|---|
  | `RABIES` | always | 1 year | conservative default — actual product duration varies 1–3 years by jurisdiction/product, which this app doesn't track, so default to the shorter/safer interval |
  | `FVRCP` | always | 3 years | standard adult booster interval after the initial series |
  | `FELV` | only if `cat.outdoorAccess == true` (explicit `true` — `null`/`false` excluded, per ADR 0005) | 1 year | matches guidelines that FeLV boosters target cats with ongoing outdoor exposure |
  | `FIV` | never | — | history-only; not routinely recommended, no due-date computed, never flagged as "needed" |
  | `CHLAMYDIA` | always | 1 year | |
  | `BORDETELLA` | always | 1 year | |
  | `OTHER` | never | — | history-only, arbitrary/unspecified vaccine, no due-date logic applies |

  For each vaccine type in the cat's recommended set: if it has never been
  recorded, flag as "needed" (P2-2 AC); if recorded, `nextDue =
  mostRecentDateAdministered + interval`, no rolling-forward normalization
  (same reasoning as T3.2 above).

- **T3.4** — `SupplyReasoningService.computeHouseholdNeeds(User owner) ->
  List<DueSupplyItem>` (P2-3, P2-4). Applies Section 2's catalog/trigger/
  cadence table. **Fallback (S4, resolved):** if no `SupplyTracker` row
  exists yet for an applicable item type, treat it as due immediately
  (`dueDate = today`, `overdue = true`) — no restock history means no
  evidence it's stocked, so the safe default is "needs restocking now."
- **T3.5** — `ReasoningService.getDueSoon(User owner) -> DueSoonSummary`
  (P2-5) — orchestrates T3.2/T3.3 across all of the owner's cats plus T3.4,
  and assembles the combined household view. Single method Phase 3
  (calendar) and Phase 4 (cart) will call. Handles the zero-cats case
  (empty summary, not an error — feeds P3-5's "all clear" empty state).
- **T3.6** — Unit tests for T3.2–T3.5: date-math correctness (given a fixed
  "today" and known records, assert the exact computed due date — inject
  `Clock` rather than calling `LocalDate.now()` directly, so tests are
  deterministic), never-recorded fallback behavior for both checkups
  (T3.2) and supply items (T3.4), `outdoorAccess` null/false/true handling
  for FELV and flea/tick, missing age/condition combinations for checkup
  cadence, most-recent-checkup-record selection after an edit/delete
  (P1-13's AC), and `ReasoningService` assembling all three categories
  correctly for an owner with zero cats.

  *(First use of injecting `Clock` instead of calling `LocalDate.now()`
  directly is worth a short concept note when implemented: it lets tests
  fix "today" to a known value instead of correctness depending on what day
  the test happens to run.)*

## 4. Cross-cutting

- **T4.1** — Flyway migration for `supply_tracker` table (per the Phase 1
  T0.3 convention of versioned migrations over `ddl-auto`), including the
  `(owner_id, item_type)` unique constraint from T2.2. Can be the same
  migration batch as T0.5/T1.2's `cat`-package migrations, or separate
  files — Dev's call, no functional difference.

---

## Summary
24 tasks: 6 in the Phase 1 addendum (`CheckupRecord`, P1-12/P1-13), 4
extending `cat` with `outdoorAccess`, 5 in the new `supply` package, 6 in
the new `reasoning` package (well, 6 numbered T3.x tasks — T3.6 covers all
of T3.2–T3.5's test coverage), and 1 cross-cutting migration note — directly
traceable to P1-12, P1-13, and P2-1 through P2-5. Architectural decisions
from this pass are recorded in `docs/adr/0003-phase-2-reasoning-engine-architecture.md`
(amended with an addendum noting the new `CheckupRecord` data dependency),
`docs/adr/0004-phase-2-supply-data-model.md`, and
`docs/adr/0005-phase-2-cat-outdoor-access-field.md`. All originally open
items (S1–S4) and the gap found while finalizing them (formerly "GAP-1,"
now resolved via P1-12/P1-13) are resolved and baked into the tasks above.
**Zero open items remain.** Ready for direct handoff to Dev.
