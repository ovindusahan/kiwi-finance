# 0003. Represent money as integer cents

| Status | Date |
| --- | --- |
| Accepted | 2026-10-01 |

## Context

Binary floating point cannot represent most decimal amounts exactly. Sums, splits and
projections built on `double` drift by fractions of a cent, and those errors become visible
in a finance product.

## Decision

| Where | Representation |
| --- | --- |
| PostgreSQL | `BIGINT` columns with a `_cents` suffix |
| Finance engine | A `Money` value type wrapping a `long` number of cents; rates as `BigDecimal` |
| API | `{ "cents": 123456, "currency": "NZD" }` |
| Provider input | Decimal strings or numbers parsed through `BigDecimal`, never `double` |

Rounding happens at explicit, documented points using half-up rounding unless a rule
specifies otherwise. Splitting an amount uses largest-remainder allocation so no cents are
lost.

## Consequences

- No floating-point currency anywhere in the stack.
- The `currency` field leaves room for foreign-currency accounts without changing the API
  shape.
