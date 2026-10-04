import { NextResponse, type NextRequest } from "next/server";

const SESSION_COOKIE = "kf_refresh";
const AUTH_PAGES = ["/sign-in", "/sign-up"];
const PUBLIC_PAGES = ["/", "/pay-calculator", ...AUTH_PAGES];

/**
 * Sends people without a session to sign in, and people with one past the landing and sign-in
 * pages. The API still checks every request, so this only shapes navigation.
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const signedIn = request.cookies.has(SESSION_COOKIE);

  if (signedIn && (pathname === "/" || AUTH_PAGES.includes(pathname))) {
    return NextResponse.redirect(new URL("/home", request.url));
  }
  if (!signedIn && !PUBLIC_PAGES.includes(pathname)) {
    const signIn = new URL("/sign-in", request.url);
    signIn.searchParams.set("next", `${pathname}${search}`);
    return NextResponse.redirect(signIn);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|icon.svg|.*\\.(?:png|svg|jpg|webp|ico)$).*)"],
};
