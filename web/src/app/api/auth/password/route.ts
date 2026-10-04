import { NextResponse, type NextRequest } from "next/server";
import {
  ACCESS_COOKIE,
  API_URL,
  REFRESH_COOKIE,
  refreshSession,
  setSession,
  type Tokens,
} from "@/lib/server/session";

/**
 * Changes the password. The API ends every session when the password changes and returns a new
 * one for this device, so the new tokens go straight into the session cookies.
 */
export async function POST(request: NextRequest) {
  const body = await request.text();
  let accessToken = request.cookies.get(ACCESS_COOKIE)?.value;
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
  if (!accessToken && refreshToken) {
    accessToken = (await refreshSession(refreshToken))?.accessToken;
  }
  if (!accessToken) {
    return NextResponse.json(
      { code: "unauthenticated", detail: "Sign in to continue." },
      { status: 401, headers: { "Content-Type": "application/problem+json" } },
    );
  }
  const upstream = await fetch(`${API_URL}/api/v1/auth/me/password`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
    body,
    cache: "no-store",
  });
  const text = await upstream.text();
  if (!upstream.ok) {
    return new NextResponse(text, {
      status: upstream.status,
      headers: { "Content-Type": upstream.headers.get("Content-Type") ?? "application/problem+json" },
    });
  }
  const response = NextResponse.json({ ok: true });
  setSession(response, JSON.parse(text) as Tokens);
  return response;
}
