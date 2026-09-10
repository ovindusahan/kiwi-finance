# 0005. Spring Boot backend and Next.js web app

| Status | Date |
| --- | --- |
| Accepted | 2026-10-01 |

## Context

The backend holds every financial calculation, integrates with Akahu, and must be secure,
maintainable and easy to staff over many years. The web app needs a polished, accessible UI
and must keep authentication tokens away from browser JavaScript.

## Decision

- **Backend:** Java 21 and Spring Boot 4.1 with Spring MVC on virtual threads, Spring
  Security, Spring Data JPA and Flyway, built with Gradle.
- **Web:** Next.js with the App Router and TypeScript, acting as a backend-for-frontend.
  The Next.js server stores tokens in `HttpOnly` cookies and calls the API on the user's
  behalf.
- **Contract:** springdoc generates the OpenAPI document from the API. Web types and the
  iOS client are generated from it.

## Consequences

- The backend uses a mature ecosystem with strong conventions for security, data access
  and scheduling.
- The browser never holds an access or refresh token.
- The backend and web app use different languages. The generated contract is what keeps
  them aligned, so CI checks that it is current.
