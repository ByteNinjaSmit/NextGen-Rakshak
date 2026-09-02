# NextGen Rakshak — Simple Progress Report

*(For guide review — plain-language version, no deep technical jargon)*

## 1. The Problem

At big crowded events (fairs, festivals, religious gatherings), kids get
separated from parents. Phone networks jam up because too many people use
them at once. The first 60–90 minutes ("golden hour") matter most, but
current systems are slow and depend on internet that may not work.

## 2. Our Idea in One Line

Let every volunteer's phone camera silently check nearby faces against a
missing-child photo, using AI **on the phone itself** — so it keeps working
even with no internet, and no one's photo/video ever gets uploaded anywhere.

## 3. How It Works (Simple Flow)

1. A parent reports a missing child to a police officer at a help-desk (kiosk).
2. Officer types the child's details and uploads a photo on our **web
   portal**.
3. The system turns that photo into a small "face fingerprint" (just numbers,
   not the image) and sends the alert to volunteers nearby.
4. Volunteers open our **mobile app**, point their camera at the crowd. The
   app compares faces it sees to the fingerprint — all on the phone, nothing
   sent to any server.
5. If it looks like a strong match, the app shows both photos side by side
   and asks the volunteer, "Is this the child?" A human always confirms —
   the app never decides on its own.
6. Once confirmed, the officer's dashboard lights up instantly with the
   volunteer's location, so they can dispatch help.
7. **No internet nearby?** Phones pass the alert to each other directly
   (Bluetooth/Wi-Fi handoff) so it still spreads through the crowd.

## 4. What We Have Built So Far

| Part | Status | What it does |
|---|---|---|
| Police Web Portal | ✅ Working | Officer login, create alert, live dashboard, view volunteer sightings, notification bell |
| Mobile Volunteer App | ✅ Working | Login, view alerts, live camera scan, on-device face match, confirm/reject match, match history |
| AI Face Matching | ✅ Working | Runs fully on the phone, no photos leave the device |
| Cloud Backend | ✅ Working | Stores alerts/matches, computes face fingerprint, sends push notifications to nearby volunteers |
| Offline Phone-to-Phone Relay | ✅ Working | Alerts keep spreading between phones even without internet |
| Raspberry Pi Camera Node (gate camera) | ⏳ Not started | Optional extra — planned only if time allows |

**In short: the core system (web + mobile + AI + cloud + offline relay) is
built and working end-to-end**, as shown by the screenshots in `UI_Images/`.
Only the optional fixed-camera add-on (Raspberry Pi) is left, and it was
always scoped as a stretch goal.

## 5. Why It's Different from Existing Systems

- **Privacy-first**: no face photos or videos are ever uploaded — only the
  officer's alert photo (with parent's consent) touches the cloud.
- **Works without internet**: most solutions stop working when the network
  is congested; ours keeps spreading alerts phone-to-phone.
- **Human stays in control**: the AI only suggests; a volunteer always
  confirms visually before anything is dispatched.

## 6. What's Left

- (Optional, low priority) Raspberry Pi fixed-camera node at gates.
- Polish, more testing, and demo rehearsal.

## 7. Team

| Roll No. | Name |
|---|---|
| 09 | Bankar Smitraj Dinkar |
| 11 | Bhakare Tanishka Sharad |
| 34 | Dhadge Vedant Sanjay |
| 94 | Narkhede Atharva Anantkumar |

Guide: Dr. A. B. Pawar · Coordinator: Dr. S. R. Deshmukh · HOD: Dr. M. A. Jawale
