# 0007: Phase 4 Cart Data Model — Manual Items and Dismiss Semantics

## Status
Accepted

## Context
Phase 4 (P4-1 through P4-6) turns Phase 2's supply reasoning into "The
Supply Chain" cart. Most of Phase 4 is read/write UI over state that
already exists (`SupplyReasoningService.computeHouseholdNeeds`,
`SupplyService.markRestocked`, both from ADR 0004). Two stories, though,
need genuinely new persisted state that ADR 0004 didn't anticipate:

1. **P4-4 (manual add):** the user can add "a custom item... with at least
   a name" for something "the app didn't think of." That's arbitrary free
   text, not a pick-from-4-values affordance over `SupplyItemType` — a real
   tension with ADR 0004's deliberately closed, coded enum catalog.
2. **P4-5 (dismiss):** the user can remove an item from the active cart
   "without it counting as restocked" — the AC is explicit that this must
   not touch `SupplyTracker.lastRestockedAt` — and explicitly leaves open
   whether a dismissed item should reappear on its next normal cadence
   cycle or stay dismissed indefinitely.

Both are shape questions for the data model, not just UI wiring, so they're
recorded together here, the same way ADR 0004 bundled several related
supply-model sub-decisions into one ADR.

One precedent worth naming and ruling out up front: Phase 1's
`VaccineType.OTHER` + `otherDescription` pattern (`VaccinationRecord`,
`VaccinationService`). That pattern works there because
`VaccinationRecord` is an **append-only history table** — no uniqueness
constraint, so multiple `OTHER` rows with different descriptions coexist
fine. `SupplyTracker` is structurally different: it's a **singleton
tracker per `(owner_id, item_type)`**, enforced by a DB unique constraint
(`uq_supply_tracker_owner_item`, `V4__supply_tracker.sql`). Reusing the
`OTHER` pattern on `SupplyTracker` would mean a user could only ever have
one manually-added item active at a time — adding "cat toy" then "brush"
would silently overwrite the first. This pattern does not transfer, and is
not treated as a viable option below.

## Options Considered

### Decision 1 — how manual cart items (P4-4) are represented

**Option A — Extend `SupplyItemType`'s scope**
Add more (or arbitrary) values to the enum to cover manually-added items.
- Rejected outright: contradicts "at least a name" (implies free text, not
  a fixed set) and reintroduces exactly what ADR 0004 avoided — a new item
  type meaning a code change and redeploy, for what's supposed to be a
  spontaneous one-off add.

**Option B — Loosen `SupplyTracker`'s constraint to allow multiple
`OTHER` rows with free text**
Drop/relax the `(owner_id, item_type)` unique constraint specifically for
an `OTHER` value, and let `SupplyTracker` carry a free-text field for that
case.
- Cons: special-cases one enum value with different multiplicity rules
  than the other three; re-couples cadence-less, reasoning-free manual
  items to the table that exists specifically to back
  `SupplyReasoningService`'s cadence math. One table ends up doing two
  conceptually different jobs.

**Option C — Separate `CartItem` entity, decoupled from `SupplyTracker`/
`SupplyItemType`/the reasoning engine**
New table: `cart_item (id, owner_id FK, name, status, dismissed_at
nullable, created_at, restocked_at nullable)`, owned exactly the same way
`SupplyTracker` and `Cat` are owned. Manual items never enter
`SupplyReasoningService` — there's no cadence rule for an arbitrary name,
so there's nothing for the reasoning engine to compute. The cart view
becomes a merge of two sources: `SupplyReasoningService`'s catalog-driven
output, plus `CartItemRepository`'s manual items for that owner.
- Pros: `reasoning` (already-accepted, ADR 0003) stays untouched and pure
  — no cadence-less rows leak into it. Low coupling: the only new
  complexity is a merge step in the cart-facing service, isolated from
  existing, tested logic. Cheap to extend later (e.g. adding a
  category/tag to manual items is an additive column).
- Cons: the cart-presentation layer now reads two different shapes
  (`DueSupplyItem` and `CartItem`) and must present them with consistent
  actions (restock/dismiss/search) — a real but small and isolated cost.

**Multi-user reasoning:** `CartItem` is owner-scoped exactly like
`SupplyTracker`/`Cat` — one row per item per user, no shared state, no
different concurrency story at higher user counts.

### Decision 2 — dismiss (P4-5) semantics and persistence

