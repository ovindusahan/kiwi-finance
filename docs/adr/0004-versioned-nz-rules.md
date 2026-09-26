# 0004. NZ tax and KiwiSaver rules are versioned data

| Status | Date |
| --- | --- |
| Accepted | 2026-10-01 |

## Context

Tax brackets, the ACC earners' levy, KiwiSaver default rates and government contributions,
student loan thresholds and tax credits change regularly, sometimes part-way through a tax
year. Projections often span several tax years.

## Decision

Each New Zealand tax year (1 April to 31 March) has a typed rule set in the finance engine.
Values that change part-way through a year are dated entries within the rule set. A
resolver returns the rules that apply on any given date. Each rule set records the
publications it was taken from and the date it was verified.

## Consequences

- A new tax year means a new rule set and its fixture tests, not changes to calculation
  code.
- Projections beyond the latest known year reuse the latest rules, and the explanation
  states that assumption.
- Payslip fixture tests for each year guard against regressions.
