# Risks, Demo Runbook, and Viva Preparation

---

## 1. Risk Register

Ranked by expected damage, not by probability alone. Each risk names the week it
must be resolved by and the person who owns that call.

### R1 — `tfjs-node` will not run in the Cloud Functions runtime
**Impact: critical · Probability: medium · Owner: Tanishka · Must resolve: Week 4, day 2**

The native binding has never loaded successfully anywhere — the Week 3 report
attributes this to a local Node 22 vs Node 20 mismatch, but that diagnosis is
unconfirmed. If it also fails in the deployed runtime, no alert ever gets an
embedding and **face matching cannot work at all**.

*Mitigation:* deploy on day one, not day five. If it fails, pick a fallback the
same day:
1. Compute the embedding in the browser at alert creation with TensorFlow.js
   (the kiosk already has the photo; the model is 1.5 MB), or
2. Run the model in a small Cloud Run container with a controlled Node version.

Option 1 is faster to build and has an honest architectural justification: the
embedding is computed once, close to the photo. Do not spend more than one day
debugging a native binding.

### R2 — No suitable children's face dataset for VER-05
**Impact: high · Probability: high · Owner: Atharva (ran the Week 3 accuracy study) · Must resolve: Week 5**

NFR-02 (≥90% recognition accuracy) is the project's headline KPI and is currently
measured on adults only. Children's face photographs are an ethics problem before
they are a technical one.

*Mitigation, in order:* a published licensed research dataset with clear academic
terms → team members' own childhood photographs with written family consent →
failing both, **state plainly in the report that NFR-02 is unproven for the
target population**, present the adult measurement as an upper-bound proxy, and
make it the lead item in Future Scope. An honest, well-argued limitation is
respectable. Reporting the adult number as if it answered the child KPI is not,
and it is exactly the kind of thing an examiner probes.

### R3 — The mesh does not work across real devices
**Impact: high · Probability: medium · Owner: Tanishka · Must resolve: Week 5**

Multi-hop store-and-forward over Nearby Connections is the project's core claim
over ReUnite and every centralised system in the literature survey. It has never
run on two devices. Week 3 already found that a units bug had silently disabled
the entire path — that class of failure is invisible until you run it.

*Mitigation:* budget the whole of Wednesday Week 5 and expect the first attempt
to fail. Add verbose logging **before** the trial, not after. If genuine 3-hop
relay proves unreachable, demonstrate 2-hop and report the hop limit observed
with the reason — a measured limitation beats an unverified claim.

### R4 — Week 7 report load exceeds what one person can write
**Impact: high · Probability: medium-high · Owner: Smitraj**

Three chapters plus a possible IEEE paper in one week, on top of the regression
pass.

*Mitigation:* confirm in Week 4 whether the paper is required (removes 8 h if
not). Start DOC-16 Results in Week 6, the moment the last measurement lands — the
data is complete before the writing week begins.

### R5 — Feature freeze slips
**Impact: high · Probability: medium · Owner: Smitraj**

The classic final-year failure: the team is still adding features in Week 7 and
the report is written in a panic in Week 8.

*Mitigation:* the Thursday Week 6 freeze is a hard date. Anything unfinished at
that point becomes a Future Scope bullet, not a Week 7 task. Write the descope
down when it happens — an examiner respects a documented trade-off far more than
a half-finished feature.

### R6 — Live demo fails at the viva
**Impact: high · Probability: medium · Owner: everyone**

College Wi-Fi, FCM latency, a phone that decides to update itself.

*Mitigation:* DEMO-06. A pre-recorded video, a personal hotspot, printed
screenshots, and a mesh demo that needs no internet at all. The offline mesh
scenario is actually your safest demo — it works when the network does not, which
is the entire point of the project. Consider leading with it.

### R7 — Only 13 unit tests, most paths untested
**Impact: medium · Probability: certain · Owner: Smitraj**

Three of the seven Week-3 defects were found only by running the app. There are
almost certainly more.

*Mitigation:* QA-04 raises coverage to ≥25 tests, VER-16 exercises every test
case once. Accept that Week 7 will find defects and leave Tuesday free for them.

### R8 — Nobody can use the kiosk (no allow-listed officer)
**Impact: medium · Probability: certain until fixed · Owner: Vedant · Must resolve: Week 4**

By design, the kiosk locks out everyone until an `allowedOfficers/{email}`
document exists. Right now, none does. Fix it in Week 4 and verify **both** the
grant path and the deny path — the deny path is the interesting one and makes a
strong demo moment.

### R9 — Offline demo shows no photo
**Impact: medium · Probability: high without GAP-06 · Owner: Tanishka + Atharva**

The mesh carries `imageUrl`, not image bytes. With no internet, the side-by-side
match dialog — FR-07, and the moment the whole demo builds toward — cannot render
the parent's photo. This will be visible on stage.

*Mitigation:* GAP-06, scheduled in Week 5 **before** the mesh trial.

### R10 — Four people, one repository, no branch discipline
**Impact: low-medium · Probability: medium · Owner: Smitraj**

Every commit so far has gone straight to `main`. With four people working in
parallel from Week 4, that produces conflicts at the worst time.

