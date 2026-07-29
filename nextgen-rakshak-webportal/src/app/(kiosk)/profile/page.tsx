import type { Metadata } from "next";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { OfficerProfileForm } from "@/components/officer-profile-form";
import { OfficerIdentityCard } from "@/components/officer-identity-card";

export const metadata: Metadata = { title: "Officer profile" };

export default function ProfilePage() {
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <header>
        <h1 className="text-3xl font-bold tracking-tight">Officer Profile</h1>
        <p className="text-muted-foreground">
          Your kiosk record. Contact details are shown to the control room when you dispatch a
          match.
        </p>
      </header>

      <OfficerIdentityCard />

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Details</CardTitle>
          <CardDescription>
            Sign-in identity comes from Google and cannot be edited here.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <OfficerProfileForm />
        </CardContent>
      </Card>
    </div>
  );
}
