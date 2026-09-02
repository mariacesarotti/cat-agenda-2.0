# Phase 1 — Technical Task Breakdown

Prepared by the Tech Lead from BACKLOG.md's Phase 1 stories (P1-1 through
P1-11), following the decisions recorded in:
- `docs/adr/0001-phase-1-data-model.md`
- `docs/adr/0002-phase-1-authentication.md`

Package-by-feature layout per `.claude/skills/spring-boot-conventions`:
`com.catagenda.user`, `com.catagenda.cat`. `Condition` and
`VaccinationRecord` live inside the `cat` package rather than as their own
top-level features — they have no independent meaning outside a `Cat` (no
controller/list view of their own), so splitting them out would add package
overhead without a real feature boundary. This is a folder-structure
convention call within Tech Lead's normal remit (not a stop-and-wait
architecture decision).

Status: not started. No implementation/scaffolding has been written yet —
this is the task list only. All open items previously flagged for
client/PO confirmation (T2.4, T2.9, T4.1) have been resolved and are baked
into the tasks below. Ready for handoff to Dev.

---

## 0. Project setup (prerequisite for everything below)

- **T0.1** — Initialize Maven project: Spring Boot 3.x, Java 21. Dependencies:
  Spring Web, Spring Data JPA, Spring Security, PostgreSQL driver, Thymeleaf,
  Validation, Spring Boot DevTools; test scope: `spring-boot-starter-test`
  (JUnit 5 + Mockito included).
- **T0.2** — Configure PostgreSQL datasource for local dev
  (`application.yml`/`.properties`).
- **T0.3** — Adopt Flyway for schema migrations instead of Hibernate
  `ddl-auto`. Rationale (Tech Lead call, not a client decision point): Flyway
  gives versioned, reviewable SQL migration files — useful for tracking how
  the schema evolves phase over phase, and a good habit to build even solo,
  since `ddl-auto: update` can silently diverge from what's actually
  reviewable/reproducible.

## 1. `user` feature (P1-1, P1-2, P1-3)

- **T1.1** — `User` entity: `id`, `email` (unique, not null), `passwordHash`,
  `createdAt`.
- **T1.2** — `UserRepository extends JpaRepository<User, Long>` with
  `findByEmail`.
- **T1.3** — Spring Security configuration: `SecurityFilterChain` bean,
  `PasswordEncoder` bean (BCrypt), `UserDetailsService` implementation
  backed by `UserRepository`, authorization rules (public: register/login;
  everything else requires authentication), logout endpoint config.
  *(First use of Spring Security config — explain the filter chain concept
  inline when implemented.)*
- **T1.4** — Registration flow (P1-1): controller + form template,
  `UserService.register()` — validates email not already in use, enforces
  minimum password standard, hashes password, saves `User`. On success,
  either auto-login or redirect to login per acceptance criteria — confirm
  which with PO/Tech Lead if not already decided; on-brand copy for success
  and for "email already in use" error.
- **T1.5** — Login flow (P1-2): Spring Security form-login pointing at a
  custom on-brand login template; generic invalid-credentials message (must
  not reveal whether email or password was wrong — Spring Security's default
  behavior already supports this, just needs the on-brand copy wired in);
  successful login lands on cat list/dashboard.
- **T1.6** — Logout flow (P1-3): visible logout action while authenticated;
  confirm session invalidation on logout; verify protected routes redirect
  unauthenticated requests to login (covered by T1.3's authorization rules,
  but needs an explicit test).
- **T1.7** — `UserService` unit tests: registration with duplicate email,
  registration with weak password, successful registration hashes the
  password (not stored in plaintext).

## 2. `cat` feature — core profile (P1-4, P1-5, P1-6, P1-7, P1-8)

- **T2.1** — `Cat` entity: `id`, `owner` (`@ManyToOne` to `User`, not null),
  `name` (required, not null), `age`, `sex`, `spayedNeutered` (all three
  nullable/optional — see T2.4), `createdAt`.
  *(First use of `@ManyToOne`/`@OneToMany` — explain what this maps to at
  the database level when implemented, per ADR 0001.)*
- **T2.2** — `CatRepository extends JpaRepository<Cat, Long>` with
  `findByOwner(User owner)` and `findByIdAndOwner(Long id, User owner)`.
- **T2.3** — `CatService`: create/list/get/update/delete, **all scoped to
  the authenticated owner** — no method should look up a cat by ID alone
  without also checking ownership. This is the core security convention for
  this feature and should be consistent across every method from the start.
- **T2.4** — **Resolved (client decision):** only `name` is required at cat
  creation. `age`, `sex`, `spayedNeutered` are all optional and can be filled
  in later via the edit flow (P1-7). Implication for Dev: the creation form
  (T2.5) only validates `name`; the entity/DB columns for `age`, `sex`,
  `spayedNeutered` must be nullable (not `NOT NULL`); and the profile view
  (T2.7) needs an on-brand "not set yet" treatment for any of these fields
  left blank, consistent with the empty-state pattern already required
  elsewhere on the profile. Flag to Phase 2 planning: the reasoning engine
  (P2-1/P2-2) must handle cats with missing age/sex/spay-status gracefully
  rather than assuming they're always populated — this is now a real case,
  not an edge case.
- **T2.5** — Add-cat flow (P1-4): form + controller + service call; new cat
  appears in list immediately; on-brand confirmation copy.
