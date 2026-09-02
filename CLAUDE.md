# Project Context

## What this is
A web application built to learn Java through a realistic, team-style development process.
The "client" (project owner) is a human learning Java; the four agents (Product Owner,
Tech Lead, Dev, Scrum Master) simulate a Scrum team building the app for them.

## Tech stack
- Language: Java 21
- Framework: Spring Boot
- Templating: Thymeleaf (server-rendered views, no separate frontend framework)
- Database: PostgreSQL
- ORM: Spring Data JPA / Hibernate
- Build tool: Maven
- Testing: JUnit 5 + Mockito

## Conventions
- Standard Maven project layout (src/main/java, src/main/resources, src/test/java)
- Package by feature (e.g. com.project.user, com.project.order), not by layer
- REST/MVC controllers thin — business logic lives in service classes
- Every new feature ships with at least basic unit tests

## Team process
- Product Owner defines stories in BACKLOG.md
- Scrum Master organizes stories into sprints in SPRINT.md
- Tech Lead breaks stories into technical tasks and sets architectural direction
- Dev implements tasks and writes tests
- Since the client is learning Java, the Dev and Tech Lead agents should explain
  *why*, not just *what* — briefly note the Java/Spring concept being used when
  it's introduced for the first time (e.g. first time using @Autowired, explain
  what dependency injection is doing there).

## System design learning goal
The client is also using this project to study system design, specifically:
database/data modeling, API design, scalability reasoning, and ADR-writing practice.

For any non-trivial architectural decision (schema shape, API contract, how a
feature is structured, integration boundaries), the Tech Lead agent must:
1. Present 2-3 real alternatives, not just the chosen approach
2. Walk through tradeoffs: coupling, complexity, cost of changing later
3. Explicitly reason about how the decision would hold up if this grew from a
   single-user app into a multi-user product (even though multi-user is not
   in scope to build — this is a deliberate teaching exercise)
4. Record the decision as an ADR in /docs/adr/ (see format below)
5. Use a diagram (data flow / component boundaries) when it clarifies the tradeoff

ADR format (docs/adr/NNNN-title.md):
- Status (proposed/accepted/superseded)
- Context (what problem are we solving)
- Options considered (with pros/cons)
- Decision (what we picked and why)
- Consequences (what this makes easier/harder later)

## Project name & tone
The app is called CatAgenda. The premise is a running gag: the cats are 
secretly plotting world domination, and this app is framed as their "agenda" — 
the human is just the assistant executing their schedule (vet visits, feeding, 
supply runs) without realizing it's actually feline infrastructure.

- All user-facing copy (UI text, button labels, empty states, notifications, 
  error messages) should be witty, playful, and loaded with cat puns and 
  "world domination" framing — e.g. a shopping cart could be "The Supply Chain," 
  a calendar event for vaccines could be framed as "keeping the human minions 
  operational."
- This is copy/UX tone only — it should NOT affect data modeling, architecture, 
  or code structure, which stay serious and well-engineered. The joke lives in 
  the presentation layer, not the backend logic.
- Product Owner should write acceptance criteria that include tone/voice where 
  user-facing text is involved (e.g. "empty state copy should be witty and 
  on-brand," not literally scripting every pun in the backlog).

## Future scope (not building yet, but affects data model)
A future phase will add a social layer where multiple cat owners and their cats 
can interact (visibility, following, cross-owner interaction — like a small social 
network for cats/owners). Not being built now, but Phase 1's data model must treat 
cats as belonging to a user/owner from the start, since retrofitting that 
relationship later would mean reworking the schema. When the Tech Lead makes the 
Phase 1 data modeling decision, this future direction should be part of the 
"how would this hold up with more users" reasoning already required.
# Project Context

## What this is
A web application built to learn Java through a realistic, team-style development process.
The "client" (project owner) is a human learning Java; the four agents (Product Owner,
Tech Lead, Dev, Scrum Master) simulate a Scrum team building the app for them.

## Tech stack
- Language: Java 21
- Framework: Spring Boot
- Templating: Thymeleaf (server-rendered views, no separate frontend framework)
- Database: PostgreSQL
- ORM: Spring Data JPA / Hibernate
- Build tool: Maven
- Testing: JUnit 5 + Mockito

## Conventions
- Standard Maven project layout (src/main/java, src/main/resources, src/test/java)
- Package by feature (e.g. com.project.user, com.project.order), not by layer
- REST/MVC controllers thin — business logic lives in service classes
- Every new feature ships with at least basic unit tests

## Team process
- Product Owner defines stories in BACKLOG.md
- Scrum Master organizes stories into sprints in SPRINT.md
- Tech Lead breaks stories into technical tasks and sets architectural direction
- Dev implements tasks and writes tests
- Since the client is learning Java, the Dev and Tech Lead agents should explain
  *why*, not just *what* — briefly note the Java/Spring concept being used when
  it's introduced for the first time (e.g. first time using @Autowired, explain
  what dependency injection is doing there).

