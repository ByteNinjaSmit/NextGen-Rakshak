import type { Timestamp, GeoPoint } from "firebase/firestore";

export type AlertStatus = "active" | "resolved";
export type MatchStatus = "pending" | "dispatched" | "accepted" | "dismissed";

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
  /** Date (YYYY-MM-DD) the child was last seen. Absent on alerts filed before this field existed. */
  lastSeenDate?: string;
  /** Time (HH:MM) the child was last seen. Absent on alerts filed before this field existed. */
  lastSeenTime?: string;
  /** Free-text scars/marks/accessories not covered by clothingDesc. */
  identifyingMarks?: string;
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
  lastSeenDate: string;
  lastSeenTime: string;
  identifyingMarks: string;
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
  /** Browser push token for the kiosk notification bell; absent until granted. */
  fcmToken?: string;
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
  /** Blank when the reporter signed in via the phone-only demo path, which never collects a name. */
  volunteerName?: string;
  /**
   * Set when the sighting reached Firestore over the offline mesh, carried by a
   * different device than the one that made it. Present means `volunteerId` was
   * asserted by a relaying peer rather than proven by the reporter's own
   * session — worth showing, not worth hiding.
   */
  relayedBy?: string;
  location: GeoPoint;
  /**
   * False when the volunteer had no GPS fix — `location` is then `0,0` and must
   * not be turned into a map link. Absent on matches filed before this field
   * existed; treat absent as `true`.
   */
  hasLocation?: boolean;
  confidence: number;
  status: MatchStatus;
  timestamp: Timestamp;
}
