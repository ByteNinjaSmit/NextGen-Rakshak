import type { Timestamp, GeoPoint } from "firebase/firestore";

export type AlertStatus = "active" | "resolved";
export type MatchStatus = "pending" | "dispatched";

/** A missing-child alert created by a police officer at the kiosk. */
export interface Alert {
  id: string;
  childName: string;
  age: number;
  gender: "Male" | "Female" | "Other";
  clothingDesc: string;
  parentContact: string;
  imageUrl: string;
  /** 128-d face embedding computed from the uploaded photo. */
  embedding: number[];
  lastSeen: string;
  geoLocation?: GeoPoint;
  status: AlertStatus;
  timestamp: Timestamp;
}

/** Form input for creating an alert (pre-embedding, pre-upload). */
export interface AlertInput {
  childName: string;
  age: number;
  gender: Alert["gender"];
  clothingDesc: string;
  parentContact: string;
  lastSeen: string;
}

/** A confirmed match reported by a volunteer scanning the crowd. */
export interface Match {
  id: string;
  alertId: string;
  childName: string;
  imageUrl: string;
  volunteerId: string;
  volunteerRole: string;
  location: GeoPoint;
  confidence: number;
  status: MatchStatus;
  timestamp: Timestamp;
}
