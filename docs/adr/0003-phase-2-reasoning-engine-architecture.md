# 0003: Phase 2 Reasoning Engine — Computation Strategy and Component Structure

## Status
Accepted

## Context
Phase 2 (P2-1 through P2-5) turns Phase 1's raw care data (`Cat`, `Condition`,
`VaccinationRecord`) plus new supply-tracking data (see ADR 0004) into
"what's due" output: next checkup date per cat, next due date per vaccine
type, household supply needs, and a combined per-household view (P2-5).
Phase 2 ships no UI of its own — Phase 3 (calendar) and Phase 4 (cart) are
the first consumers of this output, and Phase 5 (Google sync) will later
need to know when a due date *changes*.

Two related questions had to be settled together, because they shape the
same component:

1. **When is "what's due" computed** — at request time from live Phase 1/2
   data, precomputed on a schedule, or some hybrid?
2. **Where does the computation live** — a dedicated component, or spread
   across the existing `cat`/`vaccination` services?

## Options Considered — computation timing

**Option A — On-demand (query-time), no persisted "due" state**
A service reads `Cat`/`Condition`/`VaccinationRecord`/supply data at request
time and computes due dates as a pure function of that data and "today."
Nothing about "what's due" is ever written to the database.
- Pros: the raw Phase 1/2 data stays the single source of truth, so P2-2's
  "due dates recompute automatically whenever a new vaccination record is
  added" is true by construction, not something a write path has to
  remember to do; no staleness or cache-invalidation logic; trivially
  testable as pure functions (given these records + this date, return this
  due-date); no new infrastructure (no background scheduler, no extra
  table).
- Cons: recomputes on every read. At this app's data volume that's
  irrelevant; the cost only becomes real for a cross-user batch query (e.g.
  "everyone overdue" across every household at once), which is not a Phase
  2 requirement — P2-5 is explicitly per-household.

**Option B — Precomputed/cached via a scheduled job**
A `@Scheduled` method (Spring's built-in way to run a method on a timer,
inside the app process — similar to a cron job) periodically recomputes
due-dates for all users and writes them into a persisted "due" table; reads
just query that table.
- Pros: reads are cheap; a persisted before/after state gives Phase 5 a
  ready-made way to detect what changed to sync.
- Cons: directly risks violating P2-2's acceptance criterion — a due-date
  written by a new vaccination record wouldn't be reflected until the next
  scheduled run, unless the job is also triggered on every write (at which
  point it's no longer a simple scheduled job, it's the hybrid in Option C
  anyway). A single timer sweeping every user's data is also a shared,
  monolithic batch job — the kind of thing that becomes a scaling and lock-
  contention concern as user count grows, since it doesn't parallelize
  per-user by default.

**Option C — Hybrid: on-demand reads, write-through cache**
Reads work like Option A, but every write that affects due-state (add
vaccination, add condition, mark an item restocked) also synchronously
recomputes and persists a compact "due" row for just the affected
cat/item — no timer, no full sweep.
- Pros: reads become cheap after the first write; no staleness window,
  because the only thing that changes due-state is the write path that
  already knows what changed; gives Phase 5 a clean "this changed, sync it"
  signal for free.
- Cons: every write path across `cat`, `vaccination`, `condition`, and
  `supply` gains an extra responsibility (recompute-and-store), which is
  more surface area to get wrong for a caching problem this app doesn't
  have yet at its current data volume.

## Options Considered — component structure

**Option A — Dedicated `reasoning` package**
A new `com.catagenda.reasoning` package holds the computation (checkup due-
dates, per-vaccine due-dates, supply due-dates, and the P2-5 combined
summary), reading from `cat` and `supply` repositories. `CatService` and
`VaccinationService` stay pure CRUD/ownership, untouched.
- Pros: matches this project's package-by-feature convention by treating
  *reasoning* as its own feature; keeps existing services single-purpose;
  gives P2-5's combined output one obvious owner instead of it being
  assembled ad hoc by whichever controller needs it first; pure-function
  reasoning logic is testable with plain JUnit + Mockito, no Spring context
  required; gives Phase 3/4/5 one stable read boundary to depend on.
- Cons: reasoning depends on `cat`/`supply` repositories/entities (one-
  directional, not circular) — a real dependency worth naming, though not a
  coupling risk since nothing in `cat`/`supply` depends back on
  `reasoning`.

