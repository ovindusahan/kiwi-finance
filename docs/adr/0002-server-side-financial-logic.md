# 0002. Financial calculations run only on the server

| Status | Date |
| --- | --- |
| Accepted | 2026-10-01 |

## Context

Tax, KiwiSaver, budgeting and affordability calculations must give identical answers on
the web and on iOS, and must be updated once when New Zealand rules change. Implementing
them in Java, TypeScript and Swift would triple the work and guarantee drift.

## Decision

All financial logic lives in `backend/finance-engine`, a plain Java library with no
framework dependencies, used only by the API. Clients call the API and render the results,
including the structured explanation returned with each one. Clients may format values
such as currency and dates, but never compute financial outcomes.

## Consequences

- One implementation to test and one place to update when rules change.
- "What if" screens need an API call per change; endpoints are kept cheap and clients
  debounce input.
- Because the engine is pure, it can be reused by scheduled jobs without change.