- **T2.6** — Cat list view (P1-5): shows name + quick-glance identifier per
  cat; on-brand empty state when the user has no cats yet; selecting a cat
  navigates to its profile.
- **T2.7** — Cat profile view (P1-6): displays name, age, sex,
  spayed/neutered status, known conditions, vaccination history; on-brand
  empty states per section when no data yet; link to edit.
- **T2.8** — Edit-cat flow (P1-7): form pre-filled with current values;
  validation with on-brand error messages; changes reflected immediately on
  profile view.
- **T2.9** — Remove-cat flow (P1-8): explicit confirm-before-delete step
  (cannot be triggered accidentally). **Resolved (client decision):** keep
  warm, non-comedic copy on this confirmation step specifically — confirmed
  as the deliberate, permanent exception to the app's usual witty tone,
  since this flow can correspond to a cat's death. Dev should treat this
  copy as a fixed exception, not something to make on-brand/punny later.
  Also: cascading removal of the cat's `VaccinationRecord` and `Condition`
  rows (via JPA cascade config or explicit service-level deletion — decide
  and document which when implementing); removed cat no longer appears in
  list.
- **T2.10** — `CatService` unit tests: ownership scoping (user A cannot
  fetch/edit/delete user B's cat), create/update/delete happy paths,
  validation failures.

## 3. `condition` sub-feature (P1-9)

- **T3.1** — `Condition` entity: `id`, `cat` (`@ManyToOne`), `name`, optional
  `notes`.
- **T3.2** — `ConditionRepository` with `findByCat`.
- **T3.3** — Add/edit/remove condition, wired into the cat profile view;
  on-brand empty state ("a clean bill of health... for now") when none
  logged.
- **T3.4** — Service unit tests for add/edit/remove, scoped through the
  parent cat's ownership (a condition can only be touched via a cat the
  requesting user owns).

## 4. `vaccination` sub-feature (P1-10, P1-11)

- **T4.1** — `VaccinationRecord` entity: `id`, `cat` (`@ManyToOne`),
  `vaccineType`, `dateAdministered`, `otherDescription` (nullable, see
  below).

  **Resolved (client decision):** `vaccineType` is a fixed Java enum, not
  free text:

  ```java
  public enum VaccineType {
      RABIES,
      FVRCP,       // combined FVR/calicivirus/panleukopenia — administered
                   // and tracked as a single vaccine in practice, so one
                   // enum value rather than three
      FELV,        // core for kittens under 1yr, non-core for adults —
                   // that distinction is a Phase 2 reasoning-rule concern,
                   // not a schema concern; the record itself is just FELV
      FIV,
      CHLAMYDIA,
      BORDETELLA,
      OTHER
  }
  ```

  Rationale: matches current AAHA/AAFP core/non-core feline vaccine
  guidelines (client-researched), and a fixed enum is what Phase 2 (P2-2)
  needs to reason over a defined "recommended vaccine set" per cat — much
  cleaner against a closed set of values than arbitrary free-text strings,
  which would make "has this cat ever had a rabies vaccine" a fuzzy string-
  matching problem instead of an exact one.

  `OTHER` needs a free-text companion field (`otherDescription`, nullable,
  only populated/required when `vaccineType == OTHER`) — Tech Lead call:
  without it, any vaccine outside the standard set (e.g. a regional or
  travel-specific vaccine a vet administers) would be recorded as an
  anonymous "OTHER" with no way to tell one apart from another on the
  profile view, which defeats the point of per-vaccine-type history tracking
  in P1-10. This mirrors a common "enum + escape hatch" pattern: keep the
  fixed set for anything Phase 2 needs to reason over automatically, and use
  the free-text field purely for display/record-keeping on values Phase 2
  won't have specific due-date rules for anyway.
- **T4.2** — `VaccinationRecordRepository` with `findByCat` and
  `findByCatAndVaccineType` (supports grouping/sorting by type on the
  profile view, and per-type due-date lookups in Phase 2).
- **T4.3** — Add-vaccination flow (P1-10): form + controller/service;
  supports multiple records of the same type per cat (booster history);
  on-brand empty state when none recorded; records grouped/sortable by type
  on the profile view.
- **T4.4** — Edit/delete-vaccination flow (P1-11): edit date or type on an
  existing record; delete with confirmation; changes reflected immediately.
- **T4.5** — Service unit tests: multiple records of same type persist
  independently (no accidental overwrite), edit/delete scoped through
  ownership, empty-state condition.

## 5. Cross-cutting

- **T5.1** — Consistent validation/error-handling convention: how field
  validation errors and service-level exceptions surface as on-brand
  Thymeleaf error messages, applied uniformly across registration, cat
  forms, condition forms, and vaccination forms (avoid ad hoc handling per
  form).
- **T5.2** — ~~Confirm final "required vs optional" and copy-tone decisions~~
  **Resolved.** All three items previously flagged here (T2.4 required
  fields, T2.9 tone exception, T4.1 vaccine enum) have been answered by the
  client and are baked into their respective tasks above. No open questions
  remain blocking Phase 1 implementation.

---

## Summary
26 tasks across setup, `user`, `cat`, `condition`, `vaccination`, and
cross-cutting concerns, directly traceable to P1-1 through P1-11. No
implementation has started. All three previously open items (required
fields at cat creation, the P1-8 tone exception, and the vaccine type enum)
are now resolved per client decision and reflected in T2.4, T2.9, and T4.1.
This document is finalized and ready for direct handoff to Dev.
