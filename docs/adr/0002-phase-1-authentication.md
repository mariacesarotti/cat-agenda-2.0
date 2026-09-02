# 0002: Phase 1 Authentication — Spring Security with Server-Side Sessions

## Status
Accepted

## Context
Phase 1 requires real (not stubbed) registration, login, and logout (P1-1,
P1-2, P1-3), with a minimum password security standard, non-revealing
invalid-credential errors, and session handling that correctly gates
logged-in-only pages after logout. The app is server-rendered with Thymeleaf
(no separate frontend framework), which shapes which auth patterns are a
natural fit.

## Options Considered

**Option A — Spring Security, server-side sessions (classic form login)**
Spring Security's built-in username/password form login, backed by an
`HttpSession` (session ID cookie in the browser, session state on the
server). Passwords hashed with BCrypt (Spring Security's default encoder).
- Pros: idiomatic default for a Thymeleaf server-rendered Spring Boot app;
  CSRF protection, session-fixation protection, "redirect unauthenticated
  users to login" (needed for P1-3), and non-revealing invalid-login
  behavior are provided by the framework rather than hand-built; BCrypt
  hashing is industry standard and built in; sets up Phase 5 (Google OAuth)
  to plug into the same Spring Security foundation later.
- Cons: sessions are stateful — by default held in server memory, which
  doesn't transparently work across multiple server instances without
  externalizing session storage (e.g. Spring Session + Redis).

**Option B — Hand-rolled auth**
Custom login form/controller, manual password comparison, manual session
attribute handling, no Spring Security.
- Pros: appears simpler at first glance, no framework configuration to learn.
- Cons: re-implements password hashing, CSRF protection, and session-fixation
  handling by hand — all easy to get subtly wrong (e.g. weak/no password
  hashing, missing CSRF protection on forms that change state); teaches
  patterns real Spring Boot teams don't use in production; no reusable
  foundation for Phase 5's OAuth needs.

**Option C — Token-based auth (JWT), stateless**
Spring Security configured to issue and validate JWTs instead of using
sessions.
- Pros: stateless by design, scales horizontally with no shared session
  store.
- Cons: solves a scaling problem this app doesn't have yet (no separate
  frontend/mobile client); adds token expiry/refresh and secure client-side
  storage complexity; works against, rather than with, a cookie-based
  server-rendered Thymeleaf app. Would be over-engineering for the current
  architecture.

## Decision
Option A: Spring Security with server-side sessions and BCrypt password
hashing.

```
Browser                         Spring Boot app
  │  POST /register (email, pw)      │
  ├──────────────────────────────────▶ UserService: check email uniqueness,
  │                                     BCrypt-hash password, save User
  │  POST /login (email, pw)          │
  ├──────────────────────────────────▶ Spring Security AuthenticationManager
  │                                     validates against UserDetailsService
  │  ◀── Set-Cookie: JSESSIONID ───────┤ HttpSession created server-side
  │  GET /cats (with session cookie)  │
  ├──────────────────────────────────▶ SecurityFilterChain checks session →
  │                                     authenticated → CatController
  │  POST /logout                     │
  ├──────────────────────────────────▶ Session invalidated
  │  GET /cats (after logout)         │
  ├──────────────────────────────────▶ Not authenticated → redirect /login
```

## Consequences
- Registration, login, logout, and access control to logged-in-only pages
  are implemented using framework-provided, well-tested mechanisms rather
  than custom security code, which reduces the risk of common auth mistakes.
- Password storage security (BCrypt) and CSRF protection on forms come from
  configuration rather than being written and maintained by hand.
- Leaves a known, well-understood path to horizontal scale later: if the app
  ever runs on multiple server instances, sessions can be externalized via
  Spring Session (e.g. backed by Redis) without changing the auth model
  itself — an additive infrastructure change, not a rewrite.
- Establishes the Spring Security foundation that Phase 5 (Google Calendar
  OAuth) will extend, rather than requiring a second, unrelated auth
  mechanism later.
- Introduces Spring Security's filter chain / `UserDetailsService` /
  `PasswordEncoder` configuration as new concepts for Dev to learn when
  implementing this — to be explained inline in the implementation task per
  CLAUDE.md's teaching requirement.
