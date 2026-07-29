import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/components/auth-provider";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: {
    default: "Rakshak · Police Kiosk",
    template: "%s · Rakshak",
  },
  description: "Edge-AI lost-child recovery system for mass gatherings",
};

/**
 * The root layout only establishes the auth session. Route protection lives in
 * `app/(kiosk)/layout.tsx` so that `/login` can render outside the guard.
 */
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className={inter.className}>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
