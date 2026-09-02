# Sprint Plan

Maintained by the Scrum Master. This organizes BACKLOG.md into sequential,
learning-sized sprints. CatAgenda is a solo-developer learning project, so
sprints here are about breaking work into coherent, dependency-respecting
chunks — not about optimizing team throughput or fitting a fixed velocity.

Sprints are meant to be worked in order. Each sprint's goal describes the
capability that exists once it's done. Status is tracked per sprint below;
everything starts at "not started" since planning has just begun.

How to read this doc: when a sprint is picked up, the Tech Lead breaks its
stories into technical tasks; this doc only tracks sprint-level scope and
status, not task-level detail.

---

## Sprint 1 — Account Basics
**Status:** Not started
**Goal:** A user can create an account, log in, and log out. This is the
security foundation everything else (cats belong to a user) sits on top of.
**Stories:** P1-1, P1-2, P1-3
**Dependencies:** None — first sprint.

---

## Sprint 2 — Cat Profile CRUD
**Status:** Not started
**Goal:** A logged-in user can add, view, edit, and remove cat profiles with
core details (name, age, sex, spay/neuter status). By the end of this sprint
there's a working cat list and profile view, owned per-user.
**Stories:** P1-4, P1-5, P1-6, P1-7, P1-8
**Dependencies:** Sprint 1 (requires accounts/login to own cats against).

---

## Sprint 3 — Care History Detail
**Status:** Not started
**Goal:** Cat profiles can hold known conditions and full vaccination history
(including booster records per vaccine type). This completes Phase 1's data
backbone — everything the reasoning engine needs to compute against exists
after this sprint.
**Stories:** P1-9, P1-10, P1-11
**Dependencies:** Sprint 2 (extends an existing cat profile).

---

## Sprint 4 — Reasoning Engine
**Status:** Not started
**Goal:** The system can compute, per cat and per household: next vet
checkup due date, next due date per vaccine, needed supply categories, and a
combined "what's due" data set. Little to no new UI — this is the engine that
Phase 3 (calendar) and Phase 4 (cart) will consume.
**Stories:** P2-1, P2-2, P2-3, P2-4, P2-5
**Dependencies:** Sprint 3 (needs conditions + full vaccination history, not
just Sprint 2's core fields, to compute accurately).

---

## Sprint 5 — Internal Calendar
**Status:** Not started
**Goal:** Users can browse a calendar of upcoming and overdue care events,
filter it by cat, and see an on-brand empty state when nothing's due.
**Stories:** P3-1, P3-2, P3-3, P3-4, P3-5
**Dependencies:** Sprint 4 (calendar is a view over P2-5's combined "due
soon" output; cannot be built before that output exists).

---

## Sprint 6 — Shopping Cart ("The Supply Chain")
**Status:** Not started
**Goal:** Users can see supply items needing restock, mark items restocked,
manually add or dismiss items, get help finding a generic product to search
for, and see an on-brand empty state when fully stocked.
**Stories:** P4-1, P4-2, P4-3, P4-4, P4-5, P4-6
**Dependencies:** Sprint 4 (cart is built on P2-3/P2-4's supply reasoning
and restock cadence, not on Sprint 5). Can technically run in parallel with
Sprint 5 if desired, since neither Phase 3 nor Phase 4 depends on the other —
noting this as a sequencing option, not a requirement to parallelize.

---

## Sprint 7 — Google Calendar Sync
**Status:** Not started
**Goal:** Users can connect a Google account and push their computed care
events to Google Calendar, with updates staying in sync, disconnect support,
and clear failure notification if sync breaks.
**Stories:** P5-1, P5-2, P5-3, P5-4, P5-5
**Dependencies:** Sprint 5 (syncs events from the Phase 3 internal calendar
— cannot be built before there's a calendar of events to sync).

---

## Sprint 8 — Spay/Neuter Scheduling
**Status:** Not started
**Goal:** Users can flag intent to spay/neuter a cat, schedule and reschedule
an appointment, mark it complete, and get reminded if intent is flagged but
nothing's scheduled yet — fully integrated into the due-soon reasoning,
calendar, and sync.
**Stories:** P6-1, P6-2, P6-3, P6-4
**Dependencies:** Sprint 3 (extends the Phase 1 spay/neuter status field),
Sprint 4 (P6-4 feeds into P2-5's "what's due" output), Sprint 5 (P6-2
appointments appear on the Phase 3 calendar), Sprint 7 (P6-2 appointments
also feed Phase 5 sync). This sprint sits behind all four prior feature
sprints, not just the immediately preceding one.

---

## Sprint 9 — Cat Social Profiles & Posts
**Status:** Not started
**Goal:** Each cat has a public-facing "propaganda page" — bio, profile
photo, and a photo-post feed the owner can create, edit, and delete — with
on-brand empty states throughout. Private to the owner for now (no Phase 8
cross-owner visibility).
**Stories:** P7-1, P7-2, P7-3, P7-4, P7-5
**Dependencies:** Sprint 2 (needs a cat to attach a profile/feed to). Does
NOT depend on Sprints 4–8 — this phase is presentation/content only and
doesn't touch care reasoning. Note: P7-2 (profile photo) and P7-3 (photo
posts) both need an image storage architecture decision — per CLAUDE.md this
requires the Tech Lead's full alternatives-plus-ADR treatment, and per the
project's decision-making protocol the client must make an explicit choice
before implementation starts on those two stories. Flagging this now so it
doesn't stall the sprint once picked up.

---

## Summary

| Sprint | Phase | Stories | Count |
|---|---|---|---|
| 1 | Phase 1 | P1-1 – P1-3 | 3 |
| 2 | Phase 1 | P1-4 – P1-8 | 5 |
| 3 | Phase 1 | P1-9 – P1-11 | 3 |
| 4 | Phase 2 | P2-1 – P2-5 | 5 |
| 5 | Phase 3 | P3-1 – P3-5 | 5 |
| 6 | Phase 4 | P4-1 – P4-6 | 6 |
| 7 | Phase 5 | P5-1 – P5-5 | 5 |
| 8 | Phase 6 | P6-1 – P6-4 | 4 |
| 9 | Phase 7 | P7-1 – P7-5 | 5 |

41 stories across 9 sprints. All sprints are currently **not started** —
Sprint 1 is next up.
