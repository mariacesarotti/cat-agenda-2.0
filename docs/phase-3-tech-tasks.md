# Phase 3 — Technical Task Breakdown

Prepared by the Tech Lead from BACKLOG.md's Phase 3 stories (P3-1 through
P3-5), following the decision recorded in
`docs/adr/0006-phase-3-calendar-event-view-model.md` (new `CalendarEvent`
view-model + `com.catagenda.calendar` mapping layer, built on top of
`ReasoningService.getDueSoon(user)` per ADR 0003).

Phase 3 adds no new persisted state and no new integration boundary — it is
a read/browse UI over Phase 2's existing reasoning output (see ADR 0006's
Context). Package layout, following package-by-feature per
`.claude/skills/spring-boot-conventions`:
- `com.catagenda.calendar` — new: `CalendarEvent`, `CalendarEventType`,
  `CalendarService`, `CalendarController`. Reads (read-only) from
  `com.catagenda.reasoning` and `com.catagenda.cat` (for the cat-filter
  dropdown and ownership checks). Nothing in `reasoning` or `cat` depends
  back on `calendar`.
- `src/main/resources/templates/calendar/` — new: `view.html`,
  `event-detail.html`.

Status: not started. Task list only, no implementation yet.

---

## 0. Design notes (context for the tasks below, not tasks themselves)