*Mitigation:* QA-06 in Week 4. Feature branches and PRs also substantiate the
"standard feature-branch workflow" the synopsis claims in §5.2.

---

## 2. Demo Runbook

Rehearse this twice: DEMO-03 in Week 7, DEMO-04 in Week 8.

### Equipment checklist

- [ ] Laptop with the kiosk open at the hosted URL, signed in as an allow-listed officer
- [ ] 3 Android phones with the **signed release APK**, all signed in as volunteers with different roles
- [ ] A printed photo of the "missing child" (a team member's photo, or a doll) and a second person to be the child
- [ ] Personal hotspot as network backup
- [ ] Pre-recorded demo video on the laptop, ready to play
- [ ] Charged phones and a power bank

### Roles

| Person | Role during the demo |
|---|---|
| One member | Police officer at the kiosk |
| Two members | Volunteers scanning |
| One member | Narrator, and controls the offline scenario |

### Script — target 8–10 minutes

**Act 1 — the problem (1 min).** 98,375 children reported missing in India in a
single year; 269 a day. Existing systems are reactive, centralised, or
internet-dependent. Festival networks fail exactly when you need them.

**Act 2 — file the alert (2 min).** Officer signs in — show the deny path first
with a non-allow-listed account, then sign in properly. Create an alert with a
photo and details. Show the Cloud Function computing the embedding in the console
logs. Show the alert appearing on the volunteer phones within seconds.

**Act 3 — the match (3 min).** A volunteer opens Scan, walks past the "child".
Phone vibrates; side-by-side comparison appears. **Emphasise that the system does
not decide — the volunteer confirms.** Match appears on the kiosk within two
seconds with GPS. Officer taps Dispatch, Google Maps opens.

**Act 4 — the offline mesh (3 min) ← the differentiator.** All three phones into
airplane mode. Show that the kiosk cannot reach them. Bring one phone back online
briefly to receive a new alert, then take it offline again. The alert propagates
phone A → B → C over Bluetooth/Wi-Fi Direct with **no internet on any device**.
Confirm a match on phone C; it queues locally, relays over the mesh, and syncs to
the kiosk the moment connectivity returns. **This is the part no reviewed system
does.**

**Act 5 — privacy and results (1 min).** No bystander's face ever leaves a phone —
show the evidence from VER-12. Then the measured numbers: separation gap 0.3591,
threshold set from measurement not literature, inference time, mesh delivery time.

### If something fails on stage
Do not debug in front of the panel. Say "we have this recorded, let me show you",
play the video for that segment, and continue. Then explain what usually happens
and why it did not — panels reward composure and understanding far more than a
flawless run.

---

## 3. Likely Viva Questions

Write answers for all of these in Week 8 (DEMO-07). Every member should be able
to answer any of them, not only those in their own track.

**On the model and accuracy**
1. Why MobileFaceNet rather than FaceNet or ArcFace?
2. How did you arrive at the 0.55 threshold, and why not the 0.75 in your synopsis?
3. What is your false-positive and false-negative rate, and which one matters more here?
4. Did you test on children's faces? *(Answer honestly — see R2.)*
5. What happens if the child's face is partially covered or turned away?
6. Why 128 dimensions, and what does the L2 normalisation buy you?

**On the architecture**
7. Why compute the embedding on the server if the whole point is on-device processing?
8. What exactly never leaves the phone, and how do you know?
9. How does the mesh prevent infinite packet loops?
10. What happens when two volunteers report the same child?
11. Why Nearby Connections instead of Bluetooth mesh or Wi-Fi Aware?
12. Your synopsis says the kiosk broadcasts to the mesh. A browser cannot do that — how does an alert actually enter the mesh? *(This is correction #5; answer it before they ask.)*

**On security and privacy**
13. Could a volunteer file a fake missing-child alert?
14. Who can read the `allowedOfficers` list?
15. What stops a relaying device tampering with alert content? *(Answer honestly — signing is either implemented in GAP-02 or descoped.)*
16. How long does biometric data live, and who deletes it?

**On testing and validation**
17. Which non-functional requirements did you actually measure, and which did you not?
18. How did you find the timestamp-unit bug, and what does that tell you about your process?
19. What is your test coverage?
20. What would break first if 10,000 people used this at a real Kumbh Mela?

**On the project as a whole**
21. What is your contribution over the Maha Kumbh Digital Khoya-Paya Kendras?
22. What did you descope, and why?
23. If you had four more weeks, what would you build?

> The strongest answers in a final-year viva are the ones that admit a limit and
> explain the reasoning behind it. Your Week 3 report already does this well —
> the threshold revision and the seven documented defects are genuinely good
> material. Lead with them rather than hiding them.

---

## 4. Definition of Done — Project Level

The project is finished when all of the following are true:

- [ ] All P0 tasks in `02-backlog.md` are complete
- [ ] Every FR is either implemented and tested, or documented as descoped with a reason
- [ ] Every NFR has either a measured number or a written limitation
- [ ] The final report is submitted in the required format
- [ ] Slides and demo video exist and have been rehearsed
- [ ] The repository is clean, tagged `v1.0`, and the README lets a stranger set it up
- [ ] Every member can explain any part of the system
