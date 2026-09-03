# Phase 5 — Technical Task Breakdown

Prepared by the Tech Lead from BACKLOG.md's Phase 5 stories (P5-1 through
P5-5), following the decisions recorded in
`docs/adr/0008-phase-5-google-calendar-sync-architecture.md`: a new
`SyncedEvent` entity as the local source of truth for identity + sync
health, Spring Security's OAuth2 Client support (authorization-only) for
Google token acquisition/storage/refresh, a "Sync now" action plus a
`@Scheduled` job as the sync trigger, full reconciliation-diff on every sync
run for staleness detection, and disconnect-deactivates/reconnect-reactivates
(never deletes) for `SyncedEvent` lifecycle.

Package layout, following package-by-feature per
`.claude/skills/spring-boot-conventions`:
- `com.catagenda.googlesync` — new: `GoogleCalendarConnection`,
  `GoogleCalendarConnectionRepository`, `SyncedEvent`,
  `SyncedEventRepository`, `GoogleSyncStatus`, `JpaOAuth2AuthorizedClientService`
  (Spring Security extension point), `GoogleCalendarClient` (thin API
  wrapper), `GoogleSyncService` (the reconciliation engine),
  `GoogleSyncScheduler`, `GoogleConnectionController`, `GoogleSyncController`.
  Reads (read-only) from `com.catagenda.calendar`, `com.catagenda.cat`, and
  `com.catagenda.user`. Nothing in `calendar` or `reasoning` depends back on
  `googlesync` — see ADR 0008's Consequences.
- `com.catagenda.calendar` — extended only at the controller layer:
  `CalendarController` additionally queries a `googlesync` read method to
  decorate the existing calendar view with per-event sync status.
  `CalendarService`/`CalendarEvent`/`CalendarEventType` are **untouched**.
- `src/main/resources/templates/googlesync/` — new: `status.html` (the
  connect/disconnect/sync-now/health screen).
- `src/main/resources/templates/calendar/view.html` — extended (not
  replaced) to render a small sync badge per event.

Status: not started. Task list only, no implementation yet.

---

## 0. Design notes (context for the tasks below, not tasks themselves)

- **OAuth2 Client, not OAuth2 Login:** the `google-calendar` `ClientRegistration`
  is registered purely for authorization (API access to a resource the user
  owns), not as a login provider — CatAgenda's own session-based login (ADR
  0002) is completely unaffected. *New Spring concept:* an OAuth2 "scope"
  defines exactly what access is being requested — this app requests the
  narrowest scope that does the job
  (`https://www.googleapis.com/auth/calendar.events`, event-level access,
  not full calendar management), both because it satisfies P5-1's AC
  ("clearly told what access is being requested" reads better/narrower this
  way) and as ordinary least-privilege practice.
- **Google-specific gotcha to build against, not rediscover:** Google only
  returns a `refresh_token` on a user's *first* consent by default; a
  reconnect after disconnect (P5-4) or a re-consent will not include one
  unless the authorization request explicitly forces it
  (`access_type=offline` + `prompt=consent` request parameters on the
  `ClientRegistration`/authorization request). Without this, a reconnect
  could silently leave the app with only an access token and no way to
  refresh it later. Call this out directly in T2.1 rather than leaving Dev
  to discover it during testing.
- **Background sync needs a service-oriented authorized-client manager, not
  the request-scoped one:** the interactive connect flow (T2.3) runs inside
  an HTTP request and can use Spring Security's default,
  request-scoped `OAuth2AuthorizedClientRepository`. The scheduled job
  (T5.2) runs with no HTTP request and no `SecurityContext` principal at
  all, so it must instead use an
  `AuthorizedClientServiceOAuth2AuthorizedClientManager` (built from the
  `OAuth2AuthorizedClientService` in T2.2), which can authorize/refresh a
  client for a given owner outside of any request. Both `GoogleSyncService`
  call paths (T5.1's "sync now" and T5.2's scheduled batch) should go
  through the same service-oriented manager for consistency, rather than
  the interactive flow's request-scoped one — one code path, two triggers.
