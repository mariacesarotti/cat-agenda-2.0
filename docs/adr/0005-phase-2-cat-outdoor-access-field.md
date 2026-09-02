# 0005: Cat Outdoor Access Field (added during Phase 2)

## Status
Accepted

## Context
Resolving Phase 2's open items required a piece of data Phase 1's `Cat`
entity doesn't have: whether a cat has outdoor access. Two Phase 2 rules
depend on it directly:
- **S3 (vaccine reasoning):** FeLV boosters are only clinically indicated
  for cats with ongoing outdoor exposure — an indoor-only cat shouldn't be
  flagged as "needed"/"due" for FeLV the way an outdoor cat should.
- **S1 (supply reasoning):** flea/tick prevention is only relevant for cats
  with outdoor exposure.

The client decided to add this field now, during Phase 2, rather than defer
it — even though the current scope only needs it for due-date reasoning —
specifically to avoid a second migration touching the same entity later.
This extends the `Cat` entity that ADR 0001 established in Phase 1, so it's
recorded here as an addendum to that decision rather than folded silently
into implementation.

This is a small, additive schema change (one nullable column, following an
already-established pattern), not a decision with real competing
architectural tradeoffs — so unlike the stop-and-wait decisions in ADR
0003/0004, this one is a Tech Lead call within normal remit (the client
already decided to add the field; this ADR documents shape and defaults).

## Options Considered

**Option A — nullable `Boolean outdoorAccess` on `Cat`**
Same pattern as the existing `spayedNeutered` field: a nullable Boolean,
optional at creation, editable later via the existing edit flow (P1-7).
- Pros: consistent with the established optionality pattern for `age`,
  `sex`, `spayedNeutered` (ADR 0001 / Phase 1 T2.4); directly matches the
  binary trigger described in both S1 and S3 (has outdoor access, or
  doesn't); no new ceremony.
- Cons: `null` and `false` both end up meaning "don't apply outdoor-only
  rules" unless reasoning code is explicit about treating them the same —
  already true of how the codebase treats other optional Booleans, so not a
  new class of problem.

**Option B — three-state `Lifestyle` enum (`INDOOR`, `OUTDOOR_ACCESS`,
`UNKNOWN`)**
- Pros: makes "unknown" an explicit value instead of overloading `null`.
- Cons: no rule in S1 or S3 actually needs a third state beyond "confirmed
  outdoor access" vs. "not confirmed" — this is more ceremony than the
  current requirements call for.

**Option C — historical lifestyle tracking (a dated record, like
`VaccinationRecord`)**
- Pros: would be correct if a cat's past vaccine relevance depended on its
  lifestyle *at the time* a vaccine was administered.
- Cons: no requirement asks for this; Phase 2 only needs lifestyle as a
  current-state input to today's reasoning, not a history. Meaningful added
  complexity for a need that doesn't exist yet.

## Decision
Option A: add a nullable `Boolean outdoorAccess` column to `Cat`, following
the same optional-field convention as `spayedNeutered`.

- **Migration:** existing cats get `outdoor_access = NULL` (unset), not a
  default of `false` — an unset value is meaningfully different from "known
  to be indoor-only," and forcing a default would silently assert something
  nobody confirmed.
- **Reasoning treatment of `null`:** treated the same as `false` — outdoor-
  only rules (FeLV due-reasoning, flea/tick supply flagging) only activate
  on an explicit `true`. This is a deliberate asymmetry against how unknown
  *age* is handled in the checkup cadence rule (S2), where "unknown" rolls
  forward to the more cautious/frequent case. The two "unknown" defaults
  aren't the same because the underlying rules aren't the same kind of
  risk: senior-cadence checkups err toward *more* monitoring for an
  unconfirmed-age adult cat, which is low-cost and clinically reasonable.
  Defaulting an *unconfirmed* lifestyle to "assume outdoor" would instead
  produce false-positive FeLV/flea-tick flags for what are very likely
  indoor cats whose owner simply hasn't filled in a new field yet — a worse
  failure mode for a brand-new field with no data yet.

## Consequences
- Extends ADR 0001's `Cat` entity additively — one new nullable column, no
  change to existing columns, relationships, or ownership scoping.
- Requires a Flyway migration altering the existing `cat` table (tracked as
  a Phase 2 task in `docs/phase-2-tech-tasks.md`).
- `CatForm`, the cat creation/edit templates (P1-4/P1-7), and the profile
  view (P1-6) need a small, on-brand addition for this field — it must be
  settable somewhere for it to ever become non-null. Treated as a small
  extension of existing Phase 1 screens, not a new feature.
- Establishes a second nullable-lifestyle-style Boolean on `Cat`, keeping
  the "optional at creation, editable later, on-brand 'not set yet' empty
  state" convention consistent across the entity rather than introducing a
  one-off pattern for this field.
- If a future rule ever needs more than "confirmed outdoor access or not"
  (e.g. distinguishing supervised outdoor time from free-roaming), that
  would mean revisiting this toward Option B — additive, not a rework of
  this column.
