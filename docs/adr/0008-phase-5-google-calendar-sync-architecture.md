# 0008: Phase 5 Google Calendar Sync Architecture

## Status
Accepted

## Context
Phase 5 (P5-1 through P5-5) pushes CatAgenda's computed care events into a
user's Google Calendar. This is CatAgenda's first external integration, and
it forces four real architectural forks at once, plus one product question
(P5-4) that shapes the same entities the architecture introduces:

1. **Identity tracking (P5-2, P5-3):** how does the app know a given care
   event has already been pushed to Google, so a re-sync updates that same
   Google event instead of creating a duplicate? This is hard specifically
   because `CalendarEvent` (ADR 0006) is a stateless view-model, recomputed
   fresh on every read from `ReasoningService.getDueSoon(user)`, with no
   database id — only a natural composite key (cat + event type + vaccine
   type).
2. **OAuth token storage/refresh (P5-1):** Google Calendar access requires
   an access token (short-lived) and a refresh token (long-lived), acquired
   through OAuth's authorization-code flow and persisted so background sync
   can run without the user present in a browser.
3. **Sync trigger model (P5-2, P5-3, P5-5):** what actually causes a push to
   happen — a user action, a page load, a background schedule, or some
   combination.
4. **Staleness detection (P5-3):** how "the due date changed, push an
   update" is computed at all, given there is no persisted row with its own
   change history to compare against — this follows directly from decisions
   1 and 3 rather than being an independent fork, and is recorded here for
   completeness rather than as a fresh option set.
5. **Disconnect/reconnect behavior (P5-4):** an explicitly product-flavored
   question — what happens to already-synced Google events when a user
   disconnects, and what happens to CatAgenda's own mapping state if they
   reconnect later. Folded into this ADR because it directly governs the
   lifecycle of the entities Decision 1 introduces.

All five were presented to the client as alternatives with a recommendation
per CLAUDE.md's decision-making protocol; the client's explicit picks are
recorded as the Decision below.

## Options Considered

### Decision 1 — synced-event identity and sync-status tracking (P5-2, P5-3, P5-5)

**Option A — new `SyncedEvent` entity, local source of truth for identity
*and* status**
A new table keyed by the same natural key `CalendarEvent` already exposes —
`(owner_id, cat_id, event_type, vaccine_type)` — storing the corresponding
`google_event_id`, the due date last pushed, and per-event sync health
(status + last error). A sync run recomputes `List<CalendarEvent>` and looks
each one up by that key.
- Pros: a single, queryable local table answers both "is this already
  synced, to which Google event" (identity) and "is it healthy, what failed"
  (P5-5) without any call to Google. New event types (Phase 6 spay/neuter)
  are additive — a new enum case, no schema change.
- Cons: one more table to keep consistent with reality; if a user manually
  edits/deletes the event on Google's side directly, our local mapping can
  drift from Google's actual state — an unavoidable risk of any local
  mapping, not specific to this option.

**Option B — store `google_event_id` on the "source" entity
(`VaccinationRecord`/`CheckupRecord`)**
Ruled out structurally, not just as a tradeoff — the same way ADR 0007 ruled
out reusing the `VaccineType.OTHER` pattern for `CartItem`. A due date
frequently has **no single source row** to attach a column to: a checkup due
date can anchor to the cat's creation date when no `CheckupRecord` exists
yet, and a vaccine due date can represent "never given, flagged as needed"
(P2-2) with no `VaccinationRecord` at all. There is nothing to put
`google_event_id` on in either case.

**Option C — identity lives in Google's extended properties, no local
mapping table**
Google Calendar events support private extended properties (arbitrary
key/value metadata). Store the natural key there instead of locally, and
query Google (filtered by extended property) to determine create-vs-update.
This was the client's initial preference, but was reversed once the
implications below were made explicit:
- Every identity check becomes a live round-trip to Google (latency, rate
  limits) just to decide create-vs-update, for every event, on every sync
  run.
