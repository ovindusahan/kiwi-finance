# Configuration

> Environment variables, profiles and secrets for the API and the web app.

Configuration is validated when each app starts. The API refuses to start with a missing or
weak secret rather than running insecurely.

**Contents**

1. [Profiles](#1-profiles)
2. [API environment variables](#2-api-environment-variables)
3. [Generating secrets](#3-generating-secrets)
4. [Rotating the encryption key](#4-rotating-the-encryption-key)
5. [Web app environment variables](#5-web-app-environment-variables)
6. [Tuning](#6-tuning)

---

## 1. Profiles

| Profile | Use it for | What it adds |
| --- | --- | --- |
| *(none)* | Every shared or production environment | Nothing. Every secret must come from the environment. |
| `local` | Your own machine | The Docker Compose database, development-only secrets and CORS for `http://localhost:3000` |
| `sandbox` | Demos, development and end-to-end tests, always with `local` | A built-in stand-in for Akahu and a seeded demo account (see below) |

```bash
cd backend
./gradlew :api:bootRun --args='--spring.profiles.active=local,sandbox'
```

> [!WARNING]
> The `local` and `sandbox` profiles contain secrets that are published in this repository.
> Never enable them on a server that holds real data.

### The sandbox

The `sandbox` profile points the Akahu client at a stand-in served by the API itself, so
the whole bank-feed experience works with no Akahu account:

| What | Value |
| --- | --- |
| Demo sign-in | `demo@kiwifinance.nz` with password `kiwi-demo-2026` |
| Demo data | 13 months of realistic ANZ everyday, savings, credit card and KiwiSaver activity, a budget, three goals and a car purchase plan |
| Personal app tokens | Any app token starting `app_token_sandbox` with any user token starting `user_token_sandbox` |
| OAuth | "Connect with Akahu" opens a sandbox consent page instead of Akahu |

The demo account is created the first time the API starts with the profile. Delete the
user to have it recreated on the next start. Set `KIWI_SANDBOX_SEED_DEMO=false` to skip it.

---

## 2. API environment variables

### Required

| Variable | Description |
| --- | --- |
| `SPRING_DATASOURCE_URL` | JDBC URL, for example `jdbc:postgresql://db:5432/kiwi_finance` |
| `SPRING_DATASOURCE_USERNAME` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `KIWI_AUTH_JWT_SECRET` | Base64 key for signing access tokens. Must decode to at least 32 bytes. |
| `KIWI_CRYPTO_ACTIVE_KEY_ID` | Identifier of the key used to encrypt new bank-feed credentials, for example `k2026` |
| `KIWI_CRYPTO_KEYS_<ID>` | One Base64 AES-256 key (32 bytes) per identifier, for example `KIWI_CRYPTO_KEYS_K2026`. Keep old keys until nothing uses them. |

Key identifiers are matched in lower case, so use lower-case letters and digits.

### Akahu

| Variable | Description |
| --- | --- |
| `KIWI_AKAHU_APP_TOKEN` | App ID token of the deployment's registered Akahu app. Enables "Connect with Akahu". |
| `KIWI_AKAHU_APP_SECRET` | App secret used to exchange OAuth codes. |
| `KIWI_AKAHU_REDIRECT_URI` | Must exactly match the redirect URI registered with Akahu, for example `https://app.example.nz/connect/akahu/callback` |

All three are optional. Without them, OAuth is shown as unavailable and people connect with
their own personal app, which always works. See the [Akahu guide](integrations/akahu.md).

### Optional

| Variable | Default | Description |
| --- | --- | --- |
| `KIWI_CORS_ALLOWED_ORIGINS` | none | Comma-separated origins allowed to call the API from a browser. The web app calls the API from its server, so this is only needed for other browser clients. |
| `KIWI_AKAHU_SCHEDULED_SYNC_ENABLED` | `true` | Turns scheduled sync on or off |
| `KIWI_AKAHU_SCHEDULED_SYNC_CRON` | `0 15 */6 * * *` | When scheduled sync runs, in the server's time zone |
| `SERVER_PORT` | `8080` | HTTP port |

---

## 3. Generating secrets

```bash
# Access token signing key
openssl rand -base64 48

# Credential encryption key (exactly 32 bytes)
openssl rand -base64 32
```

Store secrets in your platform's secret manager and inject them as environment variables.
Never commit them.

---

## 4. Rotating the encryption key

Every encrypted credential records the identifier of the key that encrypted it, so keys can
be rotated without downtime.

1. Generate a new key and add it as `KIWI_CRYPTO_KEYS_<NEWID>`, keeping the old one.
2. Set `KIWI_CRYPTO_ACTIVE_KEY_ID` to the new identifier and restart. New and updated
   credentials use the new key; existing ones still decrypt with the old key.
3. Once every connection has been re-saved or disconnected, remove the old key.

---

## 5. Web app environment variables

| Variable | Required | Description |
| --- | :-: | --- |
| `KIWI_API_URL` | Yes | Where the web server reaches the API, for example `http://api:8080`. Only the server uses it. |
| `NEXT_PUBLIC_SHOW_DEMO_ACCOUNT` | No | `true` shows the sandbox demo sign-in on the sign-in page. Use it only with the `sandbox` profile. |

Session cookies are `HttpOnly`, `SameSite=Lax` and `Secure` in production builds, so serve
the web app over HTTPS.

---

## 6. Tuning

These rarely need changing. Override any of them with the matching environment variable.

| Setting | Default | Meaning |
| --- | --- | --- |
| `kiwi.auth.access-token-ttl` | `15m` | Access token lifetime |
| `kiwi.auth.refresh-token-ttl` | `30d` | Refresh token lifetime |
| `kiwi.auth.max-failed-logins` | `5` | Failed sign-ins before a temporary lockout |
| `kiwi.auth.lockout-duration` | `15m` | How long the lockout lasts |
| `kiwi.akahu.initial-history-days` | `365` | History fetched on the first sync of an account |
| `kiwi.akahu.resync-overlap-days` | `7` | Days re-read on each sync to catch late-posting transactions |
| `kiwi.akahu.sync-lease` | `15m` | How long a sync can hold its lease before another server may take over |
| `kiwi.akahu.read-timeout` | `30s` | Timeout for Akahu responses |
