import "server-only";

import type { NextResponse } from "next/server";

export const ACCESS_COOKIE = "kf_access";
export const REFRESH_COOKIE = "kf_refresh";

export const API_URL = process.env.KIWI_API_URL ?? "http://localhost:8080";

export type Tokens = {
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  refreshTokenExpiresAt: string;
};

const secure = process.env.NODE_ENV === "production";

/** Stores the session in HttpOnly cookies so client-side scripts can never read the tokens. */
export function setSession(response: NextResponse, tokens: Tokens) {
  response.cookies.set(ACCESS_COOKIE, tokens.accessToken, {
    httpOnly: true,
    secure,
    sameSite: "lax",
    path: "/",
    maxAge: Math.max(0, tokens.expiresIn - 30),
  });
  response.cookies.set(REFRESH_COOKIE, tokens.refreshToken, {
    httpOnly: true,
    secure,
    sameSite: "lax",
    path: "/",
    expires: new Date(tokens.refreshTokenExpiresAt),
  });
}

export function clearSession(response: NextResponse) {
  response.cookies.delete(ACCESS_COOKIE);
  response.cookies.delete(REFRESH_COOKIE);
}

const inFlight = new Map<string, Promise<Tokens | null>>();

/**
 * Exchanges a refresh token for new tokens. Concurrent requests share one exchange, because the
 * API treats a second use of the same refresh token as theft and ends the session.
 */
export function refreshSession(refreshToken: string): Promise<Tokens | null> {
  const pending = inFlight.get(refreshToken);
  if (pending) return pending;
  const exchange = fetch(`${API_URL}/api/v1/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
    cache: "no-store",
  })
    .then(async (response) => (response.ok ? ((await response.json()) as Tokens) : null))
    .catch(() => null)
    .finally(() => {
      setTimeout(() => inFlight.delete(refreshToken), 10_000);
    });
  inFlight.set(refreshToken, exchange);
  return exchange;
}
