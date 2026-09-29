import "@fontsource-variable/inter";
import "./globals.css";

import type { Metadata, Viewport } from "next";
import { Providers } from "./providers";

export const metadata: Metadata = {
  title: { default: "Kiwi Finance", template: "%s · Kiwi Finance" },
  description:
    "Personal finance built for New Zealand. Budgets, goals and honest answers to “Can I afford this?”",
  icons: { icon: "/icon.svg" },
};

export const viewport: Viewport = {
  themeColor: "#003a5d",
  colorScheme: "light",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-NZ">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