- **One reconciliation method, two callers:** `GoogleSyncService.syncForOwner(owner)`
  is the single place the diff-and-push logic (ADR 0008, Decision 4) lives.
  `GoogleSyncController`'s "Sync now" action and `GoogleSyncScheduler`'s
  periodic job both call it — no duplicated reconciliation logic between
  the two triggers.
- **Failure isolation is a requirement, not a nicety:** a single event's
  Google API failure (T4.2) must not abort the rest of that owner's run, and
  a single owner's failure (e.g. an unrecoverable expired refresh token)
  must not abort other owners' runs in the scheduled batch (T4.3) — this is
  what keeps ADR 0008's Decision 3 multi-user reasoning true in practice,
  not just on paper.
- **Sync status decoration stays at the controller layer:** `CalendarService`
  is not modified to know about sync. `CalendarController` calls both
  `CalendarService.getCalendarView(...)` (unchanged) and a new
  `googlesync` read method keyed by the same natural key `CalendarEvent`
  already exposes (cat id + event type + vaccine type), and passes both to
  the template. This keeps `calendar`'s already-accepted boundary (ADR 0006)
  untouched, per ADR 0008's Consequences.
- **Token encryption:** access/refresh token columns use a JPA
  `AttributeConverter` (T1.7) with a symmetric key read from configuration
  (an env var, not committed). A locally-generated dev key is acceptable for
  this phase; a real secrets-management story is out of scope (ADR 0008's
  Consequences already names this explicitly).
- **`SyncedEvent` lifecycle verbs, precisely (ADR 0008, Decision 5):**
  "no longer applies" (source disappeared from the current calendar) is a
  **hard delete** of the `SyncedEvent` row (plus a delete call to Google) —
  it genuinely doesn't apply anymore. "Disconnected" is a **soft
  deactivation** (`deactivatedAt` set, row kept, no Google call). These are
  two different operations on the same table and must not be conflated —
  T4.1 and T2.4 implement different ones.

---

## 1. `googlesync` package — core entities & persistence

