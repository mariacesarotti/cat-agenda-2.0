# Phase 4 — Technical Task Breakdown

Prepared by the Tech Lead from BACKLOG.md's Phase 4 stories (P4-1 through
P4-6), following the decisions recorded in
`docs/adr/0007-phase-4-cart-data-model.md` (new `CartItem` entity for
manual items, decoupled from `SupplyTracker`/`SupplyReasoningService`; a
new `dismissedAt` column on both `SupplyTracker` and `CartItem` for
indefinite dismiss semantics).

Package layout, following package-by-feature per
`.claude/skills/spring-boot-conventions`:
- `com.catagenda.cart` — new: `CartItem`, `CartItemRepository`,
  `CartItemView` (unified display DTO), `CartService` (the merge layer),
  `CartController`, `SearchLinkBuilder`.
- `com.catagenda.supply` — extended: `SupplyTracker` gains `dismissedAt`;
  `SupplyService` gains `dismiss`/`restore`, and `markRestocked` is
  extended to clear any existing dismissal.
- `com.catagenda.reasoning` — **untouched**. `SupplyReasoningService`
  keeps answering "what's due" exactly as it does today (ADR 0003/0004);
  dismissal filtering happens one layer up, in `CartService`. See design
  note below.
- `src/main/resources/templates/cart/` — new: `view.html`.

Status: not started. Task list only, no implementation yet.

---

## 0. Design notes (context for the tasks below, not tasks themselves)

- **Merge layer, not a reasoning change:** `CartService` is the single
  place that combines `SupplyReasoningService.computeHouseholdNeeds(owner)`
  (catalog-driven, cadence-computed) with `CartItemRepository`'s active
  manual items into one `List<CartItemView>` for the template — the same
  "don't push a presentation-driven merge back into an already-accepted
  boundary" reasoning ADR 0006 used for the calendar. `reasoning` stays
  the stable read boundary Phase 3 already depends on; Phase 4 doesn't
  bend it to fit the cart's needs.
- **Where dismissal filtering lives:** `SupplyReasoningService` is not
  made aware of `dismissedAt` at all — it keeps returning every currently-
  due item, dismissed or not. `CartService` reads `SupplyService.getForOwner
  (owner)` (which now includes `dismissedAt`), builds a `Set<SupplyItemType>`
  of currently-dismissed types, and filters the reasoning output against it
  before building the display list. This keeps the already-tested reasoning
  engine completely unmodified — it answers "what's due"; `CartService`
  answers "what's due *and not currently dismissed*."
- **P4-4's "removed" interpreted as P4-5's "dismiss":** P4-4's AC says
  manual items "can be marked restocked or removed like system-generated
  ones." System-generated items don't support a hard delete (they're
  reasoning-computed, not rows a user can delete) — their only two
  non-restock action is dismiss. Read literally alongside P4-5, "removed"
  for a manual item means the same dismiss action, not a distinct hard
  delete. Resolved as a Tech Lead judgment call (not flagged) so both item
  kinds expose exactly the same three actions: restock, dismiss, search.
- **Restore affordance is required, not optional:** ADR 0007 accepted
  indefinite dismiss on the condition that its false-negative risk is
  mitigated in the UI with a visible way to see and restore dismissed
  items — so a "dismissed items" view/section is in scope for this phase,
  not a nice-to-have (T4.1, T5.2 below).
- **Search-assist mechanism (P4-3), resolved as a Tech Lead call, no
  ADR:** a generic web search engine query built from the item's display
  name (`SupplyItemType`'s display name for system items, `CartItem.name`
  for manual ones), rendered as an outbound link — e.g.
  `https://www.google.com/search?q=<url-encoded name> for cats`. No
  retailer-specific target, no API integration, no persisted state. This
  satisfies "search assistance only, not store integration" directly and
  is a one-line, easily-swapped URL template if that ever needs to change
  — it doesn't meet the bar for an ADR (no schema impact, no coupling, no
  multi-user reasoning that changes anything, trivially reversible).
- **P4-2 backdating, resolved as a non-blocking default, flagged for PO
  awareness rather than routed through PO first:** `SupplyService.
  markRestocked` and the new `CartItem` restock action already accept an
  explicit date rather than hardcoding "today," so this is a UI question,
  not a schema/service-contract one. Default: a date picker defaulting to
  today, with a "not in the future" guard enforced both client-side (HTML
  `max` attribute) and server-side (reject a future date the same way
  other invalid input is rejected). PO can revise this later without any
  service-layer change.

---

## 1. `supply` package extension — `dismissedAt` (P4-5)

- **T1.1** — Add `dismissedAt` (nullable `LocalDateTime`) to `SupplyTracker`.
  Absence means "not currently dismissed" — same nullable-means-absent
  convention already used for `lastRestockedAt`.
- **T1.2** — Flyway migration: `ALTER TABLE supply_tracker ADD COLUMN
  dismissed_at TIMESTAMP;` (nullable, no default).
