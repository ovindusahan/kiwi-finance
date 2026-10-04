import { NextResponse, type NextRequest } from "next/server";
import {
  ACCESS_COOKIE,
  API_URL,
  REFRESH_COOKIE,
  clearSession,
  refreshSession,
  setSession,
  type Tokens,
} from "@/lib/server/session";

type Context = { params: Promise<{ path: string[] }> };

const FORWARDED_REQUEST_HEADERS = ["content-type", "accept"];
const FORWARDED_RESPONSE_HEADERS = ["content-type", "www-authenticate"];

/**
 * Forwards browser requests to the API with the session's access token. When the access token
 * has expired it refreshes the session once and retries, so the browser only ever sees cookies.
 */
async function forward(request: NextRequest, context: Context) {
  const { path } = await context.params;
  const target = `${API_URL}/api/v1/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`;
  const body = ["GET", "HEAD"].includes(request.method) ? undefined : await request.arrayBuffer();

  let accessToken = request.cookies.get(ACCESS_COOKIE)?.value;
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
  let refreshed: Tokens | null = null;

  if (!accessToken && refreshToken) {
    refreshed = await refreshSession(refreshToken);
    accessToken = refreshed?.accessToken;
  }

  let upstream = await send(request, target, body, accessToken);
  if (upstream.status === 401 && refreshToken && !refreshed) {
    refreshed = await refreshSession(refreshToken);
    if (refreshed) {
      upstream = await send(request, target, body, refreshed.accessToken);
    }
  }

  const headers = new Headers();
  for (const name of FORWARDED_RESPONSE_HEADERS) {
    const value = upstream.headers.get(name);
    if (value) headers.set(name, value);
  }
  const response = new NextResponse(upstream.status === 204 ? null : await upstream.arrayBuffer(), {
    status: upstream.status,
    headers,
  });
  if (refreshed) {
    setSession(response, refreshed);
  } else if (upstream.status === 401 && refreshToken) {
    clearSession(response);
  }
  return response;
}

function send(
  request: NextRequest,
  target: string,
  body: ArrayBuffer | undefined,
  accessToken: string | undefined,
) {
  const headers = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  return fetch(target, { method: request.method, headers, body, cache: "no-store", redirect: "manual" });
}

export const GET = forward;
export const POST = forward;
export const PUT = forward;
export const PATCH = forward;
export const DELETE = forward;