- **T1.1** — `GoogleSyncStatus` enum: `OK`, `FAILED`. (No `PENDING`
  state needed — a `CalendarEvent` with no matching `SyncedEvent` row simply
  hasn't been synced yet; only rows that exist carry a status.)
- **T1.2** — `GoogleCalendarConnection` entity: `id`, `owner`
  (`@ManyToOne` to `User`, not null, unique — one connection per owner),
  `accessToken` (encrypted, T1.7), `refreshToken` (encrypted, T1.7),
  `expiresAt`, `scope`, `connectedAt`, `disconnectedAt` (nullable —
  presence means inactive, the same nullable-means-absent convention
  `dismissedAt`/`restockedAt` already establish in `cart`/`supply`).
- **T1.3** — `GoogleCalendarConnectionRepository extends JpaRepository<GoogleCalendarConnection, Long>`
  with `findByOwner(User owner)` and `findByOwnerAndDisconnectedAtIsNull(User owner)`
  (the "active connection" query T4.3/T5.2 need).
- **T1.4** — `SyncedEvent` entity: `id`, `owner` (`@ManyToOne`, not null),
  `cat` (`@ManyToOne`, not null), `eventType` (`CalendarEventType`, reused
  from `com.catagenda.calendar` — no duplicate enum), `vaccineType`
  (nullable, mirrors `CalendarEvent`'s own nullable field), `googleEventId`,
  `lastSyncedDueDate`, `syncStatus` (`GoogleSyncStatus`), `lastError`
  (nullable), `lastSyncedAt`, `deactivatedAt` (nullable — same convention as
  T1.2), `createdAt`.
- **T1.5** — `SyncedEventRepository extends JpaRepository<SyncedEvent, Long>`
  with `findByOwnerAndDeactivatedAtIsNull(User owner)` (active rows, what
  T4.1's diff reads), `findByOwnerAndCatAndEventTypeAndVaccineType(...)`
  (single natural-key lookup), and `findByOwner(User owner)` (all rows,
  including deactivated — what T6.1's status page needs).
- **T1.6** — Flyway migrations: `V6__google_calendar_connection.sql`
  (`google_calendar_connection` table, `owner_id` unique FK) and
  `V7__synced_event.sql` (`synced_event` table, `owner_id`/`cat_id` FKs,
  unique constraint on `(owner_id, cat_id, event_type, vaccine_type)`,
  index on `owner_id`) — two files, following the same "each phase's new
  tables get their own migration" precedent as `V4`/`V5`.
- **T1.7** — `EncryptedStringConverter implements AttributeConverter<String, String>`,
  applied via `@Convert` to `accessToken`/`refreshToken`, backed by a
  symmetric key from a config property (Section 0's design note).
- **T1.8** — Unit tests: repository query methods behave as expected
  (active-only filters exclude deactivated/disconnected rows); entity
  construction enforces required fields, following the same lightweight
  coverage precedent Phase 4's `CartItem`/`SupplyTracker` tests use.

## 2. OAuth connect / disconnect / reconnect (P5-1, P5-4)

- **T2.1** — `ClientRegistration` config for `google-calendar` in
  `application.yml`: client id/secret via env vars (never committed), scope
  `https://www.googleapis.com/auth/calendar.events` (least-privilege, per
  Section 0), redirect URI, and `access_type=offline` + `prompt=consent`
  additional authorization request parameters so a refresh token is issued
  on reconnect, not just first-ever connect (Section 0's Google-specific
  gotcha).
- **T2.2** — `JpaOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService`:
  `loadAuthorizedClient`/`saveAuthorizedClient`/`removeAuthorizedClient`
  backed by `GoogleCalendarConnectionRepository`, mapping to/from
  `GoogleCalendarConnection` (tokens encrypted on save via T1.7). This is
  the Spring Security extension point that makes tokens persist to our own
  table instead of Spring's default in-memory implementation.
- **T2.3** — `GoogleConnectionController`: `GET /settings/google` (status
  view — connected/not, connected-since, T6.4's template); the actual
  connect redirect is Spring Security's built-in
  `/oauth2/authorization/google-calendar` endpoint (a link in the template,
  no custom controller code needed for the redirect itself); a success/
  failure/cancel landing handler that shows a clear, on-brand message either
  way and leaves no partial/broken connection state (P5-1's AC) — a
  cancelled or failed authorization simply means no
  `GoogleCalendarConnection` row is created/updated, nothing to roll back.
- **T2.4** — `POST /settings/google/disconnect`: sets
  `GoogleCalendarConnection.disconnectedAt = now()` and bulk-sets
  `deactivatedAt = now()` on every one of that owner's active `SyncedEvent`
  rows. **No call to Google is made** (ADR 0008, Decision 5 — previously
  synced events are left exactly as they are on the user's calendar).
  Redirects to `/settings/google` with on-brand confirmation copy stating
  plainly what did and didn't happen (existing events were left alone; new
  changes will stop syncing).
- **T2.5** — Reconnect handling: on a successful OAuth callback where an
  owner already has a (disconnected) `GoogleCalendarConnection` row, update
  that row in place (clear `disconnectedAt`, store the new tokens) rather
  than inserting a second row (enforced structurally by T1.2's unique
  `owner_id`), and bulk-clear `deactivatedAt` on that owner's existing
  `SyncedEvent` rows — reactivating them so the next reconciliation (T4.1)
  treats previously-synced events as still-valid mappings instead of
  creating duplicates on Google.
- **T2.6** — Unit/integration tests: disconnect deactivates the connection
  and all of that owner's synced events, and makes no call to
  `GoogleCalendarClient`; reconnect reactivates existing `SyncedEvent` rows
  rather than creating new ones and updates tokens on the existing
  connection row rather than inserting a second one; ownership scoping
  (user A's disconnect/reconnect never touches user B's rows).

## 3. `GoogleCalendarClient` — thin Google Calendar API wrapper (P5-2, P5-3)

- **T3.1** — Interface (`createEvent`, `updateEvent`, `deleteEvent`) plus an
  implementation calling the Google Calendar API, given a valid access
  token obtained via the service-oriented authorized-client manager
  (Section 0). Isolates Google SDK/HTTP specifics behind a small interface
  so `GoogleSyncService` (T4) can be unit tested against a mock, the same
  "isolate the external boundary behind a thin wrapper" shape
  `SearchLinkBuilder` used for Phase 4's outbound search links, one level
  more involved here since this wrapper makes real authenticated calls.
- **T3.2** — Event payload builder: title/description explicitly identify
  the cat and event type (+ vaccine type when applicable), satisfying P5-2's
  AC directly (e.g. title `"Mochi — Rabies booster due"`).
- **T3.3** — Unit tests for payload building only (pure function, no live
  API calls); `GoogleCalendarClient`'s actual HTTP behavior is exercised via
  `GoogleSyncService`'s tests against a mocked interface (T4.4), not tested
  directly against Google here.

## 4. `GoogleSyncService` — reconciliation engine (P5-2, P5-3, P5-5)

- **T4.1** — `syncForOwner(User owner)`: no-op if the owner has no active
  `GoogleCalendarConnection`. Otherwise calls
  `CalendarService.getEventsForOwner(owner)` (unchanged, ADR 0006) and
  `SyncedEventRepository.findByOwnerAndDeactivatedAtIsNull(owner)`, then
  diffs by natural key per ADR 0008's Decision 4:
  - current event, no matching row → create via `GoogleCalendarClient`,
    insert a new `SyncedEvent` (`status = OK`).
  - matching row, due date differs from `lastSyncedDueDate` → update via
    `GoogleCalendarClient`, update the stored due date.
  - matching row, due date unchanged → no-op.
  - row exists but its key is absent from the current event list (source no
    longer applies — cat removed, vaccine no longer due, etc.) → delete via
    `GoogleCalendarClient`, **hard-delete** the `SyncedEvent` row (distinct
    from T2.4's soft deactivation — see Section 0).
- **T4.2** — Per-event isolation: wrap each create/update/delete call in its
  own try/catch; on failure, set that row's `syncStatus = FAILED` and
  `lastError` to a clear message, and continue the loop — one event's
  failure never aborts the rest of that owner's run.
- **T4.3** — `syncAllActiveOwners()`: iterates every owner with an active
  `GoogleCalendarConnection` (T1.3), calling `syncForOwner` per owner inside
  its own try/catch — one owner's unrecoverable failure (e.g. an expired
  refresh token) never aborts the batch for other owners.
- **T4.4** — Unit tests (JUnit + Mockito, mocked `CalendarService`/
  `SyncedEventRepository`/`GoogleCalendarConnectionRepository`/
  `GoogleCalendarClient` — no `@SpringBootTest`, consistent with how
  `reasoning`/`calendar`/`cart` are tested): create/update/no-op/delete
  cases for a single owner; an event-level failure is recorded on its own
  row and doesn't block sibling events in the same run; an owner-level
  failure doesn't block other owners in `syncAllActiveOwners`; a
  disconnected/inactive owner is skipped entirely; ownership scoping
  throughout.

## 5. Sync triggers (P5-2, P5-3, P5-5)

- **T5.1** — `POST /settings/google/sync` (`GoogleSyncController`): resolves
  the owner via `CurrentUserProvider` (existing convention), calls
  `GoogleSyncService.syncForOwner(owner)` synchronously, redirects to
  `/settings/google` with a result summary (counts created/updated/removed/
  failed). This is the same action that serves P5-5's "retry" — a
  previously-failed event is simply attempted again on the next run of the
  same reconciliation, not a separate retry code path.
- **T5.2** — `GoogleSyncScheduler`: a `@Scheduled(fixedDelay = ...)` method
  calling `GoogleSyncService.syncAllActiveOwners()`. *New Spring concept:*
  `@Scheduled` runs a method on a fixed interval or cron expression with no
  external job-scheduler infrastructure required; needs `@EnableScheduling`
  added once on a config class (T7.3). Exact interval is an operational
  choice, not an architectural one — see OI-2.
- **T5.3** — Test coverage for the scheduler itself is intentionally thin
  (it's a one-line delegation to `syncAllActiveOwners`, already covered by
  T4.4); a smoke test confirming the method is scheduled/wired is optional,
  Dev's call.

## 6. Sync status visibility (P5-2, P5-5)

- **T6.1** — A read method exposing sync status by natural key — either
  `SyncedEventRepository.findByOwner(owner)` directly, or a thin
  `GoogleSyncService.getStatusForOwner(owner)` wrapper if some shaping is
  useful for the template. Includes deactivated rows too, since a
  disconnected user should still be able to see what *was* synced.
- **T6.2** — `CalendarController` extension: alongside the existing
  `CalendarService.getCalendarView(...)` call, also calls T6.1's method and
  builds a lookup (natural key → status) added to the model — `calendar`
  package classes themselves are not modified (Section 0's design note).
- **T6.3** — `calendar/view.html` extension: a small per-event badge
  ("Synced" / "Not yet synced" / "Sync failed") next to each rendered event,
  sourced from T6.2's lookup. Per this app's established accessibility
  pattern (P3-4's overdue styling), status must be conveyed by text/label,
  not color alone.
- **T6.4** — New `googlesync/status.html`: connection state (connected/not,
  connected-since), a list of `SyncedEvent` rows with status + last error +
  last synced time, the "Sync now" button (T5.1), and connect/disconnect
  actions (T2.3/T2.4). This is the P5-5 "see current sync health" screen.
- **T6.5** — Add a settings/sync link to `templates/fragments/nav.html`,
  alongside the existing "Calendar"/"Supply Chain" links (on-brand label
  pending PO — see OI-1).

## 7. Cross-cutting

- **T7.1** — Flyway migrations (T1.6, cross-referenced here for visibility):
  `V6__google_calendar_connection.sql`, `V7__synced_event.sql`.
- **T7.2** — `application.yml` additions: OAuth2 client registration (T2.1),
  the encryption key property (T1.7) — both sourced from environment
  variables, neither committed with real values.
- **T7.3** — `@EnableScheduling` added once to a config class — first use of
  Spring's scheduling support in this codebase, needed for T5.2.

---

## Open items — need PO/client confirmation before this is fully "done"

- **OI-1 (PO):** on-brand microcopy — the P5-1 consent-framing message
  ("clearly told what access is being requested"), connect/disconnect
  confirmation copy (T2.3/T2.4, including making the "events you already
  have on Google are left alone" behavior clear to the user, not just
  technically true), sync failure/retry messaging (T6.4), sync badge labels
  (T6.3), and the nav link label (T6.5). Dev will build against placeholder
  copy; PO should confirm/replace final strings before this phase ships,
  same pattern as Phase 3/4's OI-1.
- **OI-2 (PO/ops):** the scheduled sync interval (T5.2) — a placeholder of
  hourly is reasonable to build against, but the real choice trades off
  staleness (how long a due-date change can sit unpushed before the
  scheduled backstop catches it) against Google Calendar API quota usage
  across all connected owners; needs a deliberate answer once real usage
  patterns are known, not a permanent architectural commitment either way
  (changing the interval later is a one-line config change).

No other open items — the Google-specific `access_type`/`prompt` parameters
(T2.1) and the service-oriented vs. request-scoped authorized-client-manager
split (Section 0) are resolved above as Tech Lead implementation guidance,
not flagged, since they're correctness details within an already-decided
architecture rather than a fresh fork. The five decisions that did meet the
bar for a stop-and-wait (identity/status tracking, OAuth storage, sync
trigger, staleness detection, disconnect/reconnect lifecycle) were already
decided by the client and are recorded in
`docs/adr/0008-phase-5-google-calendar-sync-architecture.md`.

---

## Summary
37 tasks across 7 sections: 8 building the new `googlesync` package's core
entities/persistence, 6 on the OAuth connect/disconnect/reconnect flow, 3 on
the thin Google Calendar API wrapper, 4 on the reconciliation engine itself,
3 on the "sync now" / scheduled-job triggers, 5 on sync-status visibility
(including the calendar view's new sync badges), and 3 cross-cutting
config/migration tasks — directly traceable to P5-1 through P5-5. The
architectural decisions behind this breakdown are recorded in
`docs/adr/0008-phase-5-google-calendar-sync-architecture.md`. Two open items
(OI-1 microcopy, OI-2 scheduled interval) remain for the PO; neither blocks
starting implementation. Ready for handoff to Dev.