**Option A — Indefinite dismiss (persisted flag, cleared by an explicit
signal)**
Add a nullable `dismissedAt` to `SupplyTracker` and an equivalent field on
`CartItem`. Cart display filters out anything with `dismissedAt` set.
Cleared automatically by an actual restock (`markRestocked` /
`CartItem`'s restock action — unambiguous evidence the dismissal is moot),
or by an explicit "restore to cart" action.
- Pros: one new nullable column per entity; keeps a single source of truth
  for "is this actually due" — the real cadence math off `lastRestockedAt`
  is never bent to also model a suppression window.
- Cons: if a user dismisses something and never proactively restores it,
  it can stay silently suppressed even once genuinely needed again — a
  false-negative risk. Mitigated in the UI (a visible "restored items"
  affordance), not in the data model.

**Option B — Time-boxed snooze until the next cadence cycle**
`dismissedUntil = today + cadenceDays`, reusing the same cadence math a
restock would use, without touching the real `lastRestockedAt`.
- Cons: introduces a second, synthetic due-date clock alongside the real
  one. `SupplyReasoningService` — deliberately simple, stateless, no
  caching (ADR 0003) — would need to become aware of a suppression window
  it didn't previously know about, and that window must be recomputed
  correctly if household composition changes (e.g. cat count) while an
  item is dismissed. More moving parts and edge cases for a behavior
  that's functionally "quieter than restock, but still fabricating a
  timer."

**Multi-user reasoning:** both options are owner-scoped, single-row
updates — no different concurrency story from `lastRestockedAt` today
either way.

## Decision
**Decision 1: Option C** — a new `CartItem` entity in a new
`com.catagenda.cart` package, decoupled from `SupplyTracker`/
`SupplyItemType`/`SupplyReasoningService`.

**Decision 2: Option A** — indefinite dismiss via a nullable
`dismissedAt` column, added to both `SupplyTracker` (catalog-driven items)
and `CartItem` (manual items), cleared by an actual restock or an explicit
restore action.

```
User (existing)
   1                                1
   │ owns                           │ owns
   *                                *
SupplyTracker                    CartItem
(existing, ADR 0004               (new: id, owner_id FK, name,
 + new dismissed_at)               dismissed_at, restocked_at,
                                    created_at)
   │                                  │
   │ read by                         │ read by
   ▼                                  ▼
SupplyReasoningService          CartItemRepository
(unchanged — ADR 0003,          (no reasoning — manual
 pure, no cadence-less           items have no cadence rule)
 rows added)
   │                                  │
   └──────────────┬───────────────────┘
                   ▼
         CartService (new merge layer)
         combines both sources into one
         display list, consistent
         restock/dismiss/search actions
                   │
                   ▼
              Cart controller/view
```

## Consequences
- `SupplyReasoningService` and the rest of `reasoning` (ADR 0003) are
  untouched — the already-accepted, tested reasoning engine doesn't absorb
  cadence-less manual items or a suppression concept it wasn't designed
  for.
- The cart's read path is now a merge of two sources
  (`SupplyReasoningService.computeHouseholdNeeds` +
  `CartItemRepository`'s active rows) instead of one — a new `CartService`
  owns that merge and presents one consistent shape (with restock/dismiss/
  search actions available on both kinds of item) to the controller/view,
  the same "merge layer so the template doesn't type-branch" reasoning ADR
  0006 already established for the calendar.
- Adding richer manual-item features later (categories, quantities, tags)
  is an additive change to `CartItem` alone — it never requires touching
  `SupplyItemType`, `SupplyTracker`, or `SupplyReasoningService`.
- `dismissedAt` on `SupplyTracker` sits alongside `lastRestockedAt` as a
  second, independent field on the same row — not a reuse or overload of
  the restock timestamp, so P2-4's actual restock-cadence reasoning is
  unaffected by dismissal.
- Trade-off accepted: indefinite dismiss carries a real false-negative
  risk (a dismissed-and-forgotten item silently missing from the cart even
  once genuinely needed) that this ADR pushes to the UI layer to mitigate
  (a visible way to see/restore dismissed items), not to the data model.
- Both `SupplyTracker` and `CartItem` remain owner-scoped exactly like
  every other entity in this app (`Cat`, ADR 0001) — this holds unchanged
  at higher user counts; no shared/cross-user state is introduced by
  either decision. If Phase 8's future multi-owner layer ever needs shared
  households, that's the same additive `Household`-above-`User` posture
  ADR 0004 already flagged, not a rework of `CartItem`.
