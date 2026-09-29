import "server-only";

import { NextResponse, type NextRequest } from "next/server";
import { API_URL, setSession, type Tokens } from "./session";

/**
 * Signs in or registers with the API and turns the returned tokens into session cookies.
 */
export async function authenticate(request: NextRequest, action: "login" | "register") {
  const response = await fetch(`${API_URL}/api/v1/auth/${action}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: await request.text(),
    cache: "no-store",
  });
  const body = await response.text();
  if (!response.ok) {
    return new NextResponse(body, {
      status: response.status,
      headers: { "Content-Type": response.headers.get("Content-Type") ?? "application/problem+json" },
    });
  }
  const result = NextResponse.json({ ok: true }, { status: response.status });
  setSession(result, JSON.parse(body) as Tokens);
  return result;
}