## System design learning goal
The client is also using this project to study system design, specifically:
database/data modeling, API design, scalability reasoning, and ADR-writing practice.

For any non-trivial architectural decision (schema shape, API contract, how a
feature is structured, integration boundaries), the Tech Lead agent must:
1. Present 2-3 real alternatives, not just the chosen approach
2. Walk through tradeoffs: coupling, complexity, cost of changing later
3. Explicitly reason about how the decision would hold up if this grew from a
   single-user app into a multi-user product (even though multi-user is not
   in scope to build — this is a deliberate teaching exercise)
4. Record the decision as an ADR in /docs/adr/ (see format below)
5. Use a diagram (data flow / component boundaries) when it clarifies the tradeoff

ADR format (docs/adr/NNNN-title.md):
- Status (proposed/accepted/superseded)
- Context (what problem are we solving)
- Options considered (with pros/cons)
- Decision (what we picked and why)
- Consequences (what this makes easier/harder later)

## Project name & tone
The app is called CatAgenda. The premise is a running gag: the cats are 
secretly plotting world domination, and this app is framed as their "agenda" — 
the human is just the assistant executing their schedule (vet visits, feeding, 
supply runs) without realizing it's actually feline infrastructure.

- All user-facing copy (UI text, button labels, empty states, notifications, 
  error messages) should be witty, playful, and loaded with cat puns and 
  "world domination" framing — e.g. a shopping cart could be "The Supply Chain," 
  a calendar event for vaccines could be framed as "keeping the human minions 
  operational."
- This is copy/UX tone only — it should NOT affect data modeling, architecture, 
  or code structure, which stay serious and well-engineered. The joke lives in 
  the presentation layer, not the backend logic.
- Product Owner should write acceptance criteria that include tone/voice where 
  user-facing text is involved (e.g. "empty state copy should be witty and 
  on-brand," not literally scripting every pun in the backlog).

## Future scope (not building yet, but affects data model)
A future phase will add a social layer where multiple cat owners and their cats 
can interact (visibility, following, cross-owner interaction — like a small social 
network for cats/owners). Not being built now, but Phase 1's data model must treat 
cats as belonging to a user/owner from the start, since retrofitting that 
relationship later would mean reworking the schema. When the Tech Lead makes the 
Phase 1 data modeling decision, this future direction should be part of the 
"how would this hold up with more users" reasoning already required.

## Decision-making protocol
For architecture/design decisions, the Tech Lead presents alternatives with a 
recommendation and WAITS for the client's explicit choice before proceeding to 
implementation or writing the ADR. This is intentional — the client is studying 
system design and the decision-making itself is the learning objective, not just 
the outcome.

## Phase list
Phase 1: Cat profile management (care data)
Phase 2: Reasoning engine (what's due, what's needed)
Phase 3: Internal calendar view
Phase 4: Shopping cart
Phase 5: Google Calendar sync
Phase 6: Spay/neuter scheduling
Phase 7: Cat social profiles & posts (photos, bio, feed) — includes an image 
storage architecture decision
Phase 8 (future, not building yet): a social layer where multiple cat owners 
and their cats can interact — not in scope now, but Phase 1's data model needs 
to treat cats as belonging to a user/owner from the start so this doesn't 
require a schema rework later.