- **T1.3** — `SupplyService.dismiss(User owner, SupplyItemType itemType)`:
  creates the tracker row if it doesn't exist yet (same "first touch
  creates the row" pattern as `markRestocked`), sets `dismissedAt = now()`.
  Does **not** touch `lastRestockedAt` (P4-5's AC).
- **T1.4** — `SupplyService.restore(User owner, SupplyItemType itemType)`:
  clears `dismissedAt` on the existing tracker row (no-op if no row exists
  or it's not currently dismissed).
- **T1.5** — Extend `SupplyService.markRestocked` to also clear
  `dismissedAt` when it sets `lastRestockedAt` — a real restock is
  unambiguous evidence any prior dismissal is moot (ADR 0007's
  Consequences).
- **T1.6** — Unit tests: `dismiss` sets `dismissedAt` without touching
  `lastRestockedAt`; `dismiss` creates a tracker row if none exists yet;
  `restore` clears `dismissedAt`; `markRestocked` clears an existing
  `dismissedAt` as a side effect; ownership scoping (user A cannot
  dismiss/restore user B's tracker row).

## 2. `cart` package — `CartItem` (P4-4)

- **T2.1** — `CartItem` entity: `id`, `owner` (`@ManyToOne` to `User`, not
  null — same convention as `Cat.owner`/`SupplyTracker.owner`), `name`
  (`String`, not null), `createdAt`, `restockedAt` (nullable), `dismissedAt`
  (nullable). No `SupplyItemType`/enum field at all — manual items are
  intentionally outside the catalog (ADR 0007, Decision 1).
- **T2.2** — `CartItemRepository extends JpaRepository<CartItem, Long>`
  with `findByOwner(User owner)`, `findByIdAndOwner(Long id, User owner)`,
  and `findByOwnerAndRestockedAtIsNullAndDismissedAtIsNull(User owner)`
  (the "active" query the merge layer needs).
- **T2.3** — Flyway migration: new `cart_item` table (`owner_id` FK not
  null, `name` not null, `created_at` not null, `restocked_at` nullable,
  `dismissed_at` nullable). Can be the same migration file as T1.2 or a
  separate one — Dev's call, no functional difference (same note Phase 2's
  tech-task doc made for its own migrations).
- **T2.4** — `CartItemService` (or folded into `CartService` directly —
  Dev's call, this is a thin CRUD layer either way): `add(User owner,
  String name)`, `markRestocked(User owner, Long id, LocalDate
  restockedOn)`, `dismiss(User owner, Long id)`, `restore(User owner, Long
  id)`, each scoped through `getOwnedOrThrow(id, owner)` — same convention
  `CatService`/`CheckupService` already establish (a `CartItem` is only
  ever reachable via its owner; another user's manual item looks like it
  doesn't exist).
- **T2.5** — Unit tests: add creates a row; restock/dismiss/restore set
  the right fields and don't cross-contaminate each other; ownership
  scoping (user A cannot reach/mutate user B's `CartItem`, gets the same
  not-found behavior as `CatService`'s pattern).

## 3. `cart` package — `CartService` (merge layer; P4-1, P4-2, P4-5)

- **T3.1** — `CartItemView` record: a single display shape for both kinds
  of item — `CartItemKind kind` (`SYSTEM`/`MANUAL`), `String displayName`,
  `SupplyItemType itemType` (nullable — only for `SYSTEM`), `Long
  cartItemId` (nullable — only for `MANUAL`), `List<Cat> relevantCats`
  (empty for `MANUAL`), `LocalDate dueDate` (nullable — `MANUAL` items
  have no computed due date), `boolean overdue` (always `false` for
  `MANUAL`). One record with kind-specific nullable fields, the same "one
  record, nullable type-specific fields" pattern ADR 0006/T1.2 already
  established for `CalendarEvent`.
- **T3.2** — `CartService.getActiveCart(User owner) -> List<CartItemView>`:
  calls `supplyReasoningService.computeHouseholdNeeds(owner)`, filters out
  any `SupplyItemType` currently dismissed (per Section 0's design note,
  cross-referencing `supplyService.getForOwner(owner)`), maps the survivors
  to `SYSTEM` `CartItemView`s; calls `cartItemRepository.
  findByOwnerAndRestockedAtIsNullAndDismissedAtIsNull(owner)` and maps
  those to `MANUAL` `CartItemView`s; returns both combined (system items
  first, then manual — a stable, deterministic order for the template).
- **T3.3** — `CartService.getDismissedItems(User owner) ->
  List<CartItemView>`: the mirror query — dismissed `SupplyTracker` rows
  plus dismissed `CartItem` rows, mapped the same way, for the "restore"
  view (Section 0's required mitigation).
- **T3.4** — Action methods the controller calls, each delegating to the
  right underlying service by `kind`: `markRestocked(User owner,
  CartItemKind kind, SupplyItemType itemTypeOrNull, Long
  cartItemIdOrNull, LocalDate restockedOn)`, `dismiss(...)`,
  `restore(...)` — same parameter shape, routes to `SupplyService` or
  `CartItemService` internally. (Alternative signature split into two
  overloads per kind is also reasonable — Dev's call on ergonomics, no
  architectural difference.)
- **T3.5** — Unit tests (JUnit + Mockito, mocked `SupplyReasoningService`/
  `SupplyService`/`CartItemRepository`, no `@SpringBootTest` — consistent
  with how `reasoning`/`calendar` are tested): active cart includes
  non-dismissed system items and active manual items; a dismissed system
  item is excluded from the active cart and appears in the dismissed view;
  a dismissed manual item likewise; restock/dismiss/restore route to the
  correct underlying service by kind; zero cats and zero manual items
  yields an empty active cart (feeds P4-6).

## 4. `SearchLinkBuilder` (P4-3)

- **T4.1** — `SearchLinkBuilder.buildSearchUrl(String displayName) ->
  String`: pure, stateless helper — URL-encodes `displayName` into a
  generic web search query string (Section 0's design note). No Spring
  dependencies, trivially unit tested with plain JUnit.
- **T4.2** — Unit test: encodes special characters correctly (spaces,
  punctuation in a manual item's free-text name), produces a well-formed
  URL.

## 5. `CartController` and templates (P4-1 through P4-6)

- **T5.1** — `GET /cart`: resolves the owner via `CurrentUserProvider`
  (same convention as `CatController`/`CalendarController`), calls
  `CartService.getActiveCart(owner)`, renders `cart/view.html`. Empty
  active cart renders the P4-6 empty state instead of a blank list.
- **T5.2** — `GET /cart/dismissed`: calls `CartService.getDismissedItems
  (owner)`, renders the "restore" section/view required by Section 0's
  design note (can be a separate route+template or a toggled section
  within `view.html` — Dev's call, no architectural difference).
- **T5.3** — `POST /cart/items`: manual add (P4-4) — binds a minimal
  form (`name`), calls `CartService`/`CartItemService.add`, redirects back
  to `/cart`. Blank name rejected with a clear, on-brand error (mirrors
  existing form-validation conventions elsewhere in the app).
- **T5.4** — `POST /cart/{kind}/{ref}/restock`,
  `POST /cart/{kind}/{ref}/dismiss`, `POST /cart/{kind}/{ref}/restore`:
  `kind` distinguishes `system`/`manual`, `ref` is the `SupplyItemType`
  name or the `CartItem` id depending on `kind`. Restock accepts an
  optional date field (Section 0's backdating default: today, not in the
  future, both client- and server-side validated). All three redirect back
  to `/cart` (or `/cart/dismissed` for `restore`).
- **T5.5** — `cart/view.html`: lists active items (system + manual,
  visually distinguishable which is which per P4-4's AC), each with
  restock/dismiss/search actions; a manual-add form; a link to the
  dismissed-items view; the P4-6 empty state when the active list is
  empty. Restock and dismiss confirmations use witty, on-brand copy
  (P4-2's AC) — placeholder copy pending PO (see Open Items).
- **T5.6** — Add a "Supply Chain" (cart) link to
  `templates/fragments/nav.html`, alongside the existing "Calendar" link
  added in Phase 3.

## 6. Cross-cutting

- **T6.1** — Flyway migrations for `supply_tracker.dismissed_at` (T1.2)
  and the new `cart_item` table (T2.3) — can ship as one migration file or
  two, Dev's call, no functional difference (same note as Phase 2's T4.1).

---

## Open items — need PO/client confirmation before this is fully "done"

- **OI-1 (PO):** Exact on-brand microcopy is not written yet — the P4-6
  empty-state message, P4-2's restock confirmation copy, and the labeling
  that distinguishes system-generated from manually-added items on the
  cart view (P4-4's AC). Dev will build against placeholder copy; PO
  should confirm/replace final strings before this phase ships, same
  pattern as Phase 3's OI-1.

No other open items — the "removed" = "dismiss" interpretation (Section 0),
the search-assist mechanism (T4.1), and the P4-2 backdating default
(T5.4) are resolved above as Tech Lead judgment calls, not flagged, since
none carries a real coupling/multi-user tradeoff that would call for a
stop-and-wait per CLAUDE.md's decision protocol. The two decisions that
did meet that bar (manual item representation, dismiss persistence) were
already decided by the client and are recorded in
`docs/adr/0007-phase-4-cart-data-model.md`.

---

## Summary
25 tasks: 6 extending `supply` with dismiss/restore semantics, 5 building
the new `CartItem` entity, 5 in `CartService`'s merge layer, 2 for the
search-assist URL builder, 6 in `CartController`/templates, and 1
cross-cutting migration note — directly traceable to P4-1 through P4-6.
The architectural decisions behind this breakdown are recorded in
`docs/adr/0007-phase-4-cart-data-model.md`. One open item (OI-1, final
microcopy) remains for the PO; it does not block starting implementation.
Ready for handoff to Dev once OI-1 is confirmed or Dev proceeds with
placeholder copy pending PO review.
