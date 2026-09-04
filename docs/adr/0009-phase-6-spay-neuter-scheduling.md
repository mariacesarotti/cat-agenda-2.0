# 0009: Phase 6 Spay/Neuter Scheduling — Data Model, Calendar Representation, and Sync Boundary

## Status
Accepted

## Context
Phase 6 (P6-1 through P6-4) adds spay/neuter as a tracked, planned procedure
rather than just the existing `spayedNeutered` status flag on `Cat` (ADR
0001). A user can flag intent to get a cat spayed/neutered (P6-1), schedule
(and edit/remove) an appointment date once intent is flagged (P6-2), mark it
completed — which flips `Cat.spayedNeutered` and clears the flag/reminder
(P6-3) — and, critically, a cat with intent flagged but *no* appointment yet
must still show up in "what's due" (P2-5) and on the calendar as an
actionable item, with a generic nudge that upgrades to a real due date once
scheduled (P6-4).

This forces three related forks, bundled into one ADR the same way ADR 0007
and ADR 0008 each bundled several related sub-decisions:

1. **Where does this state live?** New fields on `Cat`, or a new decoupled
   entity — and, first, whether this should even be modeled as append-only
   history the way `VaccinationRecord`/`CheckupRecord` are.
2. **How does a cat with intent-but-no-date fit `CalendarEvent`?**
   `CalendarEvent` (ADR 0006) is a stateless view-model with a **non-nullable**
   `LocalDate dueDate`, addressed by a natural key (no database id), consumed
   directly by `CalendarService`'s sort/month-filter logic. P6-4 needs a state
   with no real date at all.
3. **Does the "no date yet" state ever reach Google Calendar sync?**
   `GoogleSyncService` (ADR 0008) currently pushes every `CalendarEvent` it's
   given, diffing on `dueDate` to decide create/update. P6-2's AC says a
   scheduled appointment "feeds into... Phase 5 sync like other care events,"
   but says nothing about the unscheduled nudge.

These three were presented to the client as alternatives with a
recommendation per CLAUDE.md's decision-making protocol; the client's
explicit picks (matching the recommendation in each case) are recorded below.

## Options Considered

### Decision 1 — where spay/neuter scheduling state lives (P6-1, P6-2, P6-3)

**Ruled out before the real options: append-only history, mirroring
`VaccinationRecord`.** Worth naming and rejecting explicitly, the same way
ADR 0007 ruled out reusing `VaccineType.OTHER`'s pattern for `SupplyTracker`.
`VaccinationRecord` needs multiple rows per cat because booster history is
genuinely cumulative (P1-10). Nothing in P6-1 through P6-4 asks for that —
P6-3's AC is that completion simply "clears the intent flag and any related
upcoming reminder," not "closes one historical record while starting a new
one." This is a **current-state singleton per cat**, structurally like
`SupplyTracker` (one row per key, mutated in place — ADR 0004), not like
`VaccinationRecord`.

**Option A — Two new fields directly on `Cat`**
`Cat` gains `spayNeuterIntentFlaggedAt` (nullable timestamp) and
`spayNeuterAppointmentDate` (nullable `LocalDate`), the same shape as the
existing `spayedNeutered`/`outdoorAccess` columns.
- Pros: no new table/entity/repository/service. Ownership scoping is
  automatic (already a `Cat` column). Cheapest possible change for two
  fields.
- Cons: unlike `spayedNeutered`/`outdoorAccess` (independent status flags,
  no lifecycle), this is a real 3-state workflow with cross-field invariants
  (an appointment can only be set if intent is flagged; completion must
  clear both fields *and* flip a third field). That business logic would
  have to live in `CatService`, the package ADR 0003 describes as staying
  "pure CRUD/ownership, untouched." Grows `Cat`'s responsibilities with
  workflow logic it wasn't designed to hold.

