import type { Timestamp, GeoPoint } from "firebase/firestore";

export type AlertStatus = "active" | "resolved";
export type MatchStatus = "pending" | "dispatched";

/** Denormalised officer attribution stamped onto each alert. */
export interface AlertAuthor {
  uid: string;
  name: string;
  station: string;
}

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
  /** Officer who filed it. Absent on alerts created before attribution existed. */
  createdBy?: AlertAuthor;
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

/**
 * A police officer registered on the kiosk (`officers/{uid}`).
 * Identity fields are written by the `claimOfficerRole` Cloud Function; only
 * the fields in `OfficerProfileInput` are editable from the browser.
 */
export interface Officer {
  uid: string;
  email: string | null;
  photoURL: string | null;
  role: "police";
  displayName: string;
  phone: string;
  station: string;
  badgeNumber: string;
  createdAt: Timestamp;
  lastLoginAt: Timestamp;
  updatedAt?: Timestamp;
}

/** The subset of an officer record firestore.rules lets the kiosk write. */
export type OfficerProfileInput = Pick<
  Officer,
  "displayName" | "phone" | "station" | "badgeNumber"
>;

/** A confirmed match reported by a volunteer scanning the crowd. */
export interface Match {
  id: string;
  alertId: string;
  childName: string;
  imageUrl: string;
  volunteerId: string;
  volunteerRole: string;
  /**
   * Set when the sighting reached Firestore over the offline mesh, carried by a
   * different device than the one that made it. Present means `volunteerId` was
   * asserted by a relaying peer rather than proven by the reporter's own
   * session — worth showing, not worth hiding.
   */
  relayedBy?: string;
  location: GeoPoint;
  confidence: number;
  status: MatchStatus;
  timestamp: Timestamp;
}
