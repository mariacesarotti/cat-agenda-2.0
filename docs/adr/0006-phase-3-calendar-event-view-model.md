# 0006: Phase 3 Calendar Event View-Model and Mapping Layer

## Status
Accepted

## Context
Phase 3 (P3-1 through P3-5) builds a browsable calendar of upcoming care
events on top of Phase 2's `ReasoningService.getDueSoon(user)` (ADR 0003).
That method returns a `DueSoonSummary` made of three separately-shaped
lists — `List<DueCheckup>`, `List<DueVaccine>`, `List<DueSupplyItem>` — each
carrying different fields (`DueVaccine` has a `vaccineType`, `DueSupplyItem`
has `relevantCats`, etc.). Per P3-1's acceptance criteria, the calendar
itself only surfaces checkups and vaccines (supply items stay Phase 4's
"Supply Chain" cart), but that's still two heterogeneous types today, and a
third (spay/neuter) is already planned for Phase 6.

A calendar view needs one merged, sorted, filterable timeline — by month and
by cat (P3-3), with overdue items flagged (P3-4) and an empty state when
nothing qualifies (P3-5). That merge has to happen somewhere, and where it
happens is a real architectural fork: it determines whether the Thymeleaf
template ends up doing type-branching logic, and it determines what shape
Phase 5 (Google Calendar sync) inherits when it becomes the next consumer of
"the calendar's event list."

## Options Considered

**Option A — Template consumes `DueCheckup`/`DueVaccine` directly**
Controller passes `DueSoonSummary` (or its raw lists) straight to
Thymeleaf; the template loops over each list separately or merges them with
`th:switch`/`instanceof`-style checks, pulling cat name/due date/overdue out
of each type differently.
- Pros: no new Java type, fewest new files.
- Cons: pushes merge/sort/type-branching logic into the template, which
  isn't reachable by JUnit the way a service method is — violates the "every
  new feature ships with at least basic unit tests" convention for the part
  of this feature that's actually nontrivial (the merge). Every future event
  type (Phase 6 spay/neuter) means editing template branching directly.
  Couples the view layer to reasoning's internal DTO shapes.

**Option B — New `CalendarEvent` view-model + mapping layer**
A new `com.catagenda.calendar` package introduces a `CalendarEvent` record
(cat, an event-type discriminator, due date, overdue flag) and a
`CalendarService` that calls `ReasoningService.getDueSoon(user)` and maps
the result into one `List<CalendarEvent>`, already merged, sorted, and
filterable. The template renders one homogeneous list.
- Pros: merge/sort/filter logic lives in a plain Java service — unit
  testable like the rest of `reasoning`, no Spring context required.
  Template stays simple. New event types are a new enum case + mapping
  branch, not new template logic. Gives Phase 5 exactly the "one clean
  type" it will want to iterate and push to Google Calendar, instead of
  Phase 5 re-deriving the merge itself.
- Cons: one new package/type for a feature that today only merges two
  source types.

**Option C — Push the unified shape back into `reasoning` itself**
Replace `DueCheckup`/`DueVaccine` at the source with a single generic event
type, so `ReasoningService.getDueSoon()` returns the unified shape directly.
- Cons: `DueVaccine`'s `vaccineType` and `DueSupplyItem`'s `relevantCats`
  are genuinely different per domain — Phase 4's cart needs
  `DueSupplyItem`'s shape, not a generic calendar event. This would bend
  Phase 2's already-accepted output contract (ADR 0003) to fit Phase 3's
  display needs, which is exactly the "consumers reach into and reshape the
  boundary" pattern ADR 0003 was written to avoid.

**Multi-user/scale reasoning:** this fork isn't really about concurrency or
data volume — ADR 0003 already settled that (per-user, on-demand, no shared
state, cheap at this scale) and nothing here changes it. What does grow over
time is the number of event types (Phase 6 adds one; more may follow) and
the number of downstream consumers of "the calendar" (Phase 3's UI now,
Phase 5's sync next). Option B is the one that stays flat as both of those
grow; Option A gets messier per new type/consumer, and Option C forces
rework of an already-shipped boundary contract instead.

## Decision
**Option B: a new `com.catagenda.calendar` package** holding:
- `CalendarEvent` — a record with a common shape (`cat`, `eventType`,
  `dueDate`, `overdue`) for anything the calendar displays.
- `CalendarEventType` — an enum discriminator (`CHECKUP`, `VACCINE` today;
  extensible for Phase 6's spay/neuter reminder).
- `CalendarService` — maps `ReasoningService.getDueSoon(user)`'s output into
  `List<CalendarEvent>`, then merges, sorts by due date, and filters (by
  cat, by month) for the controller to render.
- `CalendarController` — thin, delegates to `CalendarService`.

```
Phase 3 (this ADR)                         Phase 5 (future)
CalendarController → CalendarService       GoogleSyncService (future)
                          │                          │
                          │ getDueSoon(user)          │ List<CalendarEvent>
                          ▼                          ▲
                  com.catagenda.reasoning ────────────┘
                  ReasoningService.getDueSoon(user)
                          │ reads (read-only)
                          ▼
              com.catagenda.cat / com.catagenda.supply
```

`reasoning` is untouched — it keeps producing its existing, already-ADR'd
DTOs. `calendar` is the mapping/shaping layer that owns turning those into
one display-ready timeline, and it's the layer Phase 5 plugs into later
rather than reasoning itself.

## Consequences
- Merge/sort/filter logic is a plain Java service, unit-testable with JUnit
  + Mockito against a mocked `ReasoningService`, no `@SpringBootTest` needed
  — consistent with how `reasoning` itself is tested (ADR 0003).
- Adding Phase 6's spay/neuter reminder to the calendar is a new
  `CalendarEventType` case and a new mapping branch in `CalendarService`,
  not a template rewrite.
- Phase 5 (Google sync) gets a ready-made `List<CalendarEvent>` to iterate
  and push to Google Calendar, instead of needing to re-derive the
  checkup/vaccine merge itself — the `calendar` package becomes the second
  stable read boundary in this app (after `reasoning`), and Phase 5 depends
  on it the same way Phase 3/4 depend on `reasoning`.
- `reasoning`'s output contract (ADR 0003) is not reshaped to fit a
  presentation need — it stays the stable boundary for both Phase 3 and
  Phase 4, which keeps those two phases decoupled from each other even
  though they share the same upstream data source.
- Cost: one additional package/mapping step for what is, today, only two
  source types to merge — accepted because the alternative (Option A) pays
  that cost back with interest at Phase 6 and Phase 5, in template logic
  that's harder to test and harder to change.
- Because `CalendarEvent` is computed on demand and never persisted (no
  surrogate database ID), addressing a single event for the detail view
  (P3-2) needs a natural/composite key derived from the data itself (e.g.
  cat + event type, or cat + vaccine type) rather than a database ID — this
  is a routing detail, not a change to this decision; it's worked out in
  `docs/phase-3-tech-tasks.md`.
