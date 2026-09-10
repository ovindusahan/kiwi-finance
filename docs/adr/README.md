# Architecture Decision Records

An architecture decision record (ADR) captures one significant decision: the context it
was made in, what was decided, and what follows from it. ADRs are never rewritten after
they are accepted. If a decision changes, a new ADR supersedes the old one.

## Index

| ADR | Title | Status |
| :-: | --- | --- |
| [0001](0001-monorepo.md) | One repository for backend, web, iOS and contracts | Accepted |
| [0002](0002-server-side-financial-logic.md) | Financial calculations run only on the server | Accepted |
| [0003](0003-money-representation.md) | Represent money as integer cents | Accepted |
| [0004](0004-versioned-nz-rules.md) | NZ tax and KiwiSaver rules are versioned data | Accepted |
| [0005](0005-spring-boot-and-nextjs.md) | Spring Boot backend and Next.js web app | Accepted |
| [0006](0006-akahu-bank-feeds.md) | Bank feeds through per-user Akahu connections | Accepted |

## Writing a new ADR

1. Copy the template below into `NNNN-short-title.md` using the next number.
2. Keep it to one decision and about one page.
3. Open it in a pull request so the discussion is recorded alongside it.

```markdown
# NNNN. Title in the imperative or as a statement

| Status | Date |
| --- | --- |
| Proposed | YYYY-MM-DD |

## Context

What problem are we solving, and what constraints apply?

## Decision

What did we decide?

## Consequences

What becomes easier, what becomes harder, and what follow-up work does this create?
```
