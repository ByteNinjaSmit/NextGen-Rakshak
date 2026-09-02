# Presentation Script — Smitraj_Bankar_Weekly_Progress.pptx

*(Short talking points per slide — for presenting to guide. Say this in your
own words, don't read verbatim.)*

**Slide 1 — Title**
"This is my individual progress — backend, Cloud Functions, and the ML
pipeline for NextGen Rakshak, weeks 1 to 6."

**Slide 2 — Weeks 1-2 (Team)**
"First two weeks were team-wide: we studied existing lost-child systems,
found they all fail without internet, and designed our 3-part architecture —
web kiosk, mobile app, offline mesh."

**Slide 3 — Week 3: Studying Cloud Functions**
"I studied Firebase Cloud Functions — specifically how a function can
auto-trigger the instant a document is written to the database, no polling
needed. This became the backbone of the alert pipeline."

**Slide 4 — Proof: Week 3**
"Concretely: I built the pipeline that turns one pretrained face-recognition
model into two matching files — one for the phone, one for the server.
Result: model shrank from 5.9 MB to 1.5 MB, and a parity check proved both
versions produce near-identical output (0.99967 similarity)."

**Slide 5 — Week 4: Testing + Deployment**
"I tested the functions against a live Firestore database — not just locally
— and deployed the web portal to Vercel so the team could test it from any
browser, not just my laptop."

**Slide 6 — Proof: Week 4**
"Proof: every test alert logged 'embedding written, 128 dimensions' — that's
how I confirmed the pipeline worked end-to-end, not just that it didn't
crash."

**Slide 7 — Week 5: Built the Pipeline**
"This week I implemented the three real functions: one computes the face
embedding and starts the broadcast when an alert is filed, one sends the
push notification to volunteers, one notifies the officer when a volunteer
reports a sighting."

**Slide 8 — Proof: Week 5**
"These aren't toy functions — they handle real limits: push notifications
auto-batch at 500 devices per request (the API's hard cap), database cleanup
batches at 500 documents, and a volunteer with no location is still notified
rather than silently skipped."

**Slide 9 — Week 6: Weights + Runtime**
"I sourced the actual pretrained face-recognition weights, built the
conversion scripts, and separately upgraded our backend off Node.js 20
before it reached end-of-life in October."

**Slide 10 — Proof: Week 6**
"The big one: I tested 36 real photo pairs. Same-person scores landed
0.71–0.99, different-person scores landed 0.09–0.36 — a clean gap. The
project's original threshold (0.75) missed 5 of 15 genuine matches. I moved
it to 0.55, measured from this data — 0 missed, 0 false matches."

**Slide 11 — Technical: Functions Architecture**
"Here's the full function chain end to end: alert created → embed + notify
→ volunteer confirms → officer notified → 8 hours later, auto-expire and
wipe the photo."

**Slide 12 — Technical: Threshold Deep-Dive**
"Same threshold data again, laid out as a table — this is the evidence
behind the 0.55 number, not a guess."

**Slide 13 — Engineering Care**
"A few defensive decisions I made: functions never fetch a URL the caller
gives them (blocks a whole class of server-side attack), missing-location
volunteers are still notified, database writes are chunked to survive a
busy-event backlog, and a resolved case's photo is deleted immediately —
privacy is code, not a policy document."

**Slide 14 — Result Screenshots**
"And here's it actually running — live dashboard and match review, both fed
by the functions I just walked through."

**Slide 15 — Summary Table**
"Quick recap: Week 3 studied the trigger model, Week 4 tested it live and
deployed, Week 5 built the three functions, Week 6 tuned the threshold and
upgraded the runtime."

**Slide 16 — Thank You**
"That's my track — happy to take questions on any of the numbers."

---

## If guide asks "why should I believe this?"

Point to the **measured numbers**, not claims:
- Model: 5.9 MB → 1.5 MB, parity 0.99967
- Threshold: 36 real pairs tested, 0.55 gives 0/21 false + 0/15 missed vs.
  0.75's 5/15 missed
- Pipeline limits: 500-token FCM batches, 500-doc Firestore batches, 2 km
  geofence — all handled, not assumed

These are reproducible: re-run `scripts/verify_parity.py` for the parity
number, or open `scripts/README.md` for the full threshold study.
