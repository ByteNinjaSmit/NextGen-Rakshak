import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Sign in",
  description: "Police officer sign-in for the Rakshak kiosk",
};

export default function LoginLayout({ children }: { children: React.ReactNode }) {
  return children;
}
