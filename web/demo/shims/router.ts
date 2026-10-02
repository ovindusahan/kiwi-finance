import { useSyncExternalStore } from "react";

type Location = { pathname: string; search: string };

function read(): Location {
  const hash = typeof window === "undefined" ? "" : window.location.hash.replace(/^#/, "");
  const target = hash.startsWith("/") ? hash : "/home";
  const [pathname = "/home", search = ""] = target.split("?");
  return { pathname, search: search ? `?${search}` : "" };
}

let current = read();
const listeners = new Set<() => void>();

function emit() {
  current = read();
  listeners.forEach((listener) => listener());
  window.scrollTo({ top: 0 });
}

if (typeof window !== "undefined") window.addEventListener("hashchange", emit);

export function navigate(href: string, replace = false) {
  const url = new URL(href, "https://preview.local");
  const target = `#${url.pathname}${url.search}`;
  if (replace) window.location.replace(target);
  else window.location.hash = target;
  if (url.hash) window.setTimeout(() => document.getElementById(url.hash.slice(1))?.scrollIntoView(), 50);
}

export function useLocation(): Location {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => current,
    () => current,
  );
}