**Option B — New decoupled entity, `SpayNeuterPlan`, one row per cat** *(chosen)*
A new `com.catagenda.spayneuter` package with `SpayNeuterPlan(id, cat_id FK
UNIQUE, intent_flagged_at, appointment_date nullable, created_at)`. Queried
directly by cat (no bidirectional `@OneToMany` added to `Cat`), the same way
`CheckupReasoningService` reads `CheckupService.getMostRecentForCat(cat)`
instead of traversing `Cat`'s lazy collections.
- Pros: keeps `Cat` as pure profile/identity data, consistent with its shape
  since ADR 0001. Matches the precedent set twice already — genuinely new
  state gets its own owned entity, decoupled from the thing it's about
  (`CartItem` per ADR 0007; `SyncedEvent`/`GoogleCalendarConnection` per ADR
  0008). The 3-state workflow (intent gates appointment; completion clears
  both and flips `Cat.spayedNeutered`) gets one obvious owner — a
  `SpayNeuterService` — instead of bleeding into `CatService`. Leaves room to
  grow (vet clinic, cost, notes) without touching `Cat` again.
- Cons: one more entity/repository/service for what is, today, two fields'
  worth of state — the same honest ceremony cost ADR 0007 accepted for
  `CartItem`.

**Multi-user/Phase 8 reasoning:** both options are already transitively
owner-scoped via `Cat.owner` (ADR 0001) — this fork isn't really about
concurrency or user count. If Phase 8 later adds co-owned cats (a
`CatOwnership` join table, per ADR 0001's own consequences), a
`SpayNeuterPlan` row keyed by `cat_id` doesn't need to change shape —
visibility is a layer on top of "which cat," not something this table
encodes, the same way `SupplyTracker`/`CartItem` didn't need to change for
that reasoning either.

### Decision 2 — representing "intent flagged, no date yet" in the calendar (P6-4)

There's an existing precedent for "no real date yet" in this codebase:
`CheckupReasoningService.computeNextCheckup` returns `dueDate = today`,
`overdue = true` when no `CheckupRecord` exists — a **sentinel value**
standing in for "there is no real date." The question is whether reusing
that convention, unmarked, is safe here.

**Option 1 — Reuse the sentinel-date pattern, one `SPAY_NEUTER` event type**
A single new `CalendarEventType.SPAY_NEUTER` case; `dueDate = today` when
unscheduled, the real appointment date once scheduled.
- Pros: zero changes to `CalendarEvent`'s shape or `CalendarService`'s
  sort/filter — a pure additive mapping branch.
- Cons: `dueDate` becomes ambiguous — "a real date" and "a placeholder" are
  indistinguishable by type, only by an implicit convention. Concretely
  dangerous downstream (see Decision 3): nothing stops a future consumer
  from treating the sentinel as real.

**Option 2 — Two `CalendarEventType` cases: `SPAY_NEUTER_PENDING` (nudge) and
`SPAY_NEUTER_SCHEDULED` (real date)** *(chosen)*
The type itself is the signal, not a value convention. `SPAY_NEUTER_PENDING`
carries a sentinel `dueDate` used only for internal ordering, never treated
as real by any consumer; `SPAY_NEUTER_SCHEDULED` carries the real appointment
date.
- Pros: makes "is this date real" explicit and type-checkable everywhere it
  matters (template rendering, sync exclusion), instead of an implicit
  convention someone has to remember. Not a new idiom — `CHECKUP`/`VACCINE`
  already share one record shape for exactly this reason (different meaning,
  same fields); this is one more discriminator case, matching `CalendarEvent`'s
  own stated reasoning for choosing "one record, nullable fields" over a
  subtype per type when the differences are small.
- Cons: two mapping branches instead of one; `SyncedEventKey`'s natural key
  (which includes `eventType`) treats these as two distinct keys — turns out
  to help, not hurt, the sync-side reconciliation (see Consequences).

**Option 3 — Add a field to `CalendarEvent` itself (e.g. `boolean
hasRealDueDate`)**
- Cons: modifies the already-shipped `CalendarEvent` contract (ADR 0006) for
  a concern only one event type has; `CHECKUP`/`VACCINE` events would carry a
  field that's always meaningless for them — the same "push a concern into a
  shared type that doesn't need it" pattern ADR 0006 rejected when it ruled
  out template-level type-branching.

**Multi-user reasoning:** unchanged from ADR 0006's own — this fork is about
event-type surface area and consumer count (Phase 3's UI, Phase 5's sync),
not concurrency; nothing here changes with more users.

### Decision 3 — Google sync boundary for the unscheduled state (P6-2)

`GoogleSyncService.syncForOwner` diffs `stored.getLastSyncedDueDate().equals
(event.dueDate())` to decide create/update, for every `CalendarEvent` it's
given, on every run — including the `@Scheduled` backstop job that runs with
no user present (ADR 0008, Decision 3). If a sentinel "today" (recomputed
fresh on every read, by design — see `CheckupReasoningService`) were left in
the syncable set, every sync run would see a different `dueDate` than the
last one and push a real, spurious edit to the user's actual Google
Calendar, indefinitely, for an event that was never supposed to have a date.
This is a concrete bug risk, not a hypothetical one, and it's the direct
reason Decision 2 and Decision 3 have to be resolved together.

**Option A — Filter the exclusion at the `GoogleSyncService` boundary** *(chosen)*
`syncForOwner` excludes `eventType == SPAY_NEUTER_PENDING` from the events it
diffs/pushes, before any comparison happens. `CalendarService`/`CalendarEvent`
stay completely unaware sync exists.
- Pros: the exact boundary discipline ADR 0006/0008 already established
  twice — `CalendarService` stays the one "what's display-worthy" boundary
  Phase 3 fully depends on; the consumer that actually cares (sync) is where
  the filtering lives. Mirrors how `CalendarController`, not `CalendarService`,
  already layers in sync-status decoration.
- Cons: none of real weight.

**Option B — Push a "syncable" signal into `CalendarEvent` itself**
- Cons: speculative — no other consumer needs this today; same "modify the
  shared, already-accepted contract for one consumer's need" cost as
  Decision 2's Option 3.

**Multi-user reasoning:** unchanged from ADR 0008's own — `GoogleSyncService`
already loops per-owner with per-event try/catch; excluding one more event
type from one owner's diff set doesn't change that story at any user count.

## Decision
1. **State ownership (Decision 1): Option B** — a new `SpayNeuterPlan`
   entity in a new `com.catagenda.spayneuter` package, one row per cat
   (`cat_id UNIQUE`), holding `intentFlaggedAt` and a nullable
   `appointmentDate`. Not modeled as append-only history.
2. **Calendar representation (Decision 2): Option 2** — two new
   `CalendarEventType` cases, `SPAY_NEUTER_PENDING` (no real date, sentinel
   `dueDate` for internal ordering only) and `SPAY_NEUTER_SCHEDULED` (real
   `dueDate` = the appointment date). `CalendarEvent`'s own shape is
   unchanged.
3. **Sync boundary (Decision 3): Option A** — `GoogleSyncService` filters out
   `SPAY_NEUTER_PENDING` events before diffing; only `SPAY_NEUTER_SCHEDULED`
   ever reaches Google Calendar.
4. **Mutation mechanics (P6-2, P6-3) — not an independent fork, confirmed
   rather than reopened:** editing/removing an appointment date and marking
   completion are simple, single-transaction mutations on the entities from
   Decision 1, with no new shape required:
   - **P6-2 edit/remove appointment date:** mutates `SpayNeuterPlan
     .appointmentDate` in place — a new value, or `null`.
   - **P6-3 mark completed:** sets `Cat.spayedNeutered = true` (a small,
     additive method on the existing `CatService`/`Cat`, not a rework) and
     deletes the `SpayNeuterPlan` row, in one transaction.
   - **Semantics clarification** (flagged during analysis, folded in here
     rather than left implicit): P6-1's "unset intent" and P6-2's "remove
     the scheduled date" are **not** the same operation. **Unsetting intent**
     (P6-1) deletes the entire `SpayNeuterPlan` row — intent and any
     appointment date together — since P6-2 requires intent to already be
     flagged before an appointment can exist, so an appointment should never
     outlive its intent flag. **Removing the appointment date** (P6-2) only
     clears `appointmentDate`, leaving the row (and intent) in place, which
     falls the cat back to the `SPAY_NEUTER_PENDING` calendar state rather
     than removing it from "what's due" entirely.

```
User (existing)
   1
   │ owns
   *
Cat (existing, ADR 0001)                  SpayNeuterPlan (new)
 - spayedNeutered ◄── flipped by P6-3      (id, cat_id FK UNIQUE,
   1                                        intent_flagged_at,
   │ has at most one                       appointment_date nullable,
   0..1                                     created_at)
   └───────────────────────────────────────────┘
```

```
com.catagenda.reasoning (existing, ADR 0003)
   SpayNeuterReasoningService (new) ── reads SpayNeuterPlan by cat
        │ produces DueSpayNeuter(cat, dueDate, scheduled, overdue)
        ▼
   DueSoonSummary (+ dueSpayNeuters — additive, per ADR 0003's own
                    consequence that new "due" categories are additive here)

com.catagenda.calendar (existing, ADR 0006)
   CalendarService.toEvents()
     scheduled = false ──► CalendarEventType.SPAY_NEUTER_PENDING  (sentinel dueDate)
     scheduled = true  ──► CalendarEventType.SPAY_NEUTER_SCHEDULED (real dueDate)
        │
        ▼
   List<CalendarEvent> ──► Phase 3 calendar UI (both types rendered, differently)

com.catagenda.googlesync (existing, ADR 0008)
   GoogleSyncService.syncForOwner()
     filters OUT SPAY_NEUTER_PENDING   (no real date — never pushed to Google)
     syncs   SPAY_NEUTER_SCHEDULED    (same reconciliation loop as CHECKUP/VACCINE,
                                        no new sync-diff code needed)
```

## Consequences
- `Cat` stays pure profile/identity data — Phase 6 does not grow its column
  count or pull scheduling-workflow logic into `CatService`. The 3-state
  spay/neuter workflow gets one owner, a new `SpayNeuterService`, consistent
  with how `CartItemService`/`GoogleConnectionService` each own their own
  entity's lifecycle.
- `CalendarEvent`, `CalendarService`'s sort/filter, and `SyncedEventKey` are
  all unchanged — Phase 6 is purely additive at every one of those
  boundaries, exactly the guarantee ADR 0006 made for future event types and
  ADR 0008 made for future sync additions ("Adding Phase 6's spay/neuter
  reminder to sync is additive to `SyncedEvent`... no schema change").
- `DueSoonSummary` gains a fourth field (`dueSpayNeuters`), which changes its
  constructor signature — every existing call site that constructs it
  directly (tests included) needs updating. This is expected, not a design
  smell: ADR 0003 explicitly anticipated this exact growth ("Phase 6's
  spay/neuter reminders are a new reasoning concern layered into the same
  package and the same `DueSoonSummary` shape, not a new architectural
  pattern").
- The `SPAY_NEUTER_PENDING`→`SPAY_NEUTER_SCHEDULED` transition changes an
  event's natural key (since `eventType` is part of it). Because the pending
  state was never synced, there's no stale `SyncedEvent` row to clean up —
  the very next sync run sees a brand-new key with no matching row and
  creates it on Google, exactly like any other new event. The existing
  reconciliation loop (ADR 0008, Decision 4) handles this transition with no
  new sync-diff code, only the exclusion filter.
- Because a cat can be in the `SPAY_NEUTER_PENDING`/`SPAY_NEUTER_SCHEDULED`
  state at most once (one `SpayNeuterPlan` row per cat), the calendar
  event-detail route for this concern needs no type-disambiguating path
  segment the way `VACCINE`'s route needs `vaccineType` — `catId` alone is
  unambiguous.
- `EventPayloadBuilder.buildTitle`'s `switch` over `CalendarEventType` (no
  `default` branch) is exhaustive by construction — adding two new enum
  cases means the compiler will refuse to build until both are handled, even
  though `SPAY_NEUTER_PENDING` is filtered out before this method is ever
  called on it. This is caught at compile time, not discovered later as a
  missing-case bug at runtime.
- Trade-off accepted: the sentinel `dueDate` on `SPAY_NEUTER_PENDING` events
  is recomputed fresh on every read (same "never tracked = due now"
  convention `CheckupReasoningService` already uses) and must never be
  compared for equality/staleness by any future consumer the way
  `SPAY_NEUTER_SCHEDULED`'s real date legitimately can be — this is now
  type-enforced (Decision 2's Option 2), not just documented convention.
