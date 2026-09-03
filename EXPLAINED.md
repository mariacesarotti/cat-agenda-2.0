# Concepts Explained

Tracks Java/Spring concepts already introduced to the client (per
CLAUDE.md's teaching requirement), so later work doesn't re-explain the
same thing. Phase 1 predates this file; entries below start from Phase 2.

- Injected `Clock` for testable "now" — 2026-09-02
- Java `record` (immutable DTOs) — 2026-09-02
- `java.time.Period` for calendar-based date math — 2026-09-02
- Streams (`.stream().max(...)`, `.filter(...)`) — 2026-09-02
- `java.time.YearMonth` for month-granularity values — 2026-09-02
- Spring MVC `@PathVariable` binding directly to an enum — 2026-09-02
- Thymeleaf/SpringEL selection expressions (`collection.?[...]`) and `#vars` — 2026-09-02
- `ReflectionTestUtils.setField` for JPA-generated-id test fixtures — 2026-09-02
- Enum constants with per-constant fields/constructors — 2026-09-02
- `RedirectAttributes`/flash attributes (Post/Redirect/Get) — 2026-09-02
- SpringEL `T()` operator for static method calls in Thymeleaf — 2026-09-02