- **Scope:** per P3-1's acceptance criteria, the calendar shows checkups and
  vaccines only — `DueSupplyItem` is intentionally excluded (that's Phase
  4's "Supply Chain" cart). `CalendarService`'s mapping step only reads
  `dueSoonSummary.dueCheckups()` and `dueSoonSummary.dueVaccines()`.
- **View shape:** a month-grid calendar (events placed on the day cell
  matching their `dueDate`), not a flat chronological list — this follows
  directly from P3-1's own wording ("placed on their computed due dates")
  and matches what "calendar" implies. Resolved as a Tech Lead judgment
  call, not flagged, since the backlog language already settles it and
  there's no real coupling/cost-of-change tradeoff between "grid" and
  "list" worth a stop-and-wait.
- **Event addressing (P3-2):** `CalendarEvent` is computed on demand and
  never persisted (per ADR 0003/0006), so it has no database ID to put in a
  URL. The event-detail route instead uses a natural composite key already
  present in the data itself: cat ID + event type, plus vaccine type when
  the event type is `VACCINE` (a cat has at most one due checkup, but one
  due-date per vaccine type, so `catId` alone is ambiguous for vaccines).
  This is resolved directly below (T2.5, T3.2), not treated as an open
  architectural question — it's a routing detail flowing from the ADR 0006
  decision, not a new fork.
- **Filtering scope:** month-by-month navigation (prev/next) plus an
  optional cat filter, not an arbitrary start/end date-range picker —
  nothing in P3-1 through P3-5 asks for arbitrary ranges, and month
  navigation is what "defaults to current month" (P3-1) implies you can
  move away from.

---

## 1. `calendar` package — core types

- **T1.1** — `CalendarEventType` enum: `CHECKUP`, `VACCINE`. Extensible for
  Phase 6's spay/neuter reminder (a future `SPAY_NEUTER` case), per ADR
  0006's consequence that new event types are additive here.
- **T1.2** — `CalendarEvent` record: `Cat cat`, `CalendarEventType
  eventType`, `VaccineType vaccineType` (nullable — only populated when
  `eventType == VACCINE`), `LocalDate dueDate`, `boolean overdue`. A single
  record with a nullable type-specific field (rather than a subtype per
  event type) keeps the shape simple for a template that just needs to
  render one homogeneous list — the same "record for a DTO built once and
  only read afterward" reasoning already established for `DueCheckup`/
  `DueVaccine` in Phase 2.

## 2. `CalendarService` — mapping, merge, sort, filter (P3-1, P3-3, P3-4)

- **T2.1** — `toEvents(DueSoonSummary summary) -> List<CalendarEvent>`:
  maps each `DueCheckup` to a `CHECKUP` event and each `DueVaccine` to a
  `VACCINE` event (carrying its `vaccineType`); does not touch
  `dueSupplyItems`.
- **T2.2** — `getEventsForOwner(User owner) -> List<CalendarEvent>`: calls
  `reasoningService.getDueSoon(owner)`, maps via T2.1, sorts by `dueDate`
  (then by cat name for a stable, deterministic order when due dates tie).
- **T2.3** — Filter helpers: `filterByCat(events, Long catIdOrNull)` (null
  = "all cats," per P3-3's AC) and `filterByMonth(events, YearMonth)`.
  Small, composable, independently testable methods rather than one large
  conditional block.
- **T2.4** — `getCalendarView(User owner, YearMonth month, Long
  catIdOrNull) -> List<CalendarEvent>`: orchestrates T2.2 + T2.3 for the
  controller — the single method `CalendarController`'s month-view action
  calls.
- **T2.5** — `getEventDetail(User owner, Long catId, CalendarEventType
  type, VaccineType vaccineTypeOrNull) -> Optional<CalendarEvent>`: calls
  `catService.getOwnedOrThrow(catId, owner)` first (ownership enforced and
  a 404 raised before any lookup happens, consistent with how `cat`'s
  existing controllers scope everything through `CatService` — a user can
  never probe for another owner's events even indirectly), then calls T2.2
  and matches on the composite key. Returns empty if no matching due event
  exists (e.g. a vaccine type not currently due for that cat).
- **T2.6** — Unit tests (JUnit + Mockito, mocked `ReasoningService`/
  `CatService`, no `@SpringBootTest` — consistent with how `reasoning` is
  tested per ADR 0003): mapping includes checkups + vaccines and never
  supply items; sort order (by date, tie-broken by cat name); cat filter
  (specific cat vs. "all"); month filter; both filters combined; zero
  cats/zero due items yields an empty list (feeds P3-5); `getEventDetail`
  returns the correct event, returns empty for a vaccine type not due, and
  throws/404s for a cat the requesting user doesn't own.

## 3. `CalendarController` (P3-1, P3-2, P3-3)

- **T3.1** — `GET /calendar?catId=&year=&month=`: resolves the owner via
  `CurrentUserProvider` (same convention as `CatController`), defaults
  `year`/`month` to the current month when absent (inject `Clock`, the same
  convention `reasoning` already established for testable "today"), calls
  `CalendarService.getCalendarView(...)`, adds the owner's cat list (via
  `CatService.listForOwner`) to the model for the cat-filter dropdown, adds
  prev/next-month links, and renders `calendar/view.html`. No separate
  branch needed for the empty case (P3-5) — the template conditionally
  renders the empty state when the event list is empty.
- **T3.2** — Event detail routes: `GET /calendar/events/checkup/{catId}`
  and `GET /calendar/events/vaccine/{catId}/{vaccineType}`. Both resolve
  the owner, call `CalendarService.getEventDetail(...)`, return a 404 (via
  the existing `NotFoundException` convention) if the `Optional` is empty,
  and otherwise render `calendar/event-detail.html`, which links to
  `/cats/{catId}` per P3-2's AC.
- **T3.3** — Add a "Calendar" link to `templates/fragments/nav.html`
  alongside the existing brand link, since P3-1 makes this a primary
  browsable view, not a sub-page reached only from elsewhere.

## 4. Templates (P3-1, P3-2, P3-4, P3-5)

- **T4.1** — `calendar/view.html`: month grid (day cells for the selected
  `YearMonth`), events rendered in their due-date's cell (cat name + short
  event-type label, e.g. "Mochi — Rabies"), each event linking to its
  detail route (T3.2). Cat-filter dropdown defaulting to "all cats" (P3-3),
  with an obvious, one-click way back to "all cats" per the AC. Prev/next
  month controls. Overdue events visually and textually distinguished
  (P3-4) — see T4.4 on not relying on color alone.
- **T4.2** — `calendar/event-detail.html`: cat name, event type, due date,
  overdue flag/label, and a link to the cat's profile (`/cats/{catId}`),
  per P3-2's AC.
- **T4.3** — Empty-state section within `view.html` for P3-5: on-brand "all
  clear" copy shown when the filtered/current-month event list is empty.
  Distinct from Phase 1's "no cats yet" empty state — this one means "you
  have cats, nothing of theirs is due right now."
- **T4.4** — CSS additions (`static/css/style.css`) for the month-grid
  layout and overdue styling. Per P3-4's AC that clarity takes priority
  over the joke, overdue styling must not rely on color alone — pair any
  color/visual treatment with a text label (e.g. "Overdue") so it's legible
  regardless of color perception, not just a styling nicety.

## 5. Cross-cutting

- **T5.1** — No Flyway migration in this phase — `CalendarEvent` is
  computed on demand, never persisted (per ADR 0006). Worth calling out
  explicitly since every prior phase's task doc has included a migration
  task and Dev may otherwise expect one by pattern; Phase 3 is the first
  phase since Phase 1 with zero schema changes.

---

## Open items — need PO/client confirmation before this is fully "done"

- **OI-1 (PO):** Exact on-brand microcopy is not written yet — the P3-5
  empty-state message, the P3-4 overdue label text (must stay legible/clear
  per its AC even while witty), and the short per-event-type display labels
  used on the month grid (e.g. how a vaccine's name reads next to "due").
  Dev will build against placeholder copy; PO should confirm/replace final
  strings before this phase ships, same pattern as Phase 1's tone
  requirements (e.g. P1-8's note).

No other open items — the routing/addressing scheme (T2.5/T3.2) and the
month-vs-arbitrary-range filtering scope are resolved above as Tech Lead
judgment calls, not flagged, since the backlog wording already settles
their shape and neither carries a real coupling/multi-user tradeoff that
would call for a stop-and-wait per CLAUDE.md's decision protocol.

---

## Summary
16 tasks: 2 in the new `calendar` package's core types, 6 in
`CalendarService` (including its test coverage), 3 in `CalendarController`,
4 in templates/CSS, and 1 cross-cutting note (no migration needed) —
directly traceable to P3-1 through P3-5. The architectural decision behind
this breakdown is recorded in
`docs/adr/0006-phase-3-calendar-event-view-model.md`. One open item (OI-1,
final microcopy) remains for the PO; it does not block starting
implementation. Ready for handoff to Dev once OI-1 is confirmed or Dev
proceeds with placeholder copy pending PO review.
