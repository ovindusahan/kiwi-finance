# 0006. Bank feeds through per-user Akahu connections

| Status | Date |
| --- | --- |
| Accepted | 2026-10-01 |

## Context

Manual entry and CSV exports are tedious, and people stop using budgeting tools that need
them. Akahu provides read-only access to accounts and transactions across most New Zealand
banks and KiwiSaver providers. Akahu offers two kinds of integration:

- **Personal apps**, which any individual can create to access their own data.
- **Registered apps**, which use OAuth and require an agreement with Akahu.

A new deployment of Kiwi Finance may not yet have a registered app, but every user should
still be able to connect their bank.

## Decision

Every user connects **their own** Akahu account to Kiwi Finance.

- **Personal app** connections are always available. The user creates a personal app in
  Akahu and enters its App ID Token and User Access Token.
- **OAuth** connections are offered when the deployment has registered app credentials
  configured.
- The API serves a **setup guide tailored to each user**, reflecting the methods available
  and the user's progress, so every client can present the same step-by-step wizard.
- Credentials are encrypted with AES-256-GCM and never returned by the API.
- Sync is scheduled and on demand, idempotent on Akahu's transaction ID, and never
  overwrites a category the user chose.

## Consequences

- Kiwi Finance never handles bank login details.
- People can start using bank feeds before the deployment has a commercial agreement with
  Akahu; moving to OAuth later needs configuration, not code changes.
- Personal-app setup takes a few more steps, which the setup guide makes manageable.
- The `bankfeed` package keeps provider-neutral concepts (connections, linked accounts,
  sync runs) apart from Akahu specifics, so another provider could be added later.
