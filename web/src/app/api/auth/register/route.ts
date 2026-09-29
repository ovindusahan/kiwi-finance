import type { NextRequest } from "next/server";
import { authenticate } from "@/lib/server/auth";

export async function POST(request: NextRequest) {
  return authenticate(request, "register");
}