- P5-5 ("see sync health, retry") needs local state regardless — a failure
  means *nothing* was written to Google, so there is nothing on Google's
  side to query to discover a failure happened. Local status tracking is
  unavoidable either way.
- Once local status tracking exists anyway, storing identity there too costs
  nothing extra and removes the round-trip dependency Option C would have
  added for no remaining benefit.

**Multi-user reasoning:** `SyncedEvent` is owner-scoped exactly like
`CartItem`/`SupplyTracker`/`Cat` — one row per `(owner, event)`, no shared or
cross-user state. Unchanged into Phase 8's future multi-owner direction.

### Decision 2 — OAuth token acquisition, storage, and refresh (P5-1)

Framing note: this is **not** a second login mechanism. CatAgenda's own auth
stays exactly as ADR 0002 defined it (server-side sessions,
`UserDetailsService`, BCrypt). Google OAuth here authorizes API access to
*the user's* Calendar on behalf of an already-authenticated CatAgenda
session — Spring Security's OAuth2 Client support distinguishes this
("OAuth2 Client," authorization/API-access) from "OAuth2 Login"
(authenticating *into* the app via a provider), and supports the former
without touching the latter.

**Option A — Spring Security's OAuth2 Client support
(`spring-boot-starter-oauth2-client`), authorization-only registration**
A `ClientRegistration` for Google scoped to Calendar access only, used
purely for authorization. Framework handles the authorization-code
redirect/callback/state-parameter mechanics. Tokens persisted via a custom
`OAuth2AuthorizedClientService` backed by a JPA entity.
- Pros: this is exactly the extension point ADR 0002 flagged ("sets up
  Phase 5 to plug into the same Spring Security foundation"). Consistent
  with this codebase's established posture — framework-provided mechanisms
  for security-sensitive code over hand-rolled equivalents, the same
  reasoning ADR 0002 used to reject hand-rolled auth. State/CSRF handling on
  the OAuth redirect is provided, not custom-written.
- Cons: one more Spring module/concept to learn; its abstractions lean
  slightly toward the login use case, so some configuration is needed to use
  it purely for authorization.

**Option B — hand-rolled OAuth2 flow (manual redirect building, manual token
exchange, manual refresh timing)**
- Cons: re-implements exactly the mechanics that are easy to get subtly
  wrong — state/CSRF validation on the callback, refresh timing, a
  revoked/expired refresh token — the same category of risk ADR 0002
  explicitly rejected "hand-rolled auth" for. Would introduce a second,
  lower-quality security posture into a codebase that already made the
  opposite call once.

**Option C — Google's own Java API client library
(`google-api-client`/`google-oauth-client`), independent of Spring Security**
- Cons: introduces a second, parallel OAuth idiom alongside Spring
  Security's — duplicated concept, not just code, for no offsetting benefit;
  the JPA-backed token store still has to be hand-written either way
  (Google's `DataStore` interface is pluggable, not itself a database).

**Multi-user reasoning:** the connection entity is 1:1 with `User`, same
posture as everything else. What doesn't trivially scale is the *volume* of
outbound token-refresh/API calls across many owners — that is Decision 3's
concern, not this one's.

### Decision 3 — sync trigger model (P5-2, P5-3, P5-5)

**Option 1 — sync as a side effect of page load** — ruled out outright,
regardless of the other picks: makes a `GET` request's latency depend on a
third-party API round trip, turns a read into a hidden write (nothing else
in this app does that — `CalendarService`/`CartService` are pure reads), and
risks duplicate/overlapping runs on rapid refreshes.

**Option 2 — explicit "Sync now" user action** — a `POST` endpoint the user
triggers directly, matching this app's existing pattern for state-changing
actions (P4-2's "mark restocked" is explicit, not implicit). Also directly
serves P5-5's "retry" requirement — retry is the same action, re-run.

**Option 3 — scheduled background job** (`@Scheduled`, no external
job-scheduler infrastructure needed) — iterates every connected owner
periodically, closing the staleness gap Option 2 alone would leave (a user
who never clicks "sync now" would otherwise never see a due-date change
propagate).
- Named cost at real scale: iterating N owners sequentially against a
  rate-limited external API doesn't scale indefinitely, and a naive
  single-instance `@Scheduled` job double-executes if the app ever runs on
  multiple instances (would need distributed-scheduling coordination, e.g.
  ShedLock, at that point) — deferrable at this app's current scale, not
  free to ignore forever.

**Multi-user reasoning:** the explicit action is trivially per-owner at any
scale. The scheduled job is the one piece that genuinely needs revisiting at
real volume (batching/backoff, eventually a queue) — an additive
infrastructure change later, not a data-model rework, the same "defer, don't
redesign" posture ADR 0002 used for session externalization.

### Decision 4 — staleness detection (P5-3)

Not an independent fork — it follows directly from Decisions 1 and 3.
Recorded for completeness: each sync run (however triggered) recomputes the
full `List<CalendarEvent>` via the unchanged `CalendarService.getEventsForOwner`
and diffs it against that owner's `SyncedEvent` rows (full reconciliation),
rather than event-driven "dirty" hooks in `VaccinationService`/
`CheckupService`/`CatService`. The hook-based alternative was disqualified
outright during the original proposal: a due date can enter the "due soon"
window purely because *today's date advanced*, with no corresponding write
anywhere to hook — only full reconciliation against current computed state
is correct by construction for that case.

### Decision 5 — disconnect/reconnect behavior (P5-4, product decision)

**Option A — leave previously-synced events on Google as-is; deactivate,
don't delete, local mapping state** *(chosen)*
Disconnecting sets `GoogleCalendarConnection.disconnectedAt = now()` and
bulk-marks every one of that owner's `SyncedEvent` rows
`deactivatedAt = now()`. No call to Google is made — the events the user
already has stay exactly where they are. Reconnecting clears
`disconnectedAt` on the (owner-unique) connection row and clears
`deactivatedAt` on those same `SyncedEvent` rows — **reactivating** existing
mappings rather than deleting and letting the next sync recreate them from
scratch. This matters concretely: because the old `google_event_id` and
`last_synced_due_date` are preserved, the very next reconciliation run
(Decision 4) sees those events as "already synced, matching due date" and
does nothing — no duplicate is created on Google's side. Deleting the
mapping rows on disconnect instead would have made reconnect
indistinguishable from a first-time connect, silently duplicating every
still-current event.

**Option B — delete synced events from Google on disconnect** *(rejected)*
Disconnect becomes a sync-like operation in its own right — it must call
Google to delete every previously-synced event, with its own partial-failure
mode. Worth naming as a specific irony that counted against it: disconnects
are often *caused by* an expired/revoked token, which is exactly the
condition that would make those delete calls fail, potentially leaving a mix
of deleted and orphaned events with no clean way to know which is which.

## Decision
1. **Identity/status:** Option A — new `SyncedEvent` entity as the local
   source of truth for both identity mapping and per-event sync health.
2. **OAuth:** Option A — Spring Security OAuth2 Client, authorization-only
   `ClientRegistration`, tokens stored encrypted in a new
   `GoogleCalendarConnection` entity via a custom `OAuth2AuthorizedClientService`.
3. **Trigger:** Options 2 + 3 combined — explicit "Sync now" action (also
   serving as P5-5's retry) plus a `@Scheduled` background job as the
   staleness backstop. Page-load-triggered sync is ruled out.
4. **Staleness detection:** full reconciliation/diff on every sync run,
   flowing directly from (1) + (3).
5. **Disconnect/reconnect:** Option A — leave Google events as-is on
   disconnect; deactivate (not delete) `SyncedEvent` rows; reactivate (not
   recreate) them on reconnect.

```
User (existing)
   1                                    1
   │ owns                               │ owns
   *                                    *
GoogleCalendarConnection            SyncedEvent
(id, owner_id FK UNIQUE,            (id, owner_id FK, cat_id FK,
 access_token (encrypted),           event_type, vaccine_type nullable,
 refresh_token (encrypted),          google_event_id,
 expires_at, scope,                  last_synced_due_date,
 connected_at,                       sync_status, last_error,
 disconnected_at nullable)           last_synced_at,
                                      deactivated_at nullable)
                                      UNIQUE(owner_id, cat_id,
                                             event_type, vaccine_type)
```

```
"Sync now" (explicit) ──┐
                         ├──► GoogleSyncService.syncForOwner(owner)
@Scheduled job (periodic)┘         │
                                    │ 1. reads (unchanged, read-only)
                                    ▼
                     CalendarService.getEventsForOwner(owner)   [ADR 0006]
                                    │
                                    ▼
                     ReasoningService.getDueSoon(owner)          [ADR 0003]

GoogleSyncService.syncForOwner(owner), continued:
  2. loads owner's active SyncedEvent rows
  3. diffs current events vs. stored rows:
       new key            -> create on Google, insert SyncedEvent
       key exists, date≠   -> update on Google, update stored date
       key exists, date=   -> no-op
       stored key missing  -> delete on Google, delete SyncedEvent row
       from current events
  4. per-event try/catch: one event's failure is recorded on its own
     row (status=FAILED, lastError set) and does not abort the rest
     of the owner's run; one owner's failure does not abort other
     owners' runs in the scheduled batch
```

## Consequences
- A new `com.catagenda.googlesync` package depends on `calendar` (ADR 0006),
  `reasoning` (ADR 0003, transitively), `cat`, and `user` — read-only in
  every case. Nothing in those packages is modified or becomes aware sync
  exists; `calendar`/`reasoning` remain the same stable boundaries Phase 3
  and Phase 4 already depend on, and Phase 5 becomes a third, independent
  consumer rather than a change to either.
- Sync status becomes visible on the existing calendar view by having
  `CalendarController` — not `CalendarService` — additionally query a small
  read method in `googlesync` and pass both results to the template. The
  merge happens at the controller/template layer, the same boundary
  discipline ADR 0006 already established (a presentation-layer decoration,
  not a reshaping of an accepted contract).
- Two new tables (`google_calendar_connection`, `synced_event`), both
  owner-scoped exactly like every other entity in this app (`Cat` — ADR
  0001; `CartItem`/`SupplyTracker` — ADR 0007) — this holds unchanged into
  Phase 8's future multi-owner direction with no rework required.
- Refresh tokens are long-lived, high-value credentials (equivalent to a
  password for the user's calendar) — stored encrypted at rest via a JPA
  `AttributeConverter`, not plaintext, even at this app's current scale. A
  full secrets-manager story is real-production territory and explicitly out
  of scope for now, but plaintext storage was never on the table.
- The scheduled job (`@Scheduled`) is a known, accepted deferral: it runs
  in-process on a single instance today; multi-instance deployment would
  need distributed-scheduling coordination added later as an additive
  infrastructure change, not a redesign of anything decided here — the same
  posture ADR 0002 already used for session externalization.
- Disconnect never calls Google (Decision 5), so it cannot fail partway
  through and cannot leave a mixed deleted/orphaned state — a direct
  consequence of choosing "leave as-is" over "delete on disconnect."
  Reconnect reusing (not recreating) `SyncedEvent` rows is what prevents
  duplicate Google events after a disconnect/reconnect cycle; this was the
  specific, real follow-up question raised during review and is captured
  here explicitly, not left implicit in "reconnect just works."
- Adding Phase 6's spay/neuter reminder to sync is additive to `SyncedEvent`
  (a new `event_type` case already supported by `CalendarEventType`,
  reused here) — no schema change.
