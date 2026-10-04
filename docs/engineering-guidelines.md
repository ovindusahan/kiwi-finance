# Engineering Guidelines

> How we write, test and ship code in Kiwi Finance.

These guidelines keep the codebase consistent as it grows. When something here is unclear
or out of date, fix the document in the same change as the code.

**Contents**

1. [Principles](#1-principles)
2. [Backend](#2-backend)
3. [Web](#3-web)
4. [Database](#4-database)
5. [API design](#5-api-design)
6. [Testing](#6-testing)
7. [Security](#7-security)
8. [Comments and documentation](#8-comments-and-documentation)
9. [Git workflow](#9-git-workflow)

---

## 1. Principles

- **Clarity over cleverness.** Code is read far more often than it is written.
- **Small, focused changes.** One concern per change, with its tests.
- **No speculative abstraction.** Introduce an interface when there is a second
  implementation or a real test seam, not before.
- **No shortcuts that create debt.** If something is deliberately deferred, it is recorded
  on the [roadmap](roadmap.md), not hidden in a `TODO`.
- **Money is exact.** Integer cents everywhere. See [ADR 0003](adr/0003-money-representation.md).

---

## 2. Backend

### Structure

- Package by feature: `nz.kiwifinance.account`, `nz.kiwifinance.bankfeed`, and so on.
  Shared infrastructure lives in `nz.kiwifinance.common`.
- Types are package-private by default. Make something `public` only when another feature
  needs it.
- One controller per resource. Controllers bind, validate and delegate; they hold no
  business logic.
- Services own use cases and transactions. Never call an external service (such as Akahu)
  inside a database transaction.
- Financial calculations belong in `finance-engine`, never in the API module.

### Style

| Topic | Convention |
| --- | --- |
| Formatting | Palantir Java Format via Spotless. Run `./gradlew spotlessApply` before committing. |
| DTOs and value objects | Java records |
| Dependency injection | Constructor injection only, generated with Lombok's `@RequiredArgsConstructor` on `private final` fields. No field injection. |
| Entities | Lombok `@Getter` and `@Setter` on the fields that need them. Never `@Data`, `@EqualsAndHashCode` or `@ToString` on a JPA entity: they break entity identity and load lazy relations. |
| Boilerplate | Lombok replaces hand-written getters, setters and constructors. Behaviour, validation and domain methods stay hand-written. |
| Nullability | Avoid returning `null` from public methods. Use `Optional` for absent lookups. |
| Naming | `AccountService`, `AccountController`, `AccountRepository`; request and response records end in `Request` and `Response` |
| Errors | Throw a subclass of `ApiException` with a stable error code. Never return error payloads by hand. |
| Time | Inject `Clock`. Use `LocalDate` in `Pacific/Auckland` for calendar dates and `Instant` for moments. |
| Logging | Log4j2 through Lombok's `@Log4j2`. Log events and identifiers, never tokens, descriptions or amounts. |

---

## 3. Web

### Structure

- Routes live in `src/app` and stay thin: they set the page title and render a feature.
- Each screen is a folder in `src/features`. Shared building blocks live in
  `src/components/ui` (design system) and `src/components/app` (app shell and shared
  pieces).
- Data comes from the hooks in `src/lib/api/queries.ts`. Changes go through
  `useApiMutation`, which refreshes everything derived from the person's money.
- The browser only talks to `/api/auth/*` and `/api/proxy/*`. Tokens never reach client
  code.

### Style

| Topic | Convention |
| --- | --- |
| Formatting | Prettier (`npm run format`), checked before every push |
| Linting | ESLint with the Next.js core web vitals and TypeScript rules |
| Types | Strict TypeScript with `noUncheckedIndexedAccess`. API types come from the generated schema, never hand-written. |
| Styling | Tailwind CSS with the design tokens in `globals.css`. Colours always come from tokens. Sections sit in white `Card`s on the light grey page, and notices use light tinted cards rather than coloured stripes. |
| Money and dates | Always format with `src/lib/format.ts`. Parse typed amounts with `parseDollars`. |
| Calculations | None. If a screen needs a number the API doesn't provide, add it to the API. |
| Accessibility | Every control has a label, dialogs trap focus, statuses use words as well as colour, and charts have a table view. |

---

## 4. Database

- Every change is a new Flyway migration named `V<n>__<description>.sql`.
- Migrations are never edited once merged.
- Tables and columns use `snake_case`. Tables are plural.
- Money columns are `BIGINT` with a `_cents` suffix.
- Timestamps are `TIMESTAMPTZ`; calendar dates are `DATE`.
- Every user-owned table has a `user_id` foreign key and an index that starts with it.
- Use constraints (foreign keys, `CHECK`, unique indexes) to protect invariants the
  application relies on.

See the [data model](data-model.md) for the current schema.

---

## 5. API design

- Resources are plural nouns under `/api/v1`. Actions that are not CRUD are modelled as
  sub-resources (`POST /bank-feeds/connections/{id}/syncs`).
- Request and response bodies are JSON with `camelCase` fields.
- Money is always `{ "cents": 12345, "currency": "NZD" }`.
- Errors are RFC 9457 problem details with a stable `code`.
- Resources owned by someone else return `404`, not `403`, so their existence is not
  revealed.
- Any change to the API updates `contracts/openapi.json` in the same commit.

See the [API reference](api.md).

---

## 6. Testing

| Kind | Scope | Tools |
| --- | --- | --- |
| Unit | Engine calculations, services with collaborators mocked, mappers | JUnit 6, AssertJ, Mockito |
| HTTP client | External provider clients | `MockRestServiceServer` |
| Integration | Full request flows against PostgreSQL | `@SpringBootTest`, `MockMvc` |
| Web unit | Formatting, display logic and components | Vitest, Testing Library |
| End to end | Journeys through the web app against the real API and the Akahu sandbox | Playwright |

- Test names describe behaviour: `refreshTokenReuseRevokesTheWholeFamily`.
- Every endpoint that reads user data has an isolation test proving another user gets
  `404`.
- Tests do not depend on execution order or the current date. Use a fixed `Clock`.
- End-to-end journeys that change data create their own account, so they never depend on
  each other or on the demo account's state.

---

## 7. Security

- Validate every input at the boundary.
- Scope every query for user-owned data by user ID.
- Store secrets only in the environment. Never commit real credentials.
- Encrypt third-party credentials at rest with the shared `CredentialCipher`.
- Never log tokens, passwords, transaction descriptions or amounts.

---

## 8. Comments and documentation

Write comments only when they add something the code cannot say:

- a New Zealand regulatory rule and where it comes from,
- a provider quirk (for example, why sync re-reads a 7-day window),
- a non-obvious design decision.

Do not restate what the code does. Prefer a well-named method over a comment.

Documentation uses New Zealand English (*organise*, *colour*, *licence*), plain language
and short sentences. Diagrams use Mermaid so they render on GitHub and stay editable.

---

## 9. Git workflow

### Branches

The repository follows Git Flow.

| Branch | Purpose |
| --- | --- |
| `main` | Released code only. Every commit on `main` is a release merged from `develop` and tagged, for example `v1.0.0`. |
| `develop` | Integration branch. Finished work is merged here and must always build and pass every check. |
| `feature/<short-name>` | New functionality, branched from `develop` and merged back with a merge commit. |
| `fix/<short-name>` | A bug fix, handled the same way as a feature branch. |
| `hotfix/<short-name>` | An urgent fix branched from `main`, merged into both `main` and `develop`. |

Branch names are lower case with hyphens, for example `feature/akahu-bank-feeds`. A feature
branch carries everything the feature needs: its code, tests, migrations and documentation.
Keep branches short-lived and merge them within a few days.

### Releases and the demo

Only the repository owner merges into `main`. A ruleset on `main` blocks direct pushes and
requires a pull request with passing checks. Merging `develop` into `main` is a release: tag
it, and the **Deploy demo** workflow publishes the demo to GitHub Pages once CI passes.

### Commit messages

Commits follow [Conventional Commits](https://www.conventionalcommits.org/):

```text
feat(api): connect Akahu bank feeds

Personal app tokens and OAuth sign-in, encrypted credential storage, and
scheduled and manual syncs with a step-by-step setup guide.
```

- Format: `type(scope): summary`. Types are `feat`, `fix`, `docs`, `test`, `build`, `ci`,
  `chore` and `refactor`. Scopes name the area, such as `engine`, `api` or `web`.
- Summary in the imperative mood, lower case after the colon, no trailing full stop, under
  72 characters.
- A body when the reason for the change is not obvious from the summary.
- One logical change per commit.

### Pull requests

- Describe what changed and why, and how it was tested.
- Keep pull requests small enough to review in one sitting.
- Every check must pass before merging.
