# CatAgenda

> Your cats have an agenda. You just execute it.

CatAgenda is a cat care manager built with Java 21 and Spring Boot. It tracks each cat's health history (vaccinations, checkups, conditions), works out what is due next, turns that into a calendar and a shopping list, and syncs care events to Google Calendar.

The premise is a running gag: the cats are quietly plotting world domination, and the human is just the assistant keeping the operation running. The joke lives only in the UI copy. The data model, architecture and code are kept deliberately serious.

## Why this project exists

I built CatAgenda to learn Java and Spring Boot, and to practice system design on something bigger than a tutorial: data modeling, API design, scalability reasoning and writing Architecture Decision Records.

To get closer to a real team setting, I set up a **Scrum team of AI agents** with Claude Code (Product Owner, Tech Lead, Developer and Scrum Master) and acted as the client. The agents write stories, plan sprints, propose designs and implement them, but **every architectural decision is mine**: the Tech Lead must present 2 to 3 real alternatives with trade-offs and wait for my choice before anything gets built. See [How this was built](#how-this-was-built).# CatAgenda

> Your cats have an agenda. You just execute it.

CatAgenda is a cat care manager built with Java 21 and Spring Boot. It keeps each cat's health history (vaccinations, checkups, conditions), works out what is due next, turns that into a calendar and a shopping list, and syncs care events to Google Calendar.

The running gag is that the cats are plotting world domination and the human is just the assistant keeping things running. The joke stays in the UI copy; the data model, architecture and code are serious.

## Why this project exists

I built CatAgenda to learn Java and Spring Boot and to practice system design on something bigger than a tutorial: data modeling, API design, thinking about scale and writing Architecture Decision Records.

To get closer to how a real team works, I set up a Scrum team of AI agents in Claude Code (Product Owner, Tech Lead, Developer and Scrum Master) and played the client. The agents write stories, plan sprints, propose designs and implement them. Architecture decisions are mine: the Tech Lead has to bring 2 or 3 real alternatives with their trade-offs and wait for my call before anything gets built. More in [How this was built](#how-this-was-built).

## Features

| Phase | Feature | Status |
|---|---|---|
| 1 | Accounts, cat profiles, conditions, vaccination and checkup history | Done |
| 2 | Reasoning engine: what is due (vaccines, checkups) and which supplies are running low | Done |
| 3 | Internal calendar view of upcoming care events | Done |
| 4 | "The Supply Chain": shopping cart built from supply needs, plus manual items | Done |
| 5 | Google Calendar sync over OAuth2, with encrypted token storage and scheduled sync | Done |
| 6 | Spay/neuter scheduling | Done |
| 7 | Cat social profiles and posts | Planned |
| 8 | Multi-owner social layer | Future (already accounted for in the data model) |

## Tech stack

- Java 21, Spring Boot 3.3
- Spring Security (form login, plus OAuth2 Client for Google Calendar)
- Spring Data JPA / Hibernate, PostgreSQL, Flyway migrations
- Thymeleaf server-rendered views
- JUnit 5 and Mockito (147 unit tests)
- Maven

## Architecture highlights

- Package by feature (`cat`, `reasoning`, `calendar`, `cart`, `googlesync`, `spayneuter`, `user`), with thin controllers and the business logic in services.
- Ownership scoped from day one. Every cat belongs to a user through a required foreign key, and every query goes through that ownership. The multi-user social features don't exist yet, but the schema won't need rework when they do ([ADR 0001](docs/adr/0001-phase-1-data-model.md)).
- Computed, not stored. Due dates and calendar events are derived on read from the care history, so there's no cached "next due" value that can drift out of sync ([ADR 0003](docs/adr/0003-phase-2-reasoning-engine-architecture.md), [ADR 0006](docs/adr/0006-phase-3-calendar-event-view-model.md)).
- Testable time. An injected `Clock` makes all the date logic deterministic in tests.
- Flyway owns the schema. Hibernate runs with `ddl-auto: validate` and never alters tables.
- Google Calendar sync uses the narrowest OAuth scope, encrypts access and refresh tokens at rest with a JPA `AttributeConverter`, and keeps track of pushed events so a re-sync updates them instead of creating duplicates ([ADR 0008](docs/adr/0008-phase-5-google-calendar-sync-architecture.md)).

## Architecture Decision Records

Each ADR covers the context, the options I considered, what I decided and the consequences, including how the decision would hold up if the app became a multi-user product.

| # | Decision |
|---|---|
| [0001](docs/adr/0001-phase-1-data-model.md) | Phase 1 data model |
| [0002](docs/adr/0002-phase-1-authentication.md) | Authentication |
| [0003](docs/adr/0003-phase-2-reasoning-engine-architecture.md) | Reasoning engine architecture |
| [0004](docs/adr/0004-phase-2-supply-data-model.md) | Supply data model |
| [0005](docs/adr/0005-phase-2-cat-outdoor-access-field.md) | Cat outdoor access field |
| [0006](docs/adr/0006-phase-3-calendar-event-view-model.md) | Calendar event view model |
| [0007](docs/adr/0007-phase-4-cart-data-model.md) | Cart data model |
| [0008](docs/adr/0008-phase-5-google-calendar-sync-architecture.md) | Google Calendar sync architecture |
| [0009](docs/adr/0009-phase-6-spay-neuter-scheduling.md) | Spay/neuter scheduling |

## How this was built

The development process is part of the project. It runs on Claude Code with custom subagents and skills, all versioned in this repo.

```
.claude/
├── agents/
│   ├── product-owner.md   # writes user stories and acceptance criteria (BACKLOG.md)
│   ├── scrum-master.md    # organizes stories into sprints (SPRINT.md)
│   ├── tech-lead.md       # breaks stories into tasks, proposes architecture options
│   └── dev.md             # implements tasks and writes tests
└── skills/
    ├── spring-boot-conventions/   # consistent layering and structure
    ├── java-concept-explainer/    # explains each new Java/Spring concept once
    └── git-commit-conventions/    # Conventional Commits
CLAUDE.md                          # shared team context, rules and decision protocol
```

Each phase goes like this:

1. The Product Owner writes stories with acceptance criteria in [`BACKLOG.md`](BACKLOG.md), including the tone for user-facing copy.
2. The Scrum Master groups them into sprints in [`SPRINT.md`](SPRINT.md).
3. The Tech Lead breaks the phase into technical tasks ([`docs/`](docs)) and lays out alternatives with trade-offs for every non-trivial decision.
4. I pick one, and it gets recorded as an ADR.
5. The Dev implements and tests it. Any Java or Spring concept that shows up for the first time gets explained and logged in [`EXPLAINED.md`](EXPLAINED.md).

What I wanted to practice here was the work around the code: turning a vague idea into requirements, weighing trade-offs, deciding, and writing the decision down so it still makes sense months later.

## Running locally

Requirements: Java 21, Maven, PostgreSQL.

```bash
# 1. Create the database and user
createuser catagenda --pwprompt
createdb catagenda --owner=catagenda

# 2. Set the DB password in src/main/resources/application.yml

# 3. Run (Flyway creates the schema on startup)
mvn spring-boot:run
```

The app runs at `http://localhost:8080`.

Google Calendar sync is optional. To turn it on, set:

```bash
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
export GOOGLE_TOKEN_ENCRYPTION_KEY=...   # Base64 key for encrypting tokens at rest
```

Without them the app still starts, and everything else works.

Run the tests:

```bash
mvn test
```

## Author

Maria Cesarotti · Software developer (Java, Clojure) · [GitHub](https://github.com/mariacesarotti)

## Features

| Phase | Feature | Status |
|---|---|---|
| 1 | Accounts, cat profiles, conditions, vaccination and checkup history | Done |
| 2 | Reasoning engine: what is due (vaccines, checkups) and which supplies are running low | Done |
| 3 | Internal calendar view of upcoming care events | Done |
| 4 | "The Supply Chain": shopping cart built from supply needs, plus manual items | Done |
| 5 | Google Calendar sync over OAuth2, with encrypted token storage and scheduled sync | Done |
| 6 | Spay/neuter scheduling | Done |
| 7 | Cat social profiles and posts | Planned |
| 8 | Multi-owner social layer | Future (already accounted for in the data model) |

## Tech stack

- **Java 21**, **Spring Boot 3.3**
- **Spring Security** (form login, plus OAuth2 Client for Google Calendar)
- **Spring Data JPA / Hibernate**, **PostgreSQL**, **Flyway** migrations
- **Thymeleaf** server-rendered views
- **JUnit 5** and **Mockito** (147 unit tests)
- **Maven**

## Architecture highlights

- **Package by feature** (`cat`, `reasoning`, `calendar`, `cart`, `googlesync`, `spayneuter`, `user`), with thin controllers and business logic in services.
- **Ownership-scoped from day one.** Every cat belongs to a user through a required foreign key, and every query goes through that ownership. Multi-user social features are not built yet, but the schema will not need rework when they are ([ADR 0001](docs/adr/0001-phase-1-data-model.md)).
- **Computed, not stored.** Due dates and calendar events are derived on read from the care history, so there is no cached "next due" value to drift out of sync ([ADR 0003](docs/adr/0003-phase-2-reasoning-engine-architecture.md), [ADR 0006](docs/adr/0006-phase-3-calendar-event-view-model.md)).
- **Testable time.** An injected `Clock` makes all date logic deterministic in tests.
- **Schema owned by Flyway.** Hibernate runs with `ddl-auto: validate` and never alters tables.
- **External integration done carefully.** Google Calendar sync uses the narrowest OAuth scope, encrypts access and refresh tokens at rest with a JPA `AttributeConverter`, and tracks pushed events so re-syncs update instead of duplicating ([ADR 0008](docs/adr/0008-phase-5-google-calendar-sync-architecture.md)).

## Architecture Decision Records

Each non-trivial decision is documented with context, the options considered, the decision and its consequences, including how it would hold up if the app grew into a multi-user product.

| # | Decision |
|---|---|
| [0001](docs/adr/0001-phase-1-data-model.md) | Phase 1 data model |
| [0002](docs/adr/0002-phase-1-authentication.md) | Authentication |
| [0003](docs/adr/0003-phase-2-reasoning-engine-architecture.md) | Reasoning engine architecture |
| [0004](docs/adr/0004-phase-2-supply-data-model.md) | Supply data model |
| [0005](docs/adr/0005-phase-2-cat-outdoor-access-field.md) | Cat outdoor access field |
| [0006](docs/adr/0006-phase-3-calendar-event-view-model.md) | Calendar event view model |
| [0007](docs/adr/0007-phase-4-cart-data-model.md) | Cart data model |
| [0008](docs/adr/0008-phase-5-google-calendar-sync-architecture.md) | Google Calendar sync architecture |
| [0009](docs/adr/0009-phase-6-spay-neuter-scheduling.md) | Spay/neuter scheduling |

## How this was built

The development process is part of the project. It runs on Claude Code with custom subagents and skills, all versioned in this repo.

```
.claude/
├── agents/
│   ├── product-owner.md   # writes user stories and acceptance criteria (BACKLOG.md)
│   ├── scrum-master.md    # organizes stories into sprints (SPRINT.md)
│   ├── tech-lead.md       # breaks stories into tasks, proposes architecture options
│   └── dev.md             # implements tasks and writes tests
└── skills/
    ├── spring-boot-conventions/   # consistent layering and structure
    ├── java-concept-explainer/    # explains each new Java/Spring concept once
    └── git-commit-conventions/    # Conventional Commits
CLAUDE.md                          # shared team context, rules and decision protocol
```

**The workflow, phase by phase:**

1. The Product Owner writes stories with acceptance criteria in [`BACKLOG.md`](BACKLOG.md), including tone requirements for user-facing copy.
2. The Scrum Master groups them into sprints in [`SPRINT.md`](SPRINT.md).
3. The Tech Lead breaks each phase into technical tasks ([`docs/`](docs)) and, for every non-trivial decision, presents alternatives with trade-offs.
4. **I choose.** The decision is then recorded as an ADR.
5. The Dev implements and tests. Every new Java or Spring concept is explained the first time it shows up and logged in [`EXPLAINED.md`](EXPLAINED.md).

The point was not to have AI write an app for me. It was to practice the parts of engineering that matter most beyond writing code: framing requirements, weighing trade-offs, making decisions and documenting them.

## Running locally

Requirements: Java 21, Maven, PostgreSQL.

```bash
# 1. Create the database and user
createuser catagenda --pwprompt
createdb catagenda --owner=catagenda

# 2. Set the DB password in src/main/resources/application.yml

# 3. Run (Flyway creates the schema on startup)
mvn spring-boot:run
```

The app runs at `http://localhost:8080`.

Google Calendar sync is optional. To enable it, set:

```bash
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
export GOOGLE_TOKEN_ENCRYPTION_KEY=...   # Base64 key for encrypting tokens at rest
```

Without these, the app still starts normally and every other feature works.

Run the tests:

```bash
mvn test
```

## Author

**Maria Cesarotti** · Software developer (Java, Clojure) · [GitHub](https://github.com/mariacesarotti)
