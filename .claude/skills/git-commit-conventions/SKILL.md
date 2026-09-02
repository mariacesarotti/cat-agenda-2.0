---
name: git-commit-conventions
description: Use whenever making a git commit in this project. Ensures consistent, readable commit history.
---

Use Conventional Commits format: <type>(<scope>): <short summary>

Types: feat, fix, refactor, test, docs, chore

Examples:
- feat(order): add order creation endpoint
- fix(user): correct password validation regex
- test(order): add unit tests for OrderService

Rules:
- Summary line under 72 characters, imperative mood ("add" not "added")
- One logical change per commit — don't bundle unrelated changes
- Body (optional, below a blank line) explains *why*, not *what* — the diff already shows what
