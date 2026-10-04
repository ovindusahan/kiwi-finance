# Kiwi Finance Documentation

Everything you need to understand, run and extend Kiwi Finance.

## Start here

| Document | Read it when you want to |
| --- | --- |
| [Architecture](architecture.md) | Understand how the system fits together and why |
| [Roadmap](roadmap.md) | See what is built, what is next, and how delivery is phased |
| [Engineering guidelines](engineering-guidelines.md) | Write code, tests and commits that match the rest of the project |

## Reference

| Document | Contents |
| --- | --- |
| [API](api.md) | Conventions, authentication, errors, pagination and the endpoint catalogue |
| [Data model](data-model.md) | Entity relationship diagrams, tables and database conventions |
| [New Zealand financial rules](nz-financial-rules.md) | Tax, ACC levy, KiwiSaver, student loan and the planning algorithms built on them |
| [Configuration](configuration.md) | Environment variables, profiles and secrets |

## Integrations

| Document | Contents |
| --- | --- |
| [Akahu bank feeds](integrations/akahu.md) | How users connect their banks, how sync works, and how to operate it |

## Architecture decision records

Significant decisions are recorded as ADRs so the reasoning survives after the meeting
ends. See the [ADR index](adr/README.md).

| ADR | Decision |
| :-: | --- |
| [0001](adr/0001-monorepo.md) | One repository for backend, web, iOS and contracts |
| [0002](adr/0002-server-side-financial-logic.md) | Financial calculations run only on the server |
| [0003](adr/0003-money-representation.md) | Money is stored and calculated as integer cents |
| [0004](adr/0004-versioned-nz-rules.md) | NZ tax and KiwiSaver rules are versioned data |
| [0005](adr/0005-spring-boot-and-nextjs.md) | Spring Boot backend and Next.js web app |
| [0006](adr/0006-akahu-bank-feeds.md) | Bank feeds through per-user Akahu connections |