**Option B — Logic scattered onto existing services**
`CatService.getNextCheckupDueDate(cat)`, `VaccinationService.getNextDueDate
(cat, type)`, a new `SupplyService.getHouseholdNeeds(user)` — callers
assemble the combined view themselves.
- Pros: fewer new files up front.
- Cons: mixes two different concerns (persistence/ownership vs. date-math
  reasoning) into each service; P2-5's "one combined view" has no natural
  owner, so every future caller either re-implements the assembly or one of
  them becomes its accidental owner depending on build order.

**Option C — Computed methods on the entities themselves**
E.g. `cat.getNextCheckupDate()`.
- Pros: reads nicely at the call site.
- Cons: fights the existing precedent that Phase 1 entities are thin,
  mapped state only (see `Cat`, `Condition`, `VaccinationRecord`); bakes
  "today"-dependent, non-deterministic logic into a JPA entity, which
  complicates testing the entity in isolation and will only grow as Phase 6
  layers spay/neuter reminders on top.

## Decision
**Computation timing: Option A (on-demand, no persisted due-state).**
**Component structure: Option A (dedicated `reasoning` package).**

```
Phase 3 / Phase 4 / Phase 5 (future callers)
              │
              │  reasoningService.getDueSoon(user)
              ▼
     com.catagenda.reasoning
     ┌─────────────────────────────────────────────┐
     │ CheckupReasoningService                      │
     │ VaccinationReasoningService                  │
     │ SupplyReasoningService                       │
     │ ReasoningService (orchestrator → DueSoonSummary) │
     └─────────────────────────────────────────────┘
        │ reads (read-only)         │ reads (read-only)
        ▼                            ▼
  com.catagenda.cat            com.catagenda.supply
  (Cat, Condition,              (SupplyTracker — see
   VaccinationRecord,           ADR 0004)
   CheckupRecord — see
   P1-12/P1-13)
```

Every reasoning method takes the owning `User` (or an already owner-scoped
`Cat`/list of cats), the same convention `CatService.listForOwner(owner)`
already establishes — no method computes "for everyone" without a user
boundary. Nothing is written back to the database by a read; the only
persistent state Phase 2 introduces is `SupplyTracker`'s restock timestamps
(ADR 0004), which exist because restock timing is real data, not a
derived/cached value.

## Consequences
- P2-2's "recompute automatically on a new vaccination record" and P2-1's
  "system-computed only, no manual override" are satisfied by construction:
  there is no cached value that could go stale or need reconciling.
- `reasoning` becomes the single integration point Phase 3 (calendar),
  Phase 4 (cart), and Phase 5 (sync) all read from, instead of each
  reaching into `cat`/`vaccination`/`supply` internals separately — this
  keeps those later phases decoupled from Phase 2's internal data shape.
- Reasoning logic is testable as plain unit tests (JUnit + Mockito against
  mocked repositories), consistent with CLAUDE.md's testing convention, with
  no need for `@SpringBootTest`.
- Makes it easy to add new "due" categories later without restructuring:
  Phase 6's spay/neuter reminders are a new reasoning concern layered into
  the same package and the same `DueSoonSummary` shape, not a new
  architectural pattern.
- Defers, rather than forecloses, precomputed/cached due-state. If Phase 5
  later needs cheap change-detection at real scale, the natural next step is
  the write-through hybrid (Option C above) added *inside* `reasoning` —
  additive, not a rework of this decision.
- Each user's reasoning output depends only on that user's own data, with no
  shared mutable state between users — this holds up unchanged if the app
  ever had many concurrent users, since computing user A's due-soon summary
  never touches user B's rows.
- Cost: on-demand computation means every read pays the computation cost.
  Acceptable at this app's scale; the tradeoff to revisit is called out
  above rather than solved preemptively.

**Addendum (post-acceptance, no change to the decision):** while finalizing
Phase 2's task breakdown, a gap was found — P2-1 needed a real "last
checkup" anchor, and none existed. The Product Owner added P1-12/P1-13
(`CheckupRecord`, tracked in the `cat` package, same shape/pattern as
`VaccinationRecord`). `CheckupReasoningService` now reads
`CheckupRecordRepository` the same way it already reads `Condition`- and
`VaccinationRecord`-backed repositories — an additional read-only data
dependency, not a change to this ADR's computation-timing or package-
structure decision. See `docs/phase-2-tech-tasks.md` for the resolved task
breakdown.
