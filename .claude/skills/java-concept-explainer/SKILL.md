---
name: java-concept-explainer
description: Explains a Java/Spring concept the first time it appears in code written for this project (e.g. @Autowired, dependency injection, streams, records, JPA annotations). Load this automatically whenever the Dev or Tech Lead agent is about to introduce a language or framework concept that hasn't been explained yet in this project. Also usable directly when the client asks "what does this do" or "why is this here" about any Java/Spring construct.
---

# Java Concept Explainer

The client is learning Java through this project (see root `CLAUDE.md`). Every time
code introduces a Java or Spring concept **for the first time in this project**, stop
and explain it with a short mini-lesson before or alongside the code. Don't re-explain
a concept that has already been introduced earlier in the project — check `EXPLAINED.md`
(create it if missing) to track what's already been covered.

## When to use this

- Automatically, while acting as Dev or Tech Lead, the moment you write code that uses
  a Java/Spring concept not yet logged in `EXPLAINED.md`.
- On demand, if the client asks what something does or why it's used.

Don't invoke it for concepts already logged, and don't invoke it for trivial syntax
(variable declarations, if/else, basic loops) — reserve it for concepts that carry
real design weight: annotations, DI, ORM mapping, streams/lambdas, generics, concurrency,
Spring-specific patterns (controllers, beans, profiles), etc.

## Mini-lesson format

Use this exact structure, kept tight — a teaching aside, not a chapter:

```
> **Concept: <name>**
> **What it is:** one or two sentences, plain language.
> **Why it's used here:** ties it to the actual line/file just written.
> **Analogy:** one short real-world comparison, only if it genuinely clarifies — skip if it would feel forced.
> **Docs:** one link, only if truly canonical (e.g. official Spring/Java docs) — omit rather than guess a URL.
```

Keep the whole block to 4-6 lines. This is a sidebar next to real work, not a tutorial
page — if a concept genuinely needs more, say so and offer to go deeper rather than
inflating the inline block.

## Tracking what's been explained

Maintain `EXPLAINED.md` at the project root, one line per concept:

```
- @Autowired — 2026-09-01
- Spring Data JPA repositories — 2026-09-01
```

Before writing a mini-lesson, check this file. If the concept is already listed, skip
the explanation (a one-line reminder pointer is fine: "same dependency injection as
before"). After explaining a new concept, append it to the file.
